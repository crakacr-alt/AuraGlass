package com.auraglass.app;
import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
public final class WeatherCardView extends View {
    private final WeatherCardPainter painter=new WeatherCardPainter();
    private long started=SystemClock.uptimeMillis();
    private boolean running;
    public WeatherCardView(Context c){
        super(c);
        setOnClickListener(v->{if(!SamsungWeather.open(getContext()))getContext().startActivity(
            new android.content.Intent(getContext(),MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));});
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        painter.draw(c,getContext(),getWidth(),getHeight(),WeatherRepository.read(getContext()),
            (SystemClock.uptimeMillis()-started)/1000f);
        if(running){
            android.os.PowerManager pm=(android.os.PowerManager)getContext().getSystemService(Context.POWER_SERVICE);
            boolean saver=pm!=null&&pm.isPowerSaveMode();
            postInvalidateDelayed(saver?67:(Config.bool(getContext(),"high_fps",false)?17:34));
        }
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();running=true;invalidate();}
    @Override protected void onDetachedFromWindow(){running=false;super.onDetachedFromWindow();}
    public void refresh(){invalidate();}
}
