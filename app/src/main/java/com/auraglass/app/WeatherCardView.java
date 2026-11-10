package com.auraglass.app;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.os.SystemClock;
import android.view.View;
public final class WeatherCardView extends View {
    private final WeatherCardPainter painter=new WeatherCardPainter();
    private long started=SystemClock.uptimeMillis();
    private boolean running,held;
    private float fingerX,fingerY;
    private long lastPress;
    private final Paint ripplePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip=new Path();
    public WeatherCardView(Context c){
        super(c);
        setOnClickListener(v->{if(!SamsungWeather.open(getContext()))getContext().startActivity(
            new android.content.Intent(getContext(),MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));});
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        painter.draw(c,getContext(),getWidth(),getHeight(),WeatherRepository.read(getContext()),
            (SystemClock.uptimeMillis()-started)/1000f);
        long age=SystemClock.uptimeMillis()-lastPress;
        if(lastPress>0&&(held||age<660)){
            float fade=held?1f:Math.max(0,1-age/660f);
            float radius=(52f+(held?0:age*.10f))*getResources().getDisplayMetrics().density;
            ripplePaint.setShader(new RadialGradient(fingerX,fingerY,radius,
                new int[]{((int)(57*fade)<<24)|0x00f6f9ff,((int)(25*fade)<<24)|0x00a3fff3,0x00ffffff},
                null,Shader.TileMode.CLAMP));
            clip.reset();clip.addRoundRect(new RectF(0,0,getWidth(),getHeight()),
                28f*getResources().getDisplayMetrics().density,
                28f*getResources().getDisplayMetrics().density,Path.Direction.CW);
            c.save();c.clipPath(clip);c.drawCircle(fingerX,fingerY,radius,ripplePaint);c.restore();
            ripplePaint.setShader(null);
        }
        if(running){
            android.os.PowerManager pm=(android.os.PowerManager)getContext().getSystemService(Context.POWER_SERVICE);
            boolean saver=pm!=null&&pm.isPowerSaveMode();
            postInvalidateDelayed(saver?95:(Config.bool(getContext(),"high_fps",false)?17:42));
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            fingerX=event.getX();fingerY=event.getY();held=true;lastPress=SystemClock.uptimeMillis();
            invalidate();
        }else if(action==MotionEvent.ACTION_MOVE){
            fingerX=event.getX();fingerY=event.getY();invalidate();
        }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            held=false;lastPress=SystemClock.uptimeMillis();invalidate();
        }
        return super.onTouchEvent(event);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();running=true;invalidate();}
    @Override protected void onDetachedFromWindow(){running=false;super.onDetachedFromWindow();}
    public void refresh(){invalidate();}
}
