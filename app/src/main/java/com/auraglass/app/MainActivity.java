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
    private TextView weatherStatus,activationStatus,climateStatus;
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
        RefreshRateSupport.applyToActivity(this);
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
        TextView touchHint=text("Проведи пальцем по предпросмотру: лёд тает, стекло запотевает и медленно замерзает снова.",12,false);
        touchHint.setTextColor(0xffc9ecf5);
        add(content,touchHint,-2);
        LinearLayout shortcuts=new LinearLayout(this);
        shortcuts.setOrientation(LinearLayout.HORIZONTAL);
        Button icy=action("❄ Ледяная ночь",()->{
            Config.put(this,"weather_mode","ice");
            Config.put(this,"time_mode","night");
            Config.put(this,"aurora",true);
            Config.put(this,"galaxy",true);
            Config.put(this,"comets",true);
            Config.put(this,"ice_enabled",true);
            Config.put(this,"snow_enabled",true);
            refreshModes();
            preview.reload();
        });
        Button clear=action("◈ Живое стекло",()->{
            Config.put(this,"weather_mode","clear");
            Config.put(this,"time_mode","auto");
            refreshModes();preview.reload();
        });
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(48),1);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(48),1);
        right.leftMargin=dp(8);
        shortcuts.addView(icy,left);
        shortcuts.addView(clear,right);
        add(content,shortcuts,48);
        add(content,action("✧  Полноэкранное ледяное стекло · рисовать пальцем",()->{
            Config.put(this,"weather_mode","ice");
            Config.put(this,"time_mode","night");
            Config.put(this,"aurora",true);
            Config.put(this,"galaxy",true);
            Config.put(this,"comets",true);
            Config.put(this,"ice_enabled",true);
            Config.put(this,"snow_enabled",true);
            refreshModes();
            preview.reload();
            startActivity(new Intent(this,IceShowcaseActivity.class));
        }),53);

        section(content,"Применение темы","Android спросит подтверждение, системные обои не заменяются автоматически.");
        LinearLayout activate=surface();add(content,activate,-2);
        activationStatus=text("Проверяем состояние оформления…",13,true);
        activate.addView(activationStatus,params(-1,-2));
        add(activate,action("Установить живые обои AURA",()->Activation.requestWallpaper(this)),50);
        add(activate,action("Назначить AURA главным экраном",()->Activation.requestHome(this)),50);
        add(activate,action("Вернуться к экрану активации",()->startActivity(new Intent(this,SetupActivity.class))),47);
        updateActivationStatus();

        section(content,"Атмосфера","Выбери реальную погоду или художественный эффект.");
        LinearLayout weatherModes=vertical();add(content,weatherModes,-2);
        modeRow(weatherModes,true,new String[]{"auto","clear","rain"},new String[]{"Авто","Ясно","Дождь"});
        modeRow(weatherModes,true,new String[]{"storm","snow","ice","fog"},new String[]{"Гроза","Снег","Лёд","Туман"});
        section(content,"Время суток","В режиме «Авто» зависит от часов телефона.");
        LinearLayout times=vertical();add(content,times,-2);
        modeRow(times,false,new String[]{"auto","morning","day"},new String[]{"Авто","Утро","День"});
        modeRow(times,false,new String[]{"evening","night"},new String[]{"Вечер","Ночь"});

        section(content,"Лёд, звёзды и космос","Независимые эффекты. Прикосновения оставляют след тепла на стекле.");
        LinearLayout iceControls=surface();
        add(content,iceControls,-2);
        setting(iceControls,"Морозные узоры","ice_enabled",true);
        setting(iceControls,"Снег и снежинки","snow_enabled",true);
        setting(iceControls,"Галактика и звёздная пыль","galaxy",true);
        setting(iceControls,"Кометы и светящиеся хвосты","comets",true);
        setting(iceControls,"Тепло пальца · рисовать по льду","touch_melt",true);
        setting(iceControls,"Сугробы по краям виджетов","snow_accumulation",true);
        climateStatus=text("Климатические данные уточняются…",12,false);
        climateStatus.setTextColor(0xffbed9e4);
        add(iceControls,climateStatus,-2);
        refreshClimateStatus();
        setting(iceControls,"Мягкий тактильный отклик","haptics",true);
        setting(iceControls,"Параллакс звёзд и сияния","parallax",false);
        scaledSlider(iceControls,"Глубина параллакса","parallax_strength",.35f,0f,.85f);
        setting(iceControls,"Запотевание обычного стекла","glass_touch",true);
        slider(iceControls,"Интенсивность инея","frost_strength",.92f);
        scaledSlider(iceControls,"Восстановление морозного рисунка","frost_recovery",1f,.4f,2.6f);
        scaledSlider(iceControls,"Радиус следа пальца","touch_radius",.057f,.025f,.12f);

        section(content,"Производительность","Качество будет снижаться автоматически при нагреве или энергосбережении.");
        LinearLayout perf=surface();
        add(content,perf,-2);
        modeRowQuality(perf);
        frameRateControls(perf);
        section(content,"Тонкая настройка",null);
        LinearLayout sliders=surface();add(content,sliders,-2);
        slider(sliders,"Интенсивность эффектов","intensity",.58f);
        slider(sliders,"Скорость движения","speed",.65f);
        slider(sliders,"Плотность стекла","glass",.67f);
        setting(sliders,"Северное сияние","aurora",true);
        setting(sliders,"Молнии во время грозы","lightning",true);
        setting(sliders,"Звёзды и метеоры","stars",true);
        setting(sliders,"Уменьшить движение","reduce_motion",false);

        section(content,"Обои и лаунчер","Обои не меняются без подтверждения. Один тап возвращает в настройки Android.");
        LinearLayout actions=surface();add(content,actions,-2);
        add(actions,action("✦  Установить живые обои",()->Activation.requestWallpaper(this)),49);
        add(actions,action("▧  Выбрать системные обои",()->Activation.chooseSystemWallpaper(this)),49);
        add(actions,action("◈  Открыть рабочий стол AURA",()->startActivity(new Intent(this,HomeActivity.class))),49);
        add(actions,action("✓  Назначить лаунчер по умолчанию",()->Activation.requestHome(this)),49);
        add(actions,action("↶  Вернуться к One UI Home",()->Activation.chooseSystemHome(this)),49);

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
    private void frameRateControls(LinearLayout parent){
        float supported=RefreshRateSupport.maximumHz(this);
        boolean can120=RefreshRateSupport.supports120(this);
        TextView header=text("Частота анимации",15,true);
        LinearLayout.LayoutParams hp=params(-1,-2);
        hp.topMargin=dp(14);
        parent.addView(header,hp);
        LinearLayout row=new LinearLayout(this);
        String[] values={"auto","60","120"};
        String[] labels={"Система","60 FPS","120 FPS"};
        for(int i=0;i<values.length;i++){
            final String target=values[i];
            Button button=action(labels[i],()->{
                Config.put(this,"frame_rate",target);
                RefreshRateSupport.applyToActivity(this);
                preview.reload();
            });
            if("120".equals(target)&&!can120){
                button.setEnabled(false);
                button.setAlpha(.36f);
            }
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(45),1);
            if(i>0)cp.leftMargin=dp(5);
            row.addView(button,cp);
        }
        LinearLayout.LayoutParams rp=params(-1,45);rp.topMargin=dp(8);
        parent.addView(row,rp);
        String info=can120?
            "Экран поддерживает до "+Math.round(supported)+
            " Гц. Режим 120 запрашивает повышенную частоту; реальный FPS зависит от сцены и One UI.":
            "На этом экране режим 120 Гц недоступен (максимум "+Math.round(supported)+" Гц).";
        TextView note=text(info,12,false);note.setTextColor(MUTED);
        add(parent,note,-2);
    }
    private void modeRowQuality(LinearLayout parent){
        LinearLayout row=vertical();
        String[] values={"auto","quality","battery"};
        String[] labels={"Авто","Качество","Экономия"};
        LinearLayout buttons=new LinearLayout(this);
        for(int i=0;i<values.length;i++){
            String value=values[i];
            Button b=action(labels[i],()->{
                Config.put(this,"render_quality",value);
                preview.reload();
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);
            if(i>0)lp.leftMargin=dp(5);
            buttons.addView(b,lp);
        }
        parent.addView(buttons,params(-1,44));
    }
    private void scaledSlider(LinearLayout box,String label,String key,float def,float low,float high){
        TextView heading=text(label,13,true);
        LinearLayout.LayoutParams hp=params(-1,-2);hp.topMargin=dp(8);
        box.addView(heading,hp);
        SeekBar bar=new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(Math.max(0,Math.min(100,
            Math.round((Config.number(this,key,def)-low)/(high-low)*100))));
        box.addView(bar,params(-1,38));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int value,boolean user){
                if(user){Config.put(MainActivity.this,key,low+(high-low)*value/100f);preview.reload();}
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
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
    private void refreshClimateStatus(){
        if(climateStatus==null)return;
        WeatherRepository.Snapshot weather=WeatherRepository.read(this);
        if(!ClimateGlass.fresh(weather)){
            climateStatus.setText("Укажи город: иней будет учитывать температуру и влажность. Сейчас работает художественный режим.");
            return;
        }
        int dew=Math.round(ClimateGlass.dewPoint(weather.temp,weather.humidity));
        climateStatus.setText("Снаружи "+weather.temp+" °C · Влажность "+weather.humidity+
            "% · Точка росы ≈ "+dew+" °C. Это ориентир, а не температура стекла.");
    }
    private void weatherDone(boolean ok,String msg){
        weatherStatus.setText(ok?
            WeatherRepository.read(this).city+": "+WeatherRepository.read(this).temp+"°, "+WeatherRepository.read(this).condition():
            msg);
        preview.reload();
        refreshClimateStatus();
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
    private void updateActivationStatus(){
        if(activationStatus==null)return;
        activationStatus.setText((Activation.isHomeActive(this)?"✓ Лаунчер включён":"○ Лаунчер не выбран")+
            "   ·   "+(Activation.isLiveWallpaperActive(this)?"✓ Живые обои включены":"○ Живые обои не выбраны"));
    }
    @Override protected void onResume(){
        RefreshRateSupport.applyToActivity(this);
        super.onResume();
        if(preview!=null)preview.reload();
        updateActivationStatus();
    }
}
