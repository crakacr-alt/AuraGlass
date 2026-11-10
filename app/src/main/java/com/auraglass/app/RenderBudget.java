package com.auraglass.app;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;

/** A tiny adaptive frame budget. Thermal status is checked at most every 4 seconds. */
public final class RenderBudget {
    private final Context context;
    private final PowerManager power;
    private long lastChecked;
    private int thermal;
    private boolean saver;
    private int missedFrames;
    private long lastFrame;
    private int targetMs=34;
    private float quality=1;

    public RenderBudget(Context context){
        this.context=context.getApplicationContext();
        power=(PowerManager)this.context.getSystemService(Context.POWER_SERVICE);
    }
    private void update(long now){
        if(lastChecked>0&&now-lastChecked<4000)return;
        lastChecked=now;
        saver=power!=null&&power.isPowerSaveMode();
        thermal=0;
        if(Build.VERSION.SDK_INT>=29&&power!=null){
            try{thermal=power.getCurrentThermalStatus();}
            catch(RuntimeException ignored){}
        }
        boolean reduced=Config.bool(context,"reduce_motion",false);
        boolean high=Config.bool(context,"high_fps",false);
        String preference=Config.str(context,"render_quality","auto");
        if(saver||thermal>=PowerManager.THERMAL_STATUS_SEVERE){
            targetMs=80;quality=.36f;
        }else if(thermal>=PowerManager.THERMAL_STATUS_MODERATE){
            targetMs=50;quality=.56f;
        }else if(reduced){
            targetMs=65;quality=.62f;
        }else if("battery".equals(preference)){
            targetMs=50;quality=.55f;
        }else if("quality".equals(preference)){
            targetMs=high?17:33;quality=1f;
        }else{
            targetMs=high?17:33;quality=.88f;
        }
    }
    public int nextDelay(long frameStarted,long frameCost){
        update(frameStarted);
        if(frameCost>targetMs*1.4f)missedFrames=Math.min(12,missedFrames+1);
        else missedFrames=Math.max(0,missedFrames-1);
        int interval=missedFrames>=5?Math.max(targetMs,50):targetMs;
        lastFrame=frameStarted;
        return (int)Math.max(12,Math.min(100,interval-frameCost));
    }
    public float quality(){
        update(SystemClock.uptimeMillis());
        return missedFrames>=5?Math.min(quality,.55f):quality;
    }
    public boolean powerLimited(){
        update(SystemClock.uptimeMillis());
        return saver||thermal>=PowerManager.THERMAL_STATUS_MODERATE;
    }
}
