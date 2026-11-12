package com.auraglass.app;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.view.MotionEvent;

/** Live in-app preview, updated only while the window is visible. */
public final class AtmospherePreviewView extends View {
    private final AtmosphereRenderer renderer=new AtmosphereRenderer();
    private final WeatherCardPainter weather=new WeatherCardPainter();
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clippedPreview=new Path();
    private final RectF previewBounds=new RectF();
    private final RectF strokeBounds=new RectF();
    private final android.graphics.Typeface headingTypeface=
        android.graphics.Typeface.create("sans-serif-light",0);
    private WeatherRepository.Snapshot cachedWeather;
    private long lastWeatherRead;
    private boolean running;
    private final RenderBudget budget;
    private final ParallaxMotion motion;
    public AtmospherePreviewView(Context c){
        super(c);
        budget=new RenderBudget(c);
        motion=new ParallaxMotion(c,renderer);
        renderer.reload(c);
    }
    public void reload(){
        renderer.reload(getContext());
        lastWeatherRead=0;
        motion.stop();if(running)motion.start();
        invalidate();
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        float radius=28*getResources().getDisplayMetrics().density;
        clippedPreview.rewind();
        previewBounds.set(0,0,w,h);
        clippedPreview.addRoundRect(previewBounds,radius,radius,Path.Direction.CW);
        strokeBounds.set(1,1,Math.max(1,w-1),Math.max(1,h-1));
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        if(w<=1||h<=1)return;
        float rad=28*getResources().getDisplayMetrics().density;
        c.save();
        c.clipPath(clippedPreview);
        long start=SystemClock.uptimeMillis();
        renderer.setRenderQuality(budget.quality());
        renderer.draw(c,(int)w,(int)h,start);
        p.setColor(0x20ffffff);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);
        c.drawRoundRect(strokeBounds,rad,rad,p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        p.setTextSize(Math.min(w*.066f,h*.116f));
        p.setTypeface(headingTypeface);
        c.drawText("AURA GLASS",w*.066f,h*.175f,p);
        p.setTextSize(Math.min(w*.029f,h*.055f));
        p.setColor(0xe1ffffff);
        c.drawText("Адаптивные живые обои",w*.069f,h*.245f,p);
        float cardW=w*.89f;
        float cardH=cardW*310f/760f;
        if(cardH>h*.43f){cardH=h*.43f;cardW=cardH*760f/310f;}
        float cardX=(w-cardW)/2f;
        float cardY=h-cardH-h*.07f;
        c.save();
        c.translate(cardX,cardY);
        if(cachedWeather==null||start-lastWeatherRead>=30000){
            cachedWeather=WeatherRepository.read(getContext());
            lastWeatherRead=start;
        }
        weather.draw(c,getContext(),cardW,cardH,
            cachedWeather,start/1000f);
        c.restore();
        c.restore();
        if(running)FramePacer.schedule(this,budget,start);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event==null)return false;
        int type=event.getActionMasked();
        int index=event.getActionIndex();
        if(type==MotionEvent.ACTION_DOWN||type==MotionEvent.ACTION_POINTER_DOWN){
            if(type==MotionEvent.ACTION_DOWN){
                getParent().requestDisallowInterceptTouchEvent(true);
                if(Config.bool(getContext(),"haptics",true))
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            }
            renderer.startTouch(event.getPointerId(index),
                event.getX(index),event.getY(index),
                getWidth(),getHeight(),event.getEventTime());
            invalidate();
            return true;
        }
        if(type==MotionEvent.ACTION_MOVE){
            for(int pointer=0;pointer<event.getPointerCount();pointer++){
                int pointerId=event.getPointerId(pointer);
                for(int i=0;i<event.getHistorySize();i++){
                    renderer.moveTouch(pointerId,event.getHistoricalX(pointer,i),
                        event.getHistoricalY(pointer,i),getWidth(),getHeight(),
                        event.getHistoricalEventTime(i));
                }
                renderer.moveTouch(pointerId,event.getX(pointer),event.getY(pointer),
                    getWidth(),getHeight(),event.getEventTime());
            }
            invalidate();return true;
        }
        if(type==MotionEvent.ACTION_POINTER_UP||type==MotionEvent.ACTION_UP){
            renderer.endTouch(event.getPointerId(index));
            if(type==MotionEvent.ACTION_UP)
                getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }
        if(type==MotionEvent.ACTION_CANCEL){
            renderer.endTouch();
            getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }
        return true;
    }
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();running=true;motion.start();invalidate();
    }
    @Override protected void onDetachedFromWindow(){
        running=false;motion.stop();super.onDetachedFromWindow();
    }
    @Override protected void onWindowVisibilityChanged(int visibility){
        super.onWindowVisibilityChanged(visibility);
        running=visibility==VISIBLE;
        if(running){motion.start();invalidate();}
        else motion.stop();
    }
}
