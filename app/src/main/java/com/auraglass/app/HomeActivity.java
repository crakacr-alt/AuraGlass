package com.auraglass.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** First functional AURA home: real app icons, app drawer, weather and system wallpaper passthrough. */
public final class HomeActivity extends Activity {
    private FrameLayout root;
    private FrameLayout drawer;
    private TextView clock,date;
    private WeatherCardView card;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<ResolveInfo> apps=new ArrayList<>();
    private final Runnable tick=new Runnable(){
        @Override public void run(){updateClock();handler.postDelayed(this,30000);}
    };
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private TextView label(String s,int size){
        TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);
        v.setTextSize(size);return v;
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        root=new FrameLayout(this);
        setContentView(root);
        loadApps();
        buildHome();
    }
    private void loadApps(){
        Intent request=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm=getPackageManager();
        apps.clear();
        apps.addAll(pm.queryIntentActivities(request,0));
        Collections.sort(apps,Comparator.comparing(r->String.valueOf(r.loadLabel(pm)),String.CASE_INSENSITIVE_ORDER));
    }
    private void buildHome(){
        root.removeAllViews();
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(17),dp(40),dp(17),dp(23));
        root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headline=new LinearLayout(this);headline.setOrientation(LinearLayout.VERTICAL);
        clock=label("",39);clock.setTypeface(android.graphics.Typeface.create("sans-serif-light",0));
        clock.setShadowLayer(dp(4),0,dp(2),0x70000000);headline.addView(clock);
        date=label("",12);date.setShadowLayer(dp(3),0,dp(1),0x80000000);
        headline.addView(date);
        header.addView(headline,new LinearLayout.LayoutParams(0,dp(87),1));
        TextView settings=label("⚙",27);settings.setGravity(Gravity.CENTER);
        settings.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
        header.addView(settings,new LinearLayout.LayoutParams(dp(51),dp(51)));
        content.addView(header);
        card=new WeatherCardView(this);
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,dp(160));wp.topMargin=dp(14);
        content.addView(card,wp);
        TextView search=label("⌕   Поиск приложений",14);search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(19),0,dp(12),0);
        GlassPanel searchPanel=new GlassPanel(this);searchPanel.addView(search,new FrameLayout.LayoutParams(-1,-1));
        searchPanel.setOnClickListener(v->openDrawer());
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(52));sp.topMargin=dp(17);
        content.addView(searchPanel,sp);
        View flexible=new View(this);content.addView(flexible,new LinearLayout.LayoutParams(-1,0,1));
        GlassPanel icons=new GlassPanel(this);icons.setPadding(dp(10),dp(12),dp(10),dp(8));
        GridView grid=gridFor(apps.subList(0,Math.min(12,apps.size())));
        icons.addView(grid,new FrameLayout.LayoutParams(-1,dp(258)));
        content.addView(icons,new LinearLayout.LayoutParams(-1,dp(276)));
        TextView all=label("  ●  ●  ●      ПРИЛОЖЕНИЯ      ●  ●  ●  ",11);
        all.setGravity(Gravity.CENTER);
        all.setOnClickListener(v->openDrawer());
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(48));ap.topMargin=dp(4);
        content.addView(all,ap);
        updateClock();
    }
    private GridView gridFor(List<ResolveInfo> source){
        GridView grid=new GridView(this);
        grid.setNumColumns(4);grid.setHorizontalSpacing(dp(3));grid.setVerticalSpacing(dp(12));
        grid.setGravity(Gravity.CENTER);grid.setSelector(android.R.color.transparent);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setAdapter(new IconAdapter(source));
        return grid;
    }
    private void updateClock(){
        if(clock!=null)clock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
        if(date!=null)date.setText(new SimpleDateFormat("EEEE, d MMMM",new Locale("ru")).format(new Date()));
    }
    private void openDrawer(){
        if(drawer!=null)return;
        drawer=new FrameLayout(this);drawer.setBackgroundColor(0xe6191b32);
        root.addView(drawer,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16),dp(49),dp(16),dp(28));
        drawer.addView(body,new FrameLayout.LayoutParams(-1,-1));
        TextView top=label("Все приложения     ✕",22);top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOnClickListener(v->closeDrawer());
        body.addView(top,new LinearLayout.LayoutParams(-1,dp(60)));
        EditText filter=new EditText(this);filter.setSingleLine(true);
        filter.setHint("Поиск");filter.setTextColor(Color.WHITE);filter.setHintTextColor(0xffbbbace);
        body.addView(filter,new LinearLayout.LayoutParams(-1,dp(54)));
        List<ResolveInfo> results=new ArrayList<>(apps);
        GridView grid=gridFor(results);IconAdapter adapter=(IconAdapter)grid.getAdapter();
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,0,1);gp.topMargin=dp(18);body.addView(grid,gp);
        filter.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                results.clear();
                for(ResolveInfo r:apps){
                    if(String.valueOf(r.loadLabel(getPackageManager())).toLowerCase(Locale.ROOT)
                        .contains(s.toString().toLowerCase(Locale.ROOT)))results.add(r);
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable e){}
        });
    }
    private void closeDrawer(){
        if(drawer!=null){root.removeView(drawer);drawer=null;}
    }
    private final class IconAdapter extends BaseAdapter {
        private final List<ResolveInfo> items;
        IconAdapter(List<ResolveInfo> list){items=list;}
        @Override public int getCount(){return items.size();}
        @Override public Object getItem(int i){return items.get(i);}
        @Override public long getItemId(int i){return i;}
        @Override public View getView(int index,View old,ViewGroup parent){
            ResolveInfo app=items.get(index);
            LinearLayout cell=new LinearLayout(HomeActivity.this);cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);cell.setPadding(dp(2),dp(5),dp(2),0);
            ImageView icon=new ImageView(HomeActivity.this);
            try{icon.setImageDrawable(app.loadIcon(getPackageManager()));}catch(Exception ignored){}
            cell.addView(icon,new LinearLayout.LayoutParams(dp(46),dp(46)));
            TextView name=label(String.valueOf(app.loadLabel(getPackageManager())),10);
            name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);name.setShadowLayer(dp(2),0,1,0x99000000);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(24));lp.topMargin=dp(2);
            cell.addView(name,lp);
            cell.setOnClickListener(v->{
                try{
                    Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                    launch.setClassName(app.activityInfo.packageName,app.activityInfo.name);
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                }catch(Exception e){
                    android.widget.Toast.makeText(HomeActivity.this,"Не удалось открыть приложение",android.widget.Toast.LENGTH_SHORT).show();
                }
            });
            return cell;
        }
    }
    @Override protected void onResume(){
        super.onResume();handler.removeCallbacks(tick);tick.run();
        WeatherRepository.refresh(this,false,(ok,msg)->{if(card!=null)card.refresh();});
        if(card!=null)card.refresh();
    }
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
    @Override public void onBackPressed(){
        if(drawer!=null)closeDrawer();else super.onBackPressed();
    }
}
