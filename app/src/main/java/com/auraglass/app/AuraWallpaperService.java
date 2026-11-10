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
        @Override public void onVisibilityChanged(boolean v){visible=v;if(v)schedule(0);else stop();}
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
            visible=false;stop();super.onSurfaceDestroyed(holder);
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
                if(canvas!=null)renderer.draw(canvas,w,h,start);
            }catch(Exception ignored){
                // A changing surface can be temporarily unavailable during wallpaper transitions.
            }finally{
                if(canvas!=null)try{holder.unlockCanvasAndPost(canvas);}catch(Exception ignored){}
            }
            boolean saver=false;
            PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
            if(pm!=null)saver=pm.isPowerSaveMode();
            boolean high=Config.bool(AuraWallpaperService.this,"high_fps",false);
            int ms=saver?67:(high?17:34);
            long spent=SystemClock.uptimeMillis()-start;
            if(spent>ms)ms=Math.min(100,(int)(spent*1.75f));
            schedule(Math.max(12,ms-spent));
        }
        @Override public void onSharedPreferenceChanged(SharedPreferences prefs,String key){
            renderer.reload(AuraWallpaperService.this);
            if(visible){stop();schedule(0);}
        }
        @Override public void onDestroy(){
            active=false;stop();
            Config.prefs(AuraWallpaperService.this).unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }
    }
}
