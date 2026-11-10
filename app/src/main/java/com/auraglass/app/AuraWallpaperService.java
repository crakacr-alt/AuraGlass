package com.auraglass.app;

import android.service.wallpaper.WallpaperService;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.MotionEvent;

/** Only renders while visible; never changes the user's wallpaper without confirmation. */
public final class AuraWallpaperService extends WallpaperService {
    @Override public Engine onCreateEngine(){return new AuraEngine();}
    private final class AuraEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        private final Handler handler=new Handler(Looper.getMainLooper());
        private final AtmosphereRenderer renderer=new AtmosphereRenderer();
        private final ParallaxMotion motion=new ParallaxMotion(AuraWallpaperService.this,renderer);
        private final RenderBudget budget=new RenderBudget(AuraWallpaperService.this);
        private boolean visible=false,active=true,queued=false;
        private int w=1,h=1;
        private final Runnable frame=new Runnable(){
            @Override public void run(){queued=false;render();}
        };
        AuraEngine(){
            renderer.reload(AuraWallpaperService.this);
            setTouchEventsEnabled(true);
            Config.prefs(AuraWallpaperService.this).registerOnSharedPreferenceChangeListener(this);
        }
        @Override public void onVisibilityChanged(boolean v){
            visible=v;
            if(v){motion.start();schedule(0);}
            else{motion.stop();stop();}
        }
        @Override public void onSurfaceChanged(SurfaceHolder holder,int format,int width,int height){
            super.onSurfaceChanged(holder,format,width,height);
            w=width;h=height;schedule(0);
        }
        @Override public void onTouchEvent(MotionEvent event){
            if(!active||!visible||event==null)return;
            int type=event.getActionMasked();
            if(type==MotionEvent.ACTION_DOWN){
                renderer.startTouch(event.getX(),event.getY(),w,h,event.getEventTime());
                schedule(0);
            }else if(type==MotionEvent.ACTION_MOVE){
                int n=event.getHistorySize();
                for(int i=0;i<n;i++){
                    renderer.moveTouch(event.getHistoricalX(i),event.getHistoricalY(i),w,h,
                        event.getHistoricalEventTime(i));
                }
                renderer.moveTouch(event.getX(),event.getY(),w,h,event.getEventTime());
                schedule(0);
            }else if(type==MotionEvent.ACTION_UP||type==MotionEvent.ACTION_CANCEL){
                renderer.endTouch();
            }
        }
        @Override public void onSurfaceDestroyed(SurfaceHolder holder){
            visible=false;motion.stop();stop();super.onSurfaceDestroyed(holder);
        }
        private void stop(){handler.removeCallbacks(frame);queued=false;}
        private void schedule(long delay){
            if(!visible||!active||queued)return;
            queued=true;handler.postDelayed(frame,delay);
        }
        private void render(){
            if(!visible||!active)return;
            SurfaceHolder holder=getSurfaceHolder();
            Canvas canvas=null;
            long start=SystemClock.uptimeMillis();
            try{
                canvas=holder.lockCanvas();
                if(canvas!=null){
                    renderer.setRenderQuality(budget.quality());
                    renderer.draw(canvas,w,h,start);
                }
            }catch(Exception ignored){
                // A changing surface can be temporarily unavailable during wallpaper transitions.
            }finally{
                if(canvas!=null)try{holder.unlockCanvasAndPost(canvas);}catch(Exception ignored){}
            }
            long spent=SystemClock.uptimeMillis()-start;
            schedule(budget.nextDelay(start,spent));
        }
        @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key){
            renderer.reload(AuraWallpaperService.this);
            if(visible){motion.stop();motion.start();stop();schedule(0);}
        }
        @Override public void onDestroy(){
            active=false;motion.stop();stop();
            Config.prefs(AuraWallpaperService.this).unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }
    }
}
