package com.auraglass.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * On-device, user-initiated AURA diagnostic report. No uploads and no
 * silent background measurement. FrameMetrics covers only this Activity;
 * it is not proof of the wallpaper or GPU delivering 120 FPS.
 */
public final class DiagnosticsActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<Long> frameDurations=new ArrayList<>();
    private TextView baseline,results;
    private Button measureButton;
    private DiagnosticScene scene;
    private boolean collecting,listenerRegistered;
    private long droppedReports;
    private int sampledRefreshChanges;
    private float firstDisplayHz;
    private float lastDisplayHz;
    private long started;
    private String measurement="Не выполнялось";
    private final Window.OnFrameMetricsAvailableListener listener=(window,metrics,dropped)->{
        if(!collecting)return;
        if(dropped>0)droppedReports+=dropped;
        long ns=metrics.getMetric(FrameMetrics.TOTAL_DURATION);
        if(ns>0&&ns<1000000000L&&frameDurations.size()<2500)
            frameDurations.add(ns);
    };
    private final Runnable finish=()->finishMeasurement();

    private int dp(float value){
        return (int)(value*getResources().getDisplayMetrics().density+.5f);
    }
    private GradientDrawable surface(){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xff2a3955,0xff514662,0xff2a3c4b});
        g.setCornerRadius(dp(21));
        g.setStroke(dp(1),0x83dae4ff);
        return g;
    }
    private TextView text(String value,int size){
        TextView v=new TextView(this);
        v.setText(value);v.setTextSize(size);
        v.setTextColor(0xfff3f6ff);
        v.setPadding(dp(4),dp(5),dp(4),dp(5));
        return v;
    }
    private Button button(String title,Runnable click){
        Button b=new Button(this);
        b.setText(title);b.setAllCaps(false);b.setTextSize(13);
        b.setTextColor(0xfff6f6ff);
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xc06f759e,0xd051537c});
        bg.setCornerRadius(dp(15));
        bg.setStroke(dp(1),0x74f3ffff);
        b.setBackground(bg);
        b.setOnClickListener(v->click.run());return b;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        RefreshRateSupport.applyToActivity(this);
        getWindow().setStatusBarColor(0xff19243a);
        getWindow().setNavigationBarColor(0xff111b29);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff141e32);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(17),dp(20),dp(17),dp(38));
        scroll.addView(body);
        setContentView(scroll);
        TextView heading=text("AURA · ДИАГНОСТИКА",25);heading.setTypeface(null,1);
        body.addView(heading);
        TextView subtitle=text("Проверки выполняются локально и только по нажатию.",12);
        subtitle.setTextColor(0xffc4cfe6);body.addView(subtitle);
        baseline=text("",13);
        baseline.setPadding(dp(14),dp(14),dp(14),dp(14));
        baseline.setBackground(surface());
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);
        bp.topMargin=dp(10);bp.bottomMargin=dp(8);
        body.addView(baseline,bp);
        scene=new DiagnosticScene();
        scene.setBackground(surface());
        LinearLayout.LayoutParams preview=new LinearLayout.LayoutParams(-1,dp(260));
        preview.topMargin=dp(8);preview.bottomMargin=dp(10);
        body.addView(scene,preview);
        measureButton=button("Измерить 8 секунд",this::startMeasurement);
        body.addView(measureButton,new LinearLayout.LayoutParams(-1,dp(52)));
        results=text("Для проверки кадров нажмите кнопку. Это измерение тестовой сцены, не всего One UI.",12);
        results.setPadding(dp(12),dp(9),dp(12),dp(10));
        results.setTextColor(0xffcedef7);
        body.addView(results,new LinearLayout.LayoutParams(-1,-2));
        body.addView(button("Обновить сведения об устройстве",()->baseline.setText(reportDevice())),
            new LinearLayout.LayoutParams(-1,dp(50)));
        body.addView(button("Поделиться отчётом",this::share),
            new LinearLayout.LayoutParams(-1,dp(50)));
        body.addView(button("Открыть полный тест льда",()->
            startActivity(new Intent(this,IceShowcaseActivity.class))),
            new LinearLayout.LayoutParams(-1,dp(50)));
        body.addView(button("← Назад",this::finish),
            new LinearLayout.LayoutParams(-1,dp(45)));
        baseline.setText(reportDevice());
    }
    private String reportDevice(){
        StringBuilder s=new StringBuilder();
        s.append("Устройство: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL);
        s.append("\nAndroid API: ").append(Build.VERSION.SDK_INT);
        try{
            PackageInfo pkg=getPackageManager().getPackageInfo(getPackageName(),0);
            s.append("\nAURA: ").append(pkg.versionName);
        }catch(Exception ignored){}
        s.append("\nДисплей сейчас: ").append(String.format(Locale.ROOT,"%.1f",RefreshRateSupport.currentHz(this))).append(" Гц");
        s.append("\nМаксимум: ").append(String.format(Locale.ROOT,"%.1f",RefreshRateSupport.maximumHz(this))).append(" Гц");
        s.append("\nВыбранный режим: ").append(Config.str(this,"frame_rate","auto"));
        WeatherRepository.Snapshot climate=WeatherRepository.read(this);
        SolarLighting sunlight=new SolarLighting();
        sunlight.load(climate.solarRises,climate.solarSets);
        float[] solarOutput=new float[2];
        s.append("\nОсвещение: ").append(
            sunlight.sample(System.currentTimeMillis(),solarOutput)?
                "по восходу/закату выбранного города":"без прогноза — приблизительно");
        s.append("\nЛаунчер по умолчанию: ").append(Activation.isHomeActive(this)?"AURA":"другой");
        s.append("\nОбои AURA: ").append(Activation.isLiveWallpaperActive(this)?"включены":"не выбраны");
        s.append("\nДоступ к уведомлениям: ").append(AuraEventListener.granted(this)?"разрешён":"нет");
        try{
            java.util.List<?> providers=AppWidgetManager.getInstance(this).getInstalledProviders();
            s.append("\nДоступные провайдеры виджетов: ")
                .append(providers==null?"неизвестно":providers.size());
        }catch(RuntimeException e){
            s.append("\nВиджеты: сведения недоступны");
        }
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        if(pm!=null){
            s.append("\nЭнергосбережение: ").append(pm.isPowerSaveMode()?"включено":"выключено");
            if(Build.VERSION.SDK_INT>=29){
                try{s.append("\nТепловой статус Android: ").append(pm.getCurrentThermalStatus());}
                catch(RuntimeException ignored){}
            }
        }
        return s.toString();
    }
    private void startMeasurement(){
        if(collecting)return;
        frameDurations.clear();
        droppedReports=0;
        sampledRefreshChanges=0;
        firstDisplayHz=RefreshRateSupport.currentHz(this);
        lastDisplayHz=firstDisplayHz;
        measurement="Выполняется тест 8 секунд…";
        results.setText(measurement);
        measureButton.setEnabled(false);
        RefreshRateSupport.applyToActivity(this);
        try{
            getWindow().addOnFrameMetricsAvailableListener(listener,main);
            listenerRegistered=true;
            collecting=true;
            started=SystemClock.elapsedRealtime();
            scene.start();
            main.post(refreshSampler);
            main.postDelayed(finish,8000);
        }catch(RuntimeException e){
            collecting=false;
            measureButton.setEnabled(true);
            measurement="FrameMetrics недоступен на этом устройстве";
            results.setText(measurement);
        }
    }
    private final Runnable refreshSampler=new Runnable(){
        @Override public void run(){
            if(!collecting)return;
            float hz=RefreshRateSupport.currentHz(DiagnosticsActivity.this);
            if(Math.abs(hz-lastDisplayHz)>1f)sampledRefreshChanges++;
            lastDisplayHz=hz;
            main.postDelayed(this,500);
        }
    };
    private void finishMeasurement(){
        if(!collecting)return;
        collecting=false;
        scene.stop();
        main.removeCallbacks(finish);
        main.removeCallbacks(refreshSampler);
        if(listenerRegistered){
            try{getWindow().removeOnFrameMetricsAvailableListener(listener);}
            catch(RuntimeException ignored){}
            listenerRegistered=false;
        }
        long elapsed=SystemClock.elapsedRealtime()-started;
        ArrayList<Long> values=new ArrayList<>(frameDurations);
        Collections.sort(values);
        int count=values.size();
        if(count==0){
            measurement="FrameMetrics не предоставил измерений кадров.";
        }else{
            long sum=0;
            double hz=Math.max(30,RefreshRateSupport.currentHz(this));
            double budget=1000000000.0/hz;
            int slow=0;
            for(Long value:values){
                sum+=value;
                if(value>budget)slow++;
            }
            long p95=values.get(Math.min(count-1,(int)Math.ceil(count*.95)-1));
            measurement=String.format(Locale.ROOT,
                "Тестовая сцена, %d мс\nКадров: %d\nСреднее время кадра: %.2f мс\n"+
                "95-й процентиль: %.2f мс\nКадров длиннее бюджета %.2f мс: %d (%.1f%%)\n"+
                "Запрошенная частота: %.1f Гц\n"+
                "Реальная частота дисплея: %.1f → %.1f Гц (изменений: %d)\n"+
                "Сообщения о пропущенных кадрах: %d\n"+
                "Важно: FrameMetrics измеряет кадры только этого окна. "+
                "Результат не подтверждает реальную частоту обоев, анимаций лаунчера и игр.",
                elapsed,count,sum/(double)count/1000000,p95/1000000.0,budget/1000000,
                slow,slow*100.0/count,RefreshRateSupport.requestedHz(this),
                firstDisplayHz,lastDisplayHz,sampledRefreshChanges,droppedReports);
        }
        results.setText(measurement);
        measureButton.setEnabled(true);
    }
    private void share(){
        String report="AURA GLASS — локальная диагностика\n"+
            reportDevice()+"\n\n"+measurement+
            "\n\nНе включает личные уведомления, аккаунты или данные приложений.";
        Intent share=new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT,report);
        try{startActivity(Intent.createChooser(share,"Поделиться отчётом AURA"));}
        catch(RuntimeException ignored){}
    }
    @Override protected void onPause(){
        finishMeasurement();
        super.onPause();
    }

    private final class DiagnosticScene extends View {
        private final AtmosphereRenderer renderer=new AtmosphereRenderer();
        private final Paint border=new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean moving;
        DiagnosticScene(){
            super(DiagnosticsActivity.this);
            renderer.reload(DiagnosticsActivity.this);
        }
        void start(){moving=true;invalidate();}
        void stop(){moving=false;}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            long now=SystemClock.uptimeMillis();
            renderer.draw(c,getWidth(),getHeight(),now);
            border.setColor(0x55e1f8ff);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(dp(1));
            c.drawRoundRect(dp(1),dp(1),getWidth()-dp(1),getHeight()-dp(1),
                dp(20),dp(20),border);
            border.setStyle(Paint.Style.FILL);
            if(moving)postInvalidateOnAnimation();
        }
    }
}
