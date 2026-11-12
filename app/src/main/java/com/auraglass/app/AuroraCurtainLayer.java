package com.auraglass.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * Low-resolution procedural light field, bilinearly upscaled over the sky.
 * Rebuild at 12 Hz maximum; every intervening wallpaper/preview frame reuses
 * the same texture. No allocation, trigonometry or raster work at 120 Hz.
 */
public final class AuroraCurtainLayer {
    private static final int TEXTURE_W=288;
    private static final int TEXTURE_H=192;
    private final AuroraField field=new AuroraField(TEXTURE_W,TEXTURE_H);
    private final Bitmap texture=Bitmap.createBitmap(TEXTURE_W,TEXTURE_H,Bitmap.Config.ARGB_8888);
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
    private final Rect src=new Rect(0,0,TEXTURE_W,TEXTURE_H);
    private final RectF dest=new RectF();
    private int lastFrame=Integer.MIN_VALUE;

    public void draw(Canvas canvas,float width,float height,float seconds,
                     float night,float quality,boolean reducedMotion){
        if(width<=0||height<=0||night<=.005f)return;
        // The simulation moves slowly; a 12-Hz update is smoother than a
        // stepping 120-Hz polygon mesh and avoids per-frame CPU heat.
        float time=reducedMotion?0f:seconds;
        int frame=reducedMotion?0:(int)Math.floor(time*(quality<.55f?7f:12f));
        if(frame!=lastFrame){
            field.render(time);
            texture.setPixels(field.pixels(),0,TEXTURE_W,0,0,TEXTURE_W,TEXTURE_H);
            lastFrame=frame;
        }
        paint.setAlpha(Math.min(255,Math.max(0,(int)(night*255f))));
        // Aurora occupies the upper sky, not the entire screen. Its own
        // boundaries are transparent; no hard rectangle is visible.
        dest.set(0,0,width,height*.62f);
        canvas.drawBitmap(texture,src,dest,paint);
    }
}
