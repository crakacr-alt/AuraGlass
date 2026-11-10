package com.auraglass.app;

import android.Manifest;
import android.app.Activity;
import android.app.WallpaperManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private final int TEXT=0xfff3f0ff, MUTED=0xffc2bfd7;
    private AtmospherePreviewView preview;
    private TextView weatherStatus;
    private final List<Button> weatherButtons=new ArrayList<>(),timeButtons=new ArrayList<>();
    private EditText city;
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private GradientDrawable bg(int color,float corner){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(corner));
        d.setStroke(dp(1),0x3fffffff);return d;
    }
    private TextView text(String s,int sp,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(TEXT);
        if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;
    }
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void pad(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
    private LinearLayout.LayoutParams params(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private void section(LinearLayout parent,String title,String sub){
        TextView t=text(title,19,true);LinearLayout.LayoutParams m=params(-1,-2);m.topMargin=dp(20);parent.addView(t,m);
        if(sub!=null){TextView d=text(sub,12,false);d.setTextColor(MUTED);
            LinearLayout.LayoutParams n=params(-1,-2);n.topMargin=dp(4);parent.addView(d,n);}
    }
    private Button action(String label,Runnable onClick){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(13);b.setTextColor(TEXT);
        b.setBackground(bg(0x4e9591bd,17));b.setOnClickListener(v->onClick.run());return b;
    }
    private LinearLayout surface(){
        LinearLayout box=vertical();pad(box,14,13);box.setBackground(bg(0x3f9f9bba,24));return box;
    }
    private void add(LinearLayout parent,View child,int h){
        LinearLayout.LayoutParams m=params(-1,h);m.topMargin=dp(9);parent.addView(child,m);
    }
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xff17182f);getWindow().setNavigationBarColor(0xff111325);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xff141b35,0xff393353,0xff16192d}));
        LinearLayout content=vertical();pad(content,19,23);scroll.addView(content);
        setContentView(scroll);

        TextView brand=text("AURA GLASS",31,true);brand.setLetterSpacing(.13f);content.addView(brand);
        TextView tag=text("ЖИВАЯ · АДАПТИВНАЯ · ЭЛЕГАНТНАЯ",10,false);
        tag.setTextColor(0xffdbc6ed);add(content,tag,22);
        preview=new AtmospherePreviewView(this);
        LinearLayout.LayoutParams pm=params(-1,265);pm.topMargin=dp(13);content.addView(preview,pm);
        TextView hint=text("Предпросмотр реагирует на все настройки. Применение обоев подтверждает Android.",12,false);
        hint.setTextColor(MUTED);add(content,hint,-2);

        section(content,"Атмосфера","Выбери реальную погоду или художественный эффект.");
        LinearLayout weatherModes=vertical();add(content,weatherModes,-2);
        modeRow(weatherModes,true,new String[]{"auto","clear","rain"},new String[]{"Авто","Ясно","Дождь"});
        modeRow(weatherModes,true,new String[]{"storm","snow","ice","fog"},new String[]{"Гроза","Снег","Лёд","Туман"});
        section(content,"Время суток","В режиме «Авто» зависит от часов телефона.");
        LinearLayout times=vertical();add(content,times,-2);
        modeRow(times,false,new String[]{"auto","morning","day"},new String[]{"Авто","Утро","День"});
        modeRow(times,false,new String[]{"evening","night"},new String[]{"Вечер","Ночь"});

        section(content,"Тонкая настройка",null);
        LinearLayout sliders=surface();add(content,sliders,-2);
        slider(sliders,"Интенсивность эффектов","intensity",.58f);
        slider(sliders,"Скорость движения","speed",.65f);
        slider(sliders,"Плотность стекла","glass",.67f);
        setting(sliders,"Северное сияние","aurora",true);
        setting(sliders,"Звёзды и метеоры","stars",true);
        setting(sliders,"Высокая плавность (до 60 FPS)","high_fps",false);
        setting(sliders,"Уменьшить движение","reduce_motion",false);

        section(content,"Обои и лаунчер","Обои не меняются без подтверждения. Один тап возвращает в настройки Android.");
        LinearLayout actions=surface();add(content,actions,-2);
        add(actions,action("✦  Предпросмотр живых обоев",this::chooseAuraWallpaper),49);
        add(actions,action("▧  Выбрать системные обои",()->safeStart(new Intent(Intent.ACTION_SET_WALLPAPER))),49);
        add(actions,action("◈  Открыть рабочий стол AURA",()->startActivity(new Intent(this,HomeActivity.class))),49);
        add(actions,action("✓  Назначить лаунчер по умолчанию",this::requestHome),49);
        add(actions,action("↶  Вернуться к One UI Home",()->safeStart(new Intent(Settings.ACTION_HOME_SETTINGS))),49);

        section(content,"Погода","Open-Meteo. Встроенная карточка анимирована; обычный Android-виджет обновляется статически.");
        LinearLayout weatherCard=surface();add(content,weatherCard,-2);
        weatherStatus=text("Выберите город для прогноза.",13,false);
        weatherStatus.setTextColor(MUTED);weatherCard.addView(weatherStatus,params(-1,-2));
        city=new EditText(this);city.setSingleLine(true);city.setTextColor(TEXT);
        city.setHintTextColor(MUTED);city.setHint("Например: Дубна");
        city.setText(Config.str(this,"city",""));
        add(weatherCard,city,53);
        add(weatherCard,action("⌕  Найти город и обновить",()->{
            weatherStatus.setText("Загрузка прогноза…");
            WeatherRepository.setCity(this,city.getText().toString(),this::weatherDone);
        }),49);
        add(weatherCard,action("◎  Определить приблизительное местоположение",this::locate),49);
        add(weatherCard,action("↻  Обновить прогноз",()->WeatherRepository.refresh(this,true,this::weatherDone)),49);
        add(weatherCard,action("☀  Открыть Samsung Weather",()->{
            if(!SamsungWeather.open(this))Toast.makeText(this,"Приложение Samsung Weather недоступно",Toast.LENGTH_LONG).show();
        }),49);
        TextView foot=text("AURA GLASS · Alpha 0.1\nБез root, без изменения системных файлов. Совместимость интерфейсов Samsung проверяется на устройстве.",11,false);
        foot.setTextColor(MUTED);LinearLayout.LayoutParams fp=params(-1,-2);fp.topMargin=dp(25);content.addView(foot,fp);
        refreshModes();
        WeatherRepository.refresh(this,false,(ok,msg)->{if(ok)weatherDone(true,msg);});
    }
    private void modeRow(LinearLayout parent,boolean weather,String[] values,String[] labels){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<values.length;i++){
            final String value=values[i];
            Button b=action(labels[i],()->{
                Config.put(this,weather?"weather_mode":"time_mode",value);
                refreshModes();preview.reload();
            });
            if(weather)weatherButtons.add(b);else timeButtons.add(b);
            b.setTag(value);
            LinearLayout.LayoutParams m=new LinearLayout.LayoutParams(0,dp(45),1);
            if(i>0)m.leftMargin=dp(5);row.addView(b,m);
        }
        add(parent,row,-2);
    }
    private void refreshModes(){
        String w=Config.str(this,"weather_mode","auto"),t=Config.str(this,"time_mode","auto");
        for(Button b:weatherButtons)b.setBackground(bg(b.getTag().equals(w)?0xb1b8a2e9:0x4e9591bd,16));
        for(Button b:timeButtons)b.setBackground(bg(b.getTag().equals(t)?0xb1b8a2e9:0x4e9591bd,16));
    }
    private void slider(LinearLayout box,String label,String key,float def){
        TextView title=text(label,13,true);LinearLayout.LayoutParams titleP=params(-1,-2);
        titleP.topMargin=dp(8);box.addView(title,titleP);
        SeekBar bar=new SeekBar(this);bar.setMax(100);
        bar.setProgress((int)(Config.number(this,key,def)*100));
        box.addView(bar,params(-1,38));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar s,int value,boolean fromUser){
                if(fromUser){Config.put(MainActivity.this,key,value/100f);preview.reload();}
            }
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){}
        });
    }
    private void setting(LinearLayout parent,String label,String key,boolean def){
        Switch sw=new Switch(this);sw.setText(label);sw.setTextColor(TEXT);sw.setTextSize(13);
        sw.setChecked(Config.bool(this,key,def));
        sw.setOnCheckedChangeListener((view,checked)->{Config.put(this,key,checked);preview.reload();});
        add(parent,sw,44);
    }
    private void chooseAuraWallpaper(){
        Intent intent=new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            new ComponentName(this,AuraWallpaperService.class));
        if(!safeStart(intent))safeStart(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
    }
    private boolean safeStart(Intent intent){
        try{startActivity(intent);return true;}
        catch(Exception e){Toast.makeText(this,"Эта системная функция недоступна",Toast.LENGTH_LONG).show();return false;}
    }
    private void requestHome(){
        if(Build.VERSION.SDK_INT>=29){
            RoleManager roles=(RoleManager)getSystemService(ROLE_SERVICE);
            if(roles!=null&&roles.isRoleAvailable(RoleManager.ROLE_HOME)){
                safeStart(roles.createRequestRoleIntent(RoleManager.ROLE_HOME));return;
            }
        }
        safeStart(new Intent(Settings.ACTION_HOME_SETTINGS));
    }
    private void weatherDone(boolean ok,String msg){
        weatherStatus.setText(ok?
            WeatherRepository.read(this).city+": "+WeatherRepository.read(this).temp+"°, "+WeatherRepository.read(this).condition():
            msg);
        preview.reload();
    }
    private void locate(){
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},102);return;
        }
        LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
        if(lm==null){weatherStatus.setText("Геолокация недоступна");return;}
        try{
            Location last=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if(last==null)last=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if(last!=null){applyLocation(last);return;}
            if(Build.VERSION.SDK_INT>=30){
                weatherStatus.setText("Определяем местоположение…");
                String provider=lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)?
                    LocationManager.NETWORK_PROVIDER:LocationManager.GPS_PROVIDER;
                lm.getCurrentLocation(provider,null,getMainExecutor(),loc->{
                    if(loc!=null)applyLocation(loc);
                    else weatherStatus.setText("Нет координат. Введите город вручную.");
                });return;
            }
            weatherStatus.setText("Нет координат. Введите город вручную.");
        }catch(Exception e){weatherStatus.setText("Разрешите определение местоположения в настройках.");}
    }
    private void applyLocation(Location location){
        weatherStatus.setText("Получаем погоду…");
        WeatherRepository.setCoordinates(this,location.getLatitude(),location.getLongitude(),"Моё местоположение",this::weatherDone);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==102&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)locate();
    }
    @Override protected void onResume(){super.onResume();if(preview!=null)preview.reload();}
}
