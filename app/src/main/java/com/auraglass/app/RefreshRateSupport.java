package com.auraglass.app;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;

/**
 * Requests supported refresh rates without forcing a display resolution change.
 * A 120 Hz request is a preference, not a guarantee that Canvas renders 120 FPS.
 */
public final class RefreshRateSupport {
    private RefreshRateSupport() {}

    private static Display display(Context context) {
        WindowManager wm=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        return wm==null?null:wm.getDefaultDisplay();
    }

    public static float maximumHz(Context context) {
        Display display=display(context);
        if(display==null)return 60f;
        float maximum=0;
        try {
            if(Build.VERSION.SDK_INT>=23) {
                Display.Mode active=display.getMode();
                for(Display.Mode candidate:display.getSupportedModes()) {
                    if(candidate.getPhysicalWidth()==active.getPhysicalWidth() &&
                       candidate.getPhysicalHeight()==active.getPhysicalHeight()) {
                        float hz=candidate.getRefreshRate();
                        if(hz<=121.0f)maximum=Math.max(maximum,hz);
                    }
                }
            }
        } catch(RuntimeException ignored) {}
        if(maximum<1)maximum=display.getRefreshRate();
        return Math.max(30f,Math.min(120f,maximum));
    }

    public static boolean supports120(Context ctx) {
        return maximumHz(ctx)>=110f;
    }

    public static float currentHz(Context ctx){
        Display display=display(ctx);
        if(display==null)return 60;
        try{return Math.max(30f,Math.min(120f,display.getRefreshRate()));}
        catch(RuntimeException ignored){return 60;}
    }

    public static float requestedHz(Context ctx) {
        String selected=Config.str(ctx,"frame_rate","auto");
        if(!"auto".equals(selected)&&!"60".equals(selected)&&!"120".equals(selected))
            selected="auto";
        PowerManager power=(PowerManager)ctx.getSystemService(Context.POWER_SERVICE);
        if(power!=null&&power.isPowerSaveMode())return 30f;
        if(Config.bool(ctx,"reduce_motion",false))return 30f;
        if("battery".equals(Config.str(ctx,"render_quality","auto")))return 30f;
        if("120".equals(selected))return supports120(ctx)?maximumHz(ctx):Math.min(60,maximumHz(ctx));
        if("60".equals(selected))return Math.min(60f,maximumHz(ctx));
        if(Config.bool(ctx,"high_fps",false))return Math.min(60f,maximumHz(ctx));
        // Auto follows One UI: do not force a refresh rate the user did not select.
        return Math.min(maximumHz(ctx),currentHz(ctx));
    }

    public static void applyToActivity(Activity activity) {
        try{
            String selected=Config.str(activity,"frame_rate","auto");
            WindowManager.LayoutParams params=activity.getWindow().getAttributes();
            // Auto gives control to the system. Explicit 60/120 is only a preference.
            float wanted="auto".equals(selected)&&
                !Config.bool(activity,"high_fps",false)?0:requestedHz(activity);
            if(params.preferredRefreshRate!=wanted){
                params.preferredDisplayModeId=0;
                params.preferredRefreshRate=wanted;
                activity.getWindow().setAttributes(params);
            }
        }catch(RuntimeException ignored){}
    }

    public static void applyToSurface(SurfaceHolder holder,float fps) {
        if(Build.VERSION.SDK_INT<30||holder==null)return;
        try{
            Surface surface=holder.getSurface();
            if(surface!=null&&surface.isValid())
                surface.setFrameRate(Math.max(0,fps),Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
        }catch(RuntimeException ignored){}
    }
}
