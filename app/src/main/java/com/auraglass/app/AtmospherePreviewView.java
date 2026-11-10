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
        motion.stop();if(running)motion.start();
        invalidate();
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        if(w<=1||h<=1)return;
        float rad=28*getResources().getDisplayMetrics().density;
        c.save();
        Path clip=new Path();clip.addRoundRect(new RectF(0,0,w,h),rad,rad,Path.Direction.CW);c.clipPath(clip);
        long start=SystemClock.uptimeMillis();
        renderer.setRenderQuality(budget.quality());
        renderer.draw(c,(int)w,(int)h,start);
        p.setColor(0x20ffffff);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);
        c.drawRoundRect(new RectF(1,1,w-1,h-1),rad,rad,p);p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        p.setTextSize(Math.min(w*.066f,h*.116f));
        p.setTypeface(android.graphics.Typeface.create("sans-serif-light",0));
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
        weather.draw(c,getContext(),cardW,cardH,
            WeatherRepository.read(getContext()),SystemClock.uptimeMillis()/1000f);
        c.restore();
        c.restore();
        if(running)postInvalidateDelayed(budget.nextDelay(start,SystemClock.uptimeMillis()-start));
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event==null)return false;
        int type=event.getActionMasked();
        if(type==MotionEvent.ACTION_DOWN){
            getParent().requestDisallowInterceptTouchEvent(true);
            if(Config.bool(getContext(),"haptics",true))
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            renderer.startTouch(event.getX(),event.getY(),getWidth(),getHeight(),event.getEventTime());
            invalidate();
            return true;
        }
        if(type==MotionEvent.ACTION_MOVE){
            for(int i=0;i<event.getHistorySize();i++){
                renderer.moveTouch(event.getHistoricalX(i),event.getHistoricalY(i),
                    getWidth(),getHeight(),event.getHistoricalEventTime(i));
            }
            renderer.moveTouch(event.getX(),event.getY(),getWidth(),getHeight(),event.getEventTime());
            invalidate();return true;
        }
        if(type==MotionEvent.ACTION_UP||type==MotionEvent.ACTION_CANCEL){
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
