package com.auraglass.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Native launcher: real applications, editable favorites, searchable drawer, existing wallpaper remains visible. */
public final class HomeActivity extends Activity {
    private FrameLayout root, drawer;
    private TextView clock,date;
    private WeatherCardView weatherCard;
    private AuraIslandView island;
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final List<ResolveInfo> applications = new ArrayList<>();
    private final Runnable clockTick = new Runnable(){
        @Override public void run(){updateClock();timer.postDelayed(this,30000);}
    };
    private int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
    private TextView label(String value,int size,boolean bold){
        TextView text=new TextView(this);
        text.setText(value);text.setTextColor(0xfffaf6ff);text.setTextSize(size);
        if(bold)text.setTypeface(Typeface.create("sans-serif-medium",0));
        return text;
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(0xff181a2d);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        root=new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        setContentView(root);
        loadApplications();
        showHome();
    }
    private void loadApplications(){
        Intent request=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm=getPackageManager();
        applications.clear();
        applications.addAll(pm.queryIntentActivities(request,0));
        Collections.sort(applications,Comparator.comparing(
            app->String.valueOf(app.loadLabel(pm)),String.CASE_INSENSITIVE_ORDER));
    }
    private String componentKey(ResolveInfo item){
        return new ComponentName(item.activityInfo.packageName,item.activityInfo.name).flattenToShortString();
    }
    private List<ResolveInfo> favorites(){
        String saved=Config.str(this,"pinned_apps","");
        if(!Config.prefs(this).contains("pinned_apps")){
            List<ResolveInfo> suggested=selectByPackages(new String[]{
                "com.openai.chatgpt","org.telegram.messenger","com.whatsapp","com.yandex",
                "com.android.gallery","com.sec.android.gallery3d","com.android.camera",
                "com.android.settings"},8);
            if(suggested.size()<8){
                Set<String> existing=new HashSet<>();
                for(ResolveInfo r:suggested)existing.add(componentKey(r));
                for(ResolveInfo r:applications){
                    if(suggested.size()>=8)break;
                    if(existing.add(componentKey(r)))suggested.add(r);
                }
            }
            persistFavorites(suggested);
            return suggested;
        }
        List<ResolveInfo> result=new ArrayList<>();
        for(String key:saved.split(";")){
            for(ResolveInfo r:applications){
                if(componentKey(r).equals(key)){
                    result.add(r);break;
                }
            }
            if(result.size()>=8)break;
        }
        return result;
    }
    private List<ResolveInfo> selectByPackages(String[] matches,int max){
        List<ResolveInfo> result=new ArrayList<>();
        Set<String> visited=new HashSet<>();
        for(String want:matches){
            for(ResolveInfo r:applications){
                String pkg=r.activityInfo.packageName.toLowerCase(Locale.ROOT);
                if(pkg.contains(want.toLowerCase(Locale.ROOT)) && visited.add(componentKey(r))){
                    result.add(r);break;
                }
            }
            if(result.size()>=max)break;
        }
        return result;
    }
    private void persistFavorites(List<ResolveInfo> pinned){
        StringBuilder value=new StringBuilder();
        for(ResolveInfo item:pinned){
            if(value.length()>0)value.append(';');
            value.append(componentKey(item));
        }
        Config.put(this,"pinned_apps",value.toString());
    }
    private void changeFavorite(ResolveInfo item,boolean add){
        List<ResolveInfo> current=favorites();
        String id=componentKey(item);
        if(add){
            for(ResolveInfo r:current)if(componentKey(r).equals(id)){
                Toast.makeText(this,"Уже на главном экране",Toast.LENGTH_SHORT).show();
                return;
            }
            if(current.size()>=8){
                Toast.makeText(this,"На главном экране пока не больше 8 ярлыков",Toast.LENGTH_SHORT).show();
                return;
            }
            current.add(item);
        }else{
            current.removeIf(r->componentKey(r).equals(id));
        }
        persistFavorites(current);
        if(drawer!=null)closeDrawer(false);
        showHome();
        Toast.makeText(this,add?"Ярлык добавлен":"Ярлык удалён",Toast.LENGTH_SHORT).show();
    }
    private void showHome(){
        if(island!=null)island.pause();
        root.removeAllViews();drawer=null;island=null;
        LinearLayout vertical=new LinearLayout(this);
        vertical.setOrientation(LinearLayout.VERTICAL);
        vertical.setPadding(dp(17),dp(101),dp(17),dp(18));
        vertical.setOnApplyWindowInsetsListener((view,insets)->{
            int insetBottom=insets.getSystemWindowInsetBottom();
            view.setPadding(dp(17),Math.max(dp(101),insets.getSystemWindowInsetTop()+dp(68)),dp(17),Math.max(dp(18),dp(12)+insetBottom));
            return insets;
        });
        root.addView(vertical,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts=new LinearLayout(this);texts.setOrientation(LinearLayout.VERTICAL);
        clock=label("",42,false);clock.setTypeface(Typeface.create("sans-serif-light",0));
        clock.setShadowLayer(dp(6),0,dp(2),0x99000000);
        texts.addView(clock);
        date=label("",12,false);
        date.setShadowLayer(dp(4),0,dp(1),0x99000000);
        texts.addView(date);
        header.addView(texts,new LinearLayout.LayoutParams(0,dp(88),1));
        TextView gear=label("⚙",28,false);
        gear.setGravity(Gravity.CENTER);
        gear.setContentDescription("Открыть редактор AURA");
        gear.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
        header.addView(gear,new LinearLayout.LayoutParams(dp(50),dp(50)));
        vertical.addView(header,new LinearLayout.LayoutParams(-1,dp(88)));

        weatherCard=new WeatherCardView(this);
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,dp(150));
        wp.topMargin=dp(13);
        vertical.addView(weatherCard,wp);

        GlassPanel search=new GlassPanel(this);
        TextView searchText=label("⌕    Поиск приложений",14,false);
        searchText.setGravity(Gravity.CENTER_VERTICAL);
        searchText.setPadding(dp(19),0,0,0);
        search.addView(searchText,new FrameLayout.LayoutParams(-1,-1));
        search.setOnClickListener(v->openDrawer());
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(48));
        sp.topMargin=dp(15);vertical.addView(search,sp);

