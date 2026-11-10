package com.auraglass.app;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** Live in-app preview, updated only while the window is visible. */
public final class AtmospherePreviewView extends View {
    private final AtmosphereRenderer renderer=new AtmosphereRenderer();
    private final WeatherCardPainter weather=new WeatherCardPainter();
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean running;
    public AtmospherePreviewView(Context c){super(c);renderer.reload(c);}
    public void reload(){renderer.reload(getContext());invalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        if(w<=1||h<=1)return;
        float rad=28*getResources().getDisplayMetrics().density;
        c.save();
        Path clip=new Path();clip.addRoundRect(new RectF(0,0,w,h),rad,rad,Path.Direction.CW);c.clipPath(clip);
        renderer.draw(c,(int)w,(int)h,SystemClock.uptimeMillis());
        p.setColor(0x20ffffff);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);
        c.drawRoundRect(new RectF(1,1,w-1,h-1),rad,rad,p);p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);p.setTextSize(w*.082f);
        p.setTypeface(android.graphics.Typeface.create("sans-serif-light",0));
        c.drawText("AURA GLASS",w*.072f,h*.16f,p);
        p.setTextSize(w*.035f);p.setColor(0xd9ffffff);
        c.drawText("Живая атмосфера · предпросмотр",w*.072f,h*.205f,p);
        c.save();
        c.translate(w*.06f,h*.63f);
        weather.draw(c,getContext(),w*.88f,h*.28f,
            WeatherRepository.read(getContext()),SystemClock.uptimeMillis()/1000f);
        c.restore();
        c.restore();
        if(running){
            android.os.PowerManager pm=(android.os.PowerManager)getContext().getSystemService(Context.POWER_SERVICE);
            boolean saver=pm!=null&&pm.isPowerSaveMode();
            postInvalidateDelayed(saver?67:(Config.bool(getContext(),"high_fps",false)?17:34));
        }
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();running=true;invalidate();}
    @Override protected void onDetachedFromWindow(){running=false;super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){
        super.onWindowVisibilityChanged(visibility);
        running=visibility==VISIBLE;
        if(running)invalidate();
    }
}
