package com.auraglass.app;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;

/**
 * Adaptive per-scene frame budget. The configured 120 Hz setting is a supported
 * target, not a performance claim. Missed deadlines progressively back off.
 */
public final class RenderBudget {
    private final Context context;
    private final PowerManager power;
    private long lastChecked;
    private int thermal;
    private boolean saver;
    private int missedFrames;
    private int targetMs=34;
    private float quality=1;
    private float displayTargetHz=30;

    public RenderBudget(Context context) {
        this.context=context.getApplicationContext();
        power=(PowerManager)this.context.getSystemService(Context.POWER_SERVICE);
    }

    private void update(long now) {
        if(lastChecked>0&&now-lastChecked<2500)return;
        lastChecked=now;
        saver=power!=null&&power.isPowerSaveMode();
        thermal=0;
        if(Build.VERSION.SDK_INT>=29&&power!=null)
            try{thermal=power.getCurrentThermalStatus();}
            catch(RuntimeException ignored){}

        boolean reduced=Config.bool(context,"reduce_motion",false);
        String preference=Config.str(context,"render_quality","auto");
        float screenHz=RefreshRateSupport.requestedHz(context);

        if(saver||thermal>=PowerManager.THERMAL_STATUS_SEVERE){
            targetMs=80;quality=.32f;displayTargetHz=30;
        }else if(thermal>=PowerManager.THERMAL_STATUS_MODERATE){
            targetMs=50;quality=.55f;displayTargetHz=30;
        }else if(reduced||"battery".equals(preference)){
            targetMs=65;quality=.55f;displayTargetHz=30;
        }else{
            // 120 Hz: 8.33ms frame budget. 90Hz: 11.1ms; 60Hz: 16.7ms.
            // 30/60/120 targets are not hard-coded delay floors.
            targetMs=Math.max(8,Math.round(1000f/Math.max(30f,screenHz)));
            quality="quality".equals(preference)?1f:.86f;
            displayTargetHz=screenHz;
        }
    }

    public int nextDelay(long started,long costMs) {
        update(started);
        if(costMs>targetMs*.92f)missedFrames=Math.min(16,missedFrames+1);
        else missedFrames=Math.max(0,missedFrames-1);
        // If the CPU cannot sustain a requested refresh rate, do not run a hot loop.
        int interval=targetMs;
        if(missedFrames>=12)interval=Math.max(interval,33);
        else if(missedFrames>=6)interval=Math.max(interval,17);
        // Rendering and posting Canvas buffers are not VSYNC-locked here.
        // The system scheduler ultimately determines the actual presentation time.
        return (int)Math.max(1,Math.min(100,interval-costMs));
    }

    public float quality() {
        update(SystemClock.uptimeMillis());
        if(missedFrames>=12)return Math.min(quality,.42f);
        if(missedFrames>=6)return Math.min(quality,.6f);
        return quality;
    }

    public float displayTargetHz() {
        update(SystemClock.uptimeMillis());
        return missedFrames>=12?Math.min(displayTargetHz,30):
            missedFrames>=6?Math.min(displayTargetHz,60):displayTargetHz;
    }

    public boolean powerLimited() {
        update(SystemClock.uptimeMillis());
        return saver||thermal>=PowerManager.THERMAL_STATUS_MODERATE;
    }
}
