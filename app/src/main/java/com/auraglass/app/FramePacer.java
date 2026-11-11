package com.auraglass.app;

import android.os.SystemClock;
import android.view.View;

/**
 * Synchronize animated Views with display VSYNC when they target the current
 * physical refresh rate. postInvalidateDelayed(16) after a 60 Hz draw can skip
 * the immediately upcoming VSYNC and accidentally cap animations at ~30 FPS.
 * Lower modes use RenderBudget's deliberate frame pacing and thermal fallback.
 */
public final class FramePacer {
    private FramePacer(){}
    public static void schedule(View view,RenderBudget budget,long frameStarted){
        if(view==null||budget==null||!view.isShown())return;
        int delay=budget.nextDelay(frameStarted,
            Math.max(0,SystemClock.uptimeMillis()-frameStarted));
        float current=RefreshRateSupport.currentHz(view.getContext());
        float allowed=budget.displayTargetHz();
        if(!budget.powerLimited()&&allowed>=current-1.5f){
            view.postInvalidateOnAnimation();
        }else{
            view.postInvalidateDelayed(delay);
        }
    }
}
