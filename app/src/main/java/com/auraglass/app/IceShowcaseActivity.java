package com.auraglass.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Full-screen interactive ice preview. Uses precisely the same atmosphere renderer
 * as the installed live wallpaper. It never changes the user's system wallpaper.
 */
public final class IceShowcaseActivity extends Activity {
    private IceScene scene;
    private int dp(float n){
        return (int)(getResources().getDisplayMetrics().density*n+.5f);
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        RefreshRateSupport.applyToActivity(this);
        // Explicit demo scene, never installs or replaces the user's wallpaper.
        Config.put(this,"weather_mode","ice");
        Config.put(this,"time_mode","night");
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        FrameLayout root=new FrameLayout(this);
        scene=new IceScene();
        root.addView(scene,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
    }

    private final class IceScene extends View {
        private final Paint type=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final AtmosphereRenderer renderer=new AtmosphereRenderer();
        private final ParallaxMotion motion=new ParallaxMotion(IceShowcaseActivity.this,renderer);
        private final RenderBudget budget=new RenderBudget(IceShowcaseActivity.this);
        private boolean active;
        private final float density=getResources().getDisplayMetrics().density;
        IceScene(){super(IceShowcaseActivity.this);renderer.reload(IceShowcaseActivity.this);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            long start=SystemClock.uptimeMillis();
            renderer.setRenderQuality(budget.quality());
            renderer.draw(c,getWidth(),getHeight(),start);
            type.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
            type.setTextSize(25*density);type.setColor(0xd8e9faff);
            type.setShadowLayer(7*density,0,2*density,0x990d1640);
            c.drawText("AURA  /  FROST",dp(22),getHeight()-dp(146),type);
            type.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            type.setTextSize(12*density);type.setColor(0xc6dfe6fb);
            c.drawText("Проведи пальцем по стеклу",dp(23),getHeight()-dp(116),type);
            c.drawText("Иней вернётся постепенно",dp(23),getHeight()-dp(96),type);
            c.drawText("Назад  ·  выйти из предпросмотра",dp(23),getHeight()-dp(45),type);
            type.clearShadowLayer();
            if(active)FramePacer.schedule(this,budget,start);
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                if(Config.bool(IceShowcaseActivity.this,"haptics",true))
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                renderer.startTouch(event.getX(),event.getY(),getWidth(),getHeight(),event.getEventTime());
                invalidate();return true;
            }
            if(action==MotionEvent.ACTION_MOVE){
                for(int i=0;i<event.getHistorySize();i++){
                    renderer.moveTouch(event.getHistoricalX(i),event.getHistoricalY(i),
                        getWidth(),getHeight(),event.getHistoricalEventTime(i));
                }
                renderer.moveTouch(event.getX(),event.getY(),getWidth(),getHeight(),event.getEventTime());
                invalidate();return true;
            }
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                renderer.endTouch();invalidate();return true;
            }
            return true;
        }
        @Override protected void onAttachedToWindow(){
            super.onAttachedToWindow();active=true;motion.start();invalidate();
        }
        @Override protected void onDetachedFromWindow(){
            active=false;motion.stop();super.onDetachedFromWindow();
        }
        @Override protected void onWindowVisibilityChanged(int visibility){
            super.onWindowVisibilityChanged(visibility);
            active=visibility==VISIBLE;
            if(active){renderer.reload(IceShowcaseActivity.this);motion.start();invalidate();}
            else motion.stop();
        }
        void resume(){
            renderer.reload(IceShowcaseActivity.this);active=true;motion.start();invalidate();
        }
        void pause(){active=false;motion.stop();}
    }
    @Override protected void onResume(){
        super.onResume();
        RefreshRateSupport.applyToActivity(this);
        if(scene!=null)scene.resume();
    }
    @Override protected void onPause(){if(scene!=null)scene.pause();super.onPause();}
}
