package com.auraglass.app;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * Approximate lunar illumination based on a known new-moon epoch.
 * Artistic shading; not an ephemeris or a statement about local moon visibility.
 */
public final class MoonPhase {
    private static final double SYNODIC_DAYS=29.530588853;
    private static final long NEW_MOON_EPOCH=947182440000L; // 2000-01-06 18:14 UTC
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);

    public static double phase(long utcMillis){
        double days=(utcMillis-NEW_MOON_EPOCH)/86400000.0;
        double result=(days/SYNODIC_DAYS)%1.0;
        return result<0?result+1:result;
    }

    public void draw(Canvas canvas,float x,float y,float radius,float night,long utcMillis){
        if(radius<=0||night<=0)return;
        double phase=phase(utcMillis);
        // phase=0 new, .25 first quarter, .5 full, .75 last quarter.
        float illumination=(float)(.5*(1-Math.cos(phase*2*Math.PI)));
        boolean waxing=phase<.5;
        paint.setShader(new RadialGradient(x-radius*.26f,y-radius*.22f,radius*1.28f,
            new int[]{0xff536586,0xff283653,0xff111e39},null,Shader.TileMode.CLAMP));
        paint.setAlpha((int)(190*night));
        canvas.drawCircle(x,y,radius,paint);
        paint.setShader(null);
        // Draw the illuminated lunar segment with smooth analytical limb geometry.
        // At each Y, limb X varies as an ellipse; the terminator is another ellipse.
        int slices=72;
        float step=radius*2/slices;
        float terminator=(float)Math.cos(phase*2*Math.PI);
        paint.setColor(0xffe6f0ff);
        paint.setAlpha((int)(224*night));
        paint.setStrokeWidth(step*1.5f);
        for(int i=0;i<slices;i++){
            float yy=-radius+(i+.5f)*step;
            float edge=(float)Math.sqrt(Math.max(0,radius*radius-yy*yy));
            float boundary=edge*terminator;
            float left,right;
            if(waxing){
                if(phase<.25){left=boundary;right=edge;}
                else{left=boundary;right=edge;}
            }else{
                left=-edge;right=-boundary;
            }
            // phase 0 -> no lit area; phase .5 -> entire lit disk.
            // terminator changes sign at quarter/full; waxing right, waning left.
            if(right>left){
                float shade=.88f+.12f*(1-Math.abs(yy)/radius);
                paint.setAlpha((int)(210*night*shade));
                canvas.drawLine(x+left,y+yy,x+right,y+yy,paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
    }
}
