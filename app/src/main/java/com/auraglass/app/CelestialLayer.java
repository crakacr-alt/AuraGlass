package com.auraglass.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.SparseArray;
import java.util.Random;

/**
 * Deterministic night-sky detail drawn over the atmospheric sky, behind mountains / frost.
 * Comets are deliberate time-lapse art, not a claim of literal local astronomy.
 */
public final class CelestialLayer {
    private final float[] sx=new float[205],sy=new float[205],radius=new float[205],twinkle=new float[205];
    private final float[] dustX=new float[180],dustY=new float[180],dustSize=new float[180];
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trail=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cometPath=new Path();
    // The same few halo colors recur thousands of times per minute.
    // Normalized 1-pixel shaders are scaled in Canvas space instead of
    // allocating new RadialGradient objects for every 120 Hz frame.
    private final SparseArray<Shader> glowShaders=new SparseArray<>();

    public CelestialLayer(){
        Random rng=new Random(0x61A72C0L);
        for(int i=0;i<sx.length;i++){
            sx[i]=rng.nextFloat();
            sy[i]=rng.nextFloat()*.84f;
            radius[i]=.32f+rng.nextFloat()*1.75f;
            twinkle[i]=rng.nextFloat()*6.28f;
        }
        // A tilted cloudy band with denser stars, not a single sharp geometric galaxy shape.
        for(int i=0;i<dustX.length;i++){
            float x=rng.nextFloat();
            float y=.42f-.18f*x+(float)(rng.nextGaussian()*.071f);
            dustX[i]=x;
            dustY[i]=Math.max(0,Math.min(.84f,y));
            dustSize[i]=.33f+rng.nextFloat()*1.16f;
        }
    }

    private static float clamp(float x){return Math.max(0,Math.min(1,x));}
    private static int tint(int color,float alpha){
        return (color&0x00ffffff)|(((int)(clamp(alpha)*255))<<24);
    }
    private void glow(Canvas canvas,float x,float y,float size,int rgb,float alpha){
        if(size<1||alpha<=.002f)return;
        int color=rgb&0x00ffffff;
        Shader halo=glowShaders.get(color);
        if(halo==null){
            halo=new RadialGradient(0,0,1f,
                new int[]{0xff000000|color,color},null,Shader.TileMode.CLAMP);
            glowShaders.put(color,halo);
        }
        canvas.save();
        canvas.translate(x,y);
        canvas.scale(size,size);
        paint.setShader(halo);
        paint.setAlpha((int)(clamp(alpha)*255));
        canvas.drawCircle(0,0,1f,paint);
        paint.setShader(null);
        paint.setAlpha(255);
        canvas.restore();
    }

    public void draw(Canvas c,float width,float height,float seconds,float night,
                     boolean stars,boolean galaxy,boolean comets,boolean reduced,float quality){
        if(night<.005f)return;
        float w=width,h=height;
        float clock=reduced?0:seconds;
        if(galaxy){
            // Dusty, feathered milky-way band with a warm center and cool edges.
            for(int k=0;k<5;k++){
                float cx=w*(.19f+k*.155f);
                float cy=h*(.42f-k*.028f);
                glow(c,cx,cy,w*(.26f+.07f*(k%2)),
                    k%2==0?0xffa1a8ed:0xffdfafdd,night*.039f);
            }
            glow(c,w*.48f,h*.30f,w*.40f,0xff6677d9,night*.046f);
            for(int i=0;i<(int)(dustX.length*Math.max(.30f,quality));i++){
                float pulse=reduced?1:.83f+.17f*(float)Math.sin(clock*.11f+twinkle[i%sx.length]);
                paint.setColor(tint(i%5==0?0xfff9deef:0xffc9d5fa,night*pulse*(.23f+(i%3)*.09f)));
                c.drawCircle(dustX[i]*w,dustY[i]*h,w*.00054f*dustSize[i]+.35f,paint);
            }
        }
        if(stars){
            for(int i=0;i<(int)(sx.length*Math.max(.35f,quality));i++){
                float flicker=reduced?.82f:.72f+.28f*(float)Math.sin(clock*(.46f+(i%9)*.09f)+twinkle[i]);
                float visibility=night*(.27f+.73f*flicker);
                float x=sx[i]*w,y=sy[i]*h;
                paint.setColor(tint(i%7==0?0xffc6efff:0xffedf5ff,visibility*(i%9==0?.95f:.56f)));
                float r=Math.max(.58f,w*.0013f*radius[i]);
                c.drawCircle(x,y,r,paint);
                if(i%29==0){
                    glow(c,x,y,Math.max(3,w*.012f*radius[i]),0xffe6f5ff,visibility*.25f);
                    paint.setColor(tint(0xffeaf5ff,visibility*.32f));
                    paint.setStrokeWidth(Math.max(.5f,w*.00065f));
                    c.drawLine(x-r*3,y,x+r*3,y,paint);
                    c.drawLine(x,y-r*3,x,y+r*3,paint);
                }
            }
        }
        if(comets&&!reduced){
            drawComet(c,w,h,clock,night,77,1.9f,0xfff9e5fb);
            drawComet(c,w,h,clock+27.8f,night,109,2.9f,0xffd4f9f2);
        }
    }

    private void drawComet(Canvas c,float w,float h,float time,float night,
                           float every,float duration,int rgb){
        float phase=(time%every+every)%every;
        if(phase>=duration)return;
        float x=phase/duration;
        float fade=(float)Math.sin(Math.PI*x);
        float opacity=night*fade;
        float px=w*(.91f-.86f*x),py=h*(.08f+.29f*x);
        float vx=w*.23f,vy=-h*.14f;
        float tail=1.0f+1.5f*x;
        float fromX=px+vx*tail,fromY=py+vy*tail;
        trail.setStyle(Paint.Style.STROKE);
        trail.setStrokeCap(Paint.Cap.ROUND);
        trail.setStrokeWidth(Math.max(1.1f,w*.0021f));
        trail.setShader(new LinearGradient(fromX,fromY,px,py,
            new int[]{tint(rgb,0),tint(rgb,opacity*.18f),tint(0xfff9ffff,opacity*.9f)},
            new float[]{0,.64f,1},Shader.TileMode.CLAMP));
        c.drawLine(fromX,fromY,px,py,trail);
        trail.setShader(null);trail.setStyle(Paint.Style.FILL);
        glow(c,px,py,w*.026f,rgb,opacity*.38f);
        paint.setColor(tint(0xfffaffff,opacity));
        c.drawCircle(px,py,Math.max(.8f,w*.0023f),paint);
    }
}
