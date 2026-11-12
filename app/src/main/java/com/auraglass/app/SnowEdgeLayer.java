package com.auraglass.app;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.SystemClock;

/** A subtle accumulating snow shelf along glass-card tops, locally melted by touch. */
public final class SnowEdgeLayer {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final float[] nodes=new float[26];
    private long startedAt=SystemClock.uptimeMillis();
    // Retain a short trail so a swipe melts along its entire path, not only its last point.
    private static final int TOUCH_SAMPLES=32;
    private final float[] touchXs=new float[TOUCH_SAMPLES];
    private final long[] touchTimes=new long[TOUCH_SAMPLES];
    private int nextTouch=0;

    public SnowEdgeLayer(){
        java.util.Random random=new java.util.Random(4109L);
        for(int i=0;i<nodes.length;i++)
            nodes[i]=.67f+random.nextFloat()*.46f;
    }
    public void touch(float x,long now){
        touchXs[nextTouch]=x;
        touchTimes[nextTouch]=now;
        nextTouch=(nextTouch+1)%TOUCH_SAMPLES;
    }
    public void draw(Canvas canvas,float width,float height,long now,float strength){
        if(width<30||height<20||strength<=.005f)return;
        float grown=Math.min(1f,Math.max(.05f,(now-startedAt)/40000f));
        float amount=Math.max(0,Math.min(1,strength))*grown;
        float maxDepth=Math.min(height*.085f,width*.033f)*amount;
        if(maxDepth<.35f)return;
        path.reset();
        path.moveTo(0,0);
        for(int i=0;i<nodes.length;i++){
            float x=i*width/(nodes.length-1);
            float melt=0f;
            float radius=Math.max(12f,width*.12f);
            for(int sample=0;sample<TOUCH_SAMPLES;sample++){
                long touched=touchTimes[sample];
                if(touched==0||now<touched)continue;
                float age=(now-touched)/1000f;
                if(age>=7f)continue;
                float recover=Math.max(0f,Math.min(1f,(age-.5f)/6.5f));
                recover=recover*recover*(3f-2f*recover);
                float dx=(x-touchXs[sample])/radius;
                float local=(float)Math.exp(-dx*dx)*(1f-recover);
                melt=Math.max(melt,local);
            }
            float y=Math.max(.25f,maxDepth*nodes[i]*(1-melt*.96f));
            path.lineTo(x,y);
        }
        path.lineTo(width,0);
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0,0,0,Math.max(1,maxDepth*2.1f),
            new int[]{0xbef4fbff,0x93d6eeff,0x00b3d9f6},null,Shader.TileMode.CLAMP));
        canvas.drawPath(path,p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(.5f,width*.0012f));
        p.setColor((Math.min(205,(int)(160*amount))<<24)|0x00f6fdff);
        canvas.drawPath(path,p);
        p.setStyle(Paint.Style.FILL);
    }
}