        vertical.addView(new View(this),new LinearLayout.LayoutParams(-1,0,1));

        GlassPanel shortPanel=new GlassPanel(this);
        shortPanel.setPadding(dp(8),dp(8),dp(8),dp(5));
        GridView favorites=icons(favorites(),4,true,false);
        shortPanel.addView(favorites,new FrameLayout.LayoutParams(-1,dp(153)));
        vertical.addView(shortPanel,new LinearLayout.LayoutParams(-1,dp(167)));

        TextView more=label("⌃   Все приложения",13,true);
        more.setGravity(Gravity.CENTER);
        more.setShadowLayer(dp(4),0,1,0x77000000);
        more.setOnClickListener(v->openDrawer());
        vertical.addView(more,new LinearLayout.LayoutParams(-1,dp(42)));

        GlassPanel dock=new GlassPanel(this);
        dock.setPadding(dp(8),dp(4),dp(8),dp(4));
        List<ResolveInfo> common=selectByPackages(new String[]{
            "com.google.android.dialer","com.samsung.android.dialer","com.android.dialer",
            "com.google.android.apps.messaging","com.samsung.android.messaging",
            "com.android.chrome","com.sec.android.app.sbrowser",
            "com.sec.android.app.camera","com.android.camera","com.android.settings"},5);
        if(common.size()<5){
            Set<String> ids=new HashSet<>();
            for(ResolveInfo r:common)ids.add(componentKey(r));
            for(ResolveInfo r:applications){
                if(common.size()>=5)break;
                if(ids.add(componentKey(r)))common.add(r);
            }
        }
        dock.addView(icons(common,5,false,true),new FrameLayout.LayoutParams(-1,dp(71)));
        vertical.addView(dock,new LinearLayout.LayoutParams(-1,dp(80)));
        if(Config.bool(this,"island_enabled",true)){
            island=new AuraIslandView(this);
            int collapsedWidth=Math.min(getResources().getDisplayMetrics().widthPixels-dp(62),dp(258));
            FrameLayout.LayoutParams islandLp=new FrameLayout.LayoutParams(collapsedWidth,dp(57),
                Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            islandLp.topMargin=dp(34);
            root.addView(island,islandLp);
            island.setOnApplyWindowInsetsListener((v,insets)->{
                FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)v.getLayoutParams();
                int safeTop=Math.max(dp(34),insets.getSystemWindowInsetTop()+dp(5));
                if(lp.topMargin!=safeTop){lp.topMargin=safeTop;v.setLayoutParams(lp);}
                return insets;
            });
            island.requestApplyInsets();
        }
        updateClock();
    }
    private GridView icons(List<ResolveInfo> items,int columns,boolean pinnable,boolean dock){
        GridView grid=new GridView(this);
        grid.setNumColumns(columns);
        grid.setGravity(Gravity.CENTER);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(android.R.color.transparent);
        grid.setVerticalSpacing(dp(4));
        grid.setHorizontalSpacing(dp(3));
        grid.setAdapter(new AppAdapter(items,pinnable,dock));
        return grid;
    }
    private void updateClock(){
        if(clock!=null)clock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
        if(date!=null)date.setText(new SimpleDateFormat("EEEE, d MMMM",new Locale("ru")).format(new Date()));
    }
    private void openDrawer(){
        if(drawer!=null)return;
        drawer=new FrameLayout(this);
        drawer.setBackgroundColor(0xf21b1b31);
        root.addView(drawer,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(17),dp(55),dp(17),dp(27));
        drawer.addView(content,new FrameLayout.LayoutParams(-1,-1));
        TextView title=label("Все приложения     ✕",22,true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setOnClickListener(v->closeDrawer(true));
        content.addView(title,new LinearLayout.LayoutParams(-1,dp(57)));
        TextView hint=label("Удерживайте иконку, чтобы закрепить на главном экране",12,false);
        hint.setTextColor(0xffbfc0d7);
        content.addView(hint,new LinearLayout.LayoutParams(-1,dp(36)));
        EditText filter=new EditText(this);
        filter.setSingleLine(true);filter.setTextColor(Color.WHITE);
        filter.setHintTextColor(0xffbbbace);filter.setHint("Поиск");
        content.addView(filter,new LinearLayout.LayoutParams(-1,dp(54)));
        List<ResolveInfo> results=new ArrayList<>(applications);
        GridView list=icons(results,4,false,false);
        AppAdapter adapter=(AppAdapter)list.getAdapter();
        LinearLayout.LayoutParams grid=new LinearLayout.LayoutParams(-1,0,1);
        grid.topMargin=dp(12);content.addView(list,grid);
        filter.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int n,int a){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                results.clear();
                String q=s.toString().toLowerCase(Locale.ROOT);
                for(ResolveInfo app:applications)
                    if(String.valueOf(app.loadLabel(getPackageManager())).toLowerCase(Locale.ROOT).contains(q))
                        results.add(app);
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable editable){}
        });
        drawer.setAlpha(0);
        drawer.animate().alpha(1).setDuration(230)
            .setInterpolator(new DecelerateInterpolator()).start();
    }
    private void closeDrawer(boolean animate){
        FrameLayout old=drawer;
        if(old==null)return;
        drawer=null;
        if(animate){
            old.animate().alpha(0).setDuration(160)
                .withEndAction(()->root.removeView(old)).start();
        }else root.removeView(old);
    }
    private void launch(ResolveInfo app){
        try{
            Intent intent=new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(app.activityInfo.packageName,app.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }catch(Exception e){
            Toast.makeText(this,"Приложение не открылось",Toast.LENGTH_SHORT).show();
        }
    }
    private final class AppAdapter extends BaseAdapter {
        private final List<ResolveInfo> items;
        private final boolean onHome,dock;
        AppAdapter(List<ResolveInfo> items,boolean onHome,boolean dock){
            this.items=items;this.onHome=onHome;this.dock=dock;
        }
        @Override public int getCount(){return items.size();}
        @Override public Object getItem(int position){return items.get(position);}
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View recycled,ViewGroup parent){
            ResolveInfo entry=items.get(position);
            LinearLayout cell=new LinearLayout(HomeActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(3),dp(4),dp(3),dp(2));
            ImageView image=new ImageView(HomeActivity.this);
            try{image.setImageDrawable(entry.loadIcon(getPackageManager()));}
            catch(Exception ignored){}
            cell.addView(image,new LinearLayout.LayoutParams(dp(dock?43:44),dp(dock?43:44)));
            TextView text=label(String.valueOf(entry.loadLabel(getPackageManager())),dock?9:10,false);
            text.setSingleLine(true);
            text.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.setGravity(Gravity.CENTER);
            text.setShadowLayer(dp(2),0,1,0x88000000);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(22));
            cell.addView(text,lp);
            cell.setOnClickListener(v->launch(entry));
            cell.setOnLongClickListener(v->{
                new AlertDialog.Builder(HomeActivity.this)
                    .setTitle(entry.loadLabel(getPackageManager()))
                    .setItems(onHome?new String[]{"Убрать с главного экрана"}:
                        new String[]{"Закрепить на главном экране"},(dialog,choice)->
                        changeFavorite(entry,!onHome))
                    .setNegativeButton("Отмена",null)
                    .show();
                return true;
            });
            return cell;
        }
    }
    @Override protected void onResume(){
        super.onResume();
        timer.removeCallbacks(clockTick);clockTick.run();
        WeatherRepository.refresh(this,false,(ok,msg)->{
            if(weatherCard!=null)weatherCard.refresh();
        });
        if(weatherCard!=null)weatherCard.refresh();
        if((island!=null)!=Config.bool(this,"island_enabled",true))showHome();
        if(island!=null)island.resume();
    }
    @Override protected void onPause(){
        if(island!=null)island.pause();
        timer.removeCallbacks(clockTick);super.onPause();
    }
    @Override public void onBackPressed(){
        if(drawer!=null)closeDrawer(true);
        // The default HOME activity stays open on back, like regular launchers.
    }
}
