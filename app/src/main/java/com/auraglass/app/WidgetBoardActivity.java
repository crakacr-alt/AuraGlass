package com.auraglass.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A real Android AppWidgetHost workspace, part of the AURA launcher.
 * Provider widgets remain controlled by their originating apps and system.
 * Binding/configuration requires explicit system consent when appropriate.
 */
public final class WidgetBoardActivity extends Activity {
    private static final int HOST_ID=70901;
    private static final int REQUEST_BIND=4011;
    private static final int REQUEST_CONFIG=4012;
    private final ArrayList<Integer> ids=new ArrayList<>();
    private AppWidgetHost host;
    private AppWidgetManager manager;
    private LinearLayout content;
    private int pendingId=AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean active;

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,boolean bold){
        TextView t=new TextView(this);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        return t;
    }
    private Button action(String label,Runnable runnable){
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setText(label);
        b.setOnClickListener(v->runnable.run());
        return b;
    }
    private void addButton(LinearLayout parent,String label,Runnable runnable){
        parent.addView(action(label,runnable),
            new LinearLayout.LayoutParams(-1,dp(49)));
    }
    private void message(String text){
        Toast.makeText(this,text,Toast.LENGTH_LONG).show();
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        RefreshRateSupport.applyToActivity(this);
        manager=AppWidgetManager.getInstance(this);
        host=new AppWidgetHost(this,HOST_ID);
        getWindow().setStatusBarColor(0xff1c2239);
        getWindow().setNavigationBarColor(0xff121829);
        FrameLayout surface=new FrameLayout(this);
        surface.setBackgroundColor(0xf0192235);
        setContentView(surface);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        content=new LinearLayout(this);
        content.setPadding(dp(18),dp(30),dp(18),dp(40));
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        surface.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        restoreIds();
        build();
    }

    private void restoreIds(){
        ids.clear();
        String saved=Config.str(this,"widget_board_ids","");
        if(saved==null||saved.isEmpty())return;
        for(String value:saved.split(",")){
            try{
                int id=Integer.parseInt(value);
                if(id>0&&!ids.contains(id)&&ids.size()<20)ids.add(id);
            }catch(NumberFormatException ignored){}
        }
    }
    private void saveIds(){
        StringBuilder saved=new StringBuilder();
        for(int id:ids){
            if(saved.length()>0)saved.append(',');
            saved.append(id);
        }
        Config.put(this,"widget_board_ids",saved.toString());
    }
    private int widgetHeight(int id,AppWidgetProviderInfo info){
        int minimum=info==null?140:Math.max(110,info.minHeight);
        return Math.max(110,Math.min(480,
            Math.round(Config.number(this,"widget_height_"+id,minimum))));
    }
    private void setWidgetHeight(int id,int height){
        Config.put(this,"widget_height_"+id,(float)Math.max(110,Math.min(480,height)));
        try{
            Bundle options=new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,height);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,height);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,300);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,480);
            manager.updateAppWidgetOptions(id,options);
        }catch(RuntimeException ignored){}
        build();
    }
    private void move(int index,int offset){
        int to=index+offset;
        if(to<0||to>=ids.size())return;
        Collections.swap(ids,index,to);
        saveIds();build();
    }
    private void remove(int id){
        new AlertDialog.Builder(this).setTitle("Удалить виджет из AURA?")
            .setMessage("Само приложение-поставщик не удалится.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Удалить",(d,w)->{
                ids.remove(Integer.valueOf(id));
                saveIds();
                try{host.deleteAppWidgetId(id);}catch(RuntimeException ignored){}
                build();
            }).show();
    }
    private void build(){
        if(content==null)return;
        content.removeAllViews();
        TextView title=text("AURA · ВИДЖЕТЫ",26,true);
        content.addView(title);
        TextView info=text("Настоящие виджеты Android. Нажимай на них, как на рабочем столе.",12,false);
        info.setTextColor(0xffd5daea);
        LinearLayout.LayoutParams sub=new LinearLayout.LayoutParams(-1,-2);
        sub.topMargin=dp(5);sub.bottomMargin=dp(10);
        content.addView(info,sub);
        LinearLayout headerActions=new LinearLayout(this);
        Button back=action("← Домой",this::finish);
        headerActions.addView(back,new LinearLayout.LayoutParams(0,dp(48),1));
        Button add=action("+ Добавить",this::chooseProvider);
        headerActions.addView(add,new LinearLayout.LayoutParams(0,dp(48),2));
        LinearLayout.LayoutParams buttons=new LinearLayout.LayoutParams(-1,dp(51));
        buttons.bottomMargin=dp(12);
        content.addView(headerActions,buttons);

        boolean changed=false;
        ArrayList<Integer> current=new ArrayList<>(ids);
        for(int i=0;i<current.size();i++){
            int widgetId=current.get(i);
            AppWidgetProviderInfo provider=manager.getAppWidgetInfo(widgetId);
            if(provider==null){
                ids.remove(Integer.valueOf(widgetId));
                try{host.deleteAppWidgetId(widgetId);}catch(RuntimeException ignored){}
                changed=true;
                continue;
            }
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(9),dp(10),dp(9),dp(10));
            card.setBackgroundColor(0xaa343b55);
            LinearLayout labelRow=new LinearLayout(this);
            TextView widgetName=text(provider.loadLabel(getPackageManager()),13,true);
            labelRow.addView(widgetName,new LinearLayout.LayoutParams(0,dp(34),1));
            final int position=i;
            Button up=action("↑",()->move(ids.indexOf(widgetId),-1));
            labelRow.addView(up,new LinearLayout.LayoutParams(dp(43),dp(38)));
            Button down=action("↓",()->move(ids.indexOf(widgetId),1));
            labelRow.addView(down,new LinearLayout.LayoutParams(dp(43),dp(38)));
            card.addView(labelRow);
            int height=widgetHeight(widgetId,provider);
            try{
                AppWidgetHostView widget=host.createView(this,widgetId,provider);
                widget.setAppWidget(widgetId,provider);
                widget.setBackgroundColor(0x153a4055);
                card.addView(widget,new LinearLayout.LayoutParams(-1,dp(height)));
            }catch(Exception error){
                TextView failed=text("Виджет временно недоступен — проверьте приложение-поставщик",12,false);
                card.addView(failed,new LinearLayout.LayoutParams(-1,dp(65)));
            }
            LinearLayout controls=new LinearLayout(this);
            controls.addView(action("− Высота",()->setWidgetHeight(widgetId,
                widgetHeight(widgetId,manager.getAppWidgetInfo(widgetId))-40)),
                new LinearLayout.LayoutParams(0,dp(42),1));
            controls.addView(action("+ Высота",()->setWidgetHeight(widgetId,
                widgetHeight(widgetId,manager.getAppWidgetInfo(widgetId))+40)),
                new LinearLayout.LayoutParams(0,dp(42),1));
            controls.addView(action("Удалить",()->remove(widgetId)),
                new LinearLayout.LayoutParams(0,dp(42),1));
            card.addView(controls,new LinearLayout.LayoutParams(-1,dp(45)));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
            cp.bottomMargin=dp(11);
            content.addView(card,cp);
        }
        if(changed)saveIds();
        if(ids.isEmpty()){
            TextView empty=text("Пока нет виджетов. Нажми «Добавить» и выбери календарь, "+
                "часы, задачи или виджет установленного приложения.",14,false);
            empty.setPadding(dp(15),dp(24),dp(15),dp(24));
            content.addView(empty);
        }
        TextView note=text("Виджеты сторонних приложений могут выглядеть по-своему. "+
            "AURA не подменяет их содержимое и не запрашивает доступ к личным данным.",11,false);
        note.setTextColor(0xffaebcd4);
        content.addView(note);
    }

    private void chooseProvider(){
        if(ids.size()>=20){message("Максимум 20 виджетов на странице");return;}
        final List<AppWidgetProviderInfo> providers=manager.getInstalledProviders();
        if(providers==null||providers.isEmpty()){
            message("Не найдено приложений с виджетами");return;
        }
        CharSequence[] names=new CharSequence[providers.size()];
        PackageManager pm=getPackageManager();
        for(int i=0;i<providers.size();i++){
            AppWidgetProviderInfo info=providers.get(i);
            names[i]=info.loadLabel(pm);
        }
        new AlertDialog.Builder(this).setTitle("Добавить виджет Android")
            .setItems(names,(dialog,which)->bindProvider(providers.get(which)))
            .setNegativeButton("Отмена",null)
            .show();
    }
    private void bindProvider(AppWidgetProviderInfo provider){
        if(provider==null)return;
        try{
            pendingId=host.allocateAppWidgetId();
            if(manager.bindAppWidgetIdIfAllowed(pendingId,provider.provider)){
                configureOrSave(pendingId);
            }else{
                Intent bind=new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingId);
                bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,provider.provider);
                startActivityForResult(bind,REQUEST_BIND);
            }
        }catch(Exception e){
            discardPending();
            message("Не удалось подключить виджет: "+e.getMessage());
        }
    }
    private void configureOrSave(int id){
        AppWidgetProviderInfo provider=manager.getAppWidgetInfo(id);
        if(provider==null){discardPending();message("Виджет не привязан системой");return;}
        if(provider.configure==null){saveWidget(id);return;}
        try{
            Intent config=new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            config.setComponent(provider.configure);
            config.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
            startActivityForResult(config,REQUEST_CONFIG);
        }catch(Exception e){
            discardPending();message("Настройка виджета недоступна");
        }
    }
    private void saveWidget(int id){
        if(id<=0)return;
        if(!ids.contains(id))ids.add(id);
        pendingId=AppWidgetManager.INVALID_APPWIDGET_ID;
        saveIds();build();
    }
    private void discardPending(){
        if(pendingId!=AppWidgetManager.INVALID_APPWIDGET_ID){
            try{host.deleteAppWidgetId(pendingId);}catch(RuntimeException ignored){}
        }
        pendingId=AppWidgetManager.INVALID_APPWIDGET_ID;
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=REQUEST_BIND&&request!=REQUEST_CONFIG)return;
        if(result!=RESULT_OK){discardPending();return;}
        int id=data==null?pendingId:data.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,pendingId);
        if(id<=0){discardPending();return;}
        if(request==REQUEST_BIND)configureOrSave(id);
        else saveWidget(id);
    }
    @Override protected void onResume(){
        super.onResume();
        RefreshRateSupport.applyToActivity(this);
        if(!active){
            try{host.startListening();active=true;}
            catch(RuntimeException e){message("Android временно заблокировал обновление виджетов");}
        }
    }
    @Override protected void onPause(){
        if(active){
            try{host.stopListening();}catch(RuntimeException ignored){}
            active=false;
        }
        super.onPause();
    }
    @Override protected void onDestroy(){
        if(isFinishing())discardPending();
        super.onDestroy();
    }
}
