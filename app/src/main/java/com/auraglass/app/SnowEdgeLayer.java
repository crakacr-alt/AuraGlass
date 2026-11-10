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
    private long lastTouch=0;
    private float touchX=-10000f;

    public SnowEdgeLayer(){
        java.util.Random random=new java.util.Random(4109L);
        for(int i=0;i<nodes.length;i++)
            nodes[i]=.67f+random.nextFloat()*.46f;
    }
    public void touch(float x,long now){
        touchX=x;lastTouch=now;
    }
    public void draw(Canvas canvas,float width,float height,long now,float strength){
        if(width<30||height<20||strength<=.005f)return;
        float grown=Math.min(1f,Math.max(.05f,(now-startedAt)/40000f));
        float amount=Math.max(0,Math.min(1,strength))*grown;
        float maxDepth=Math.min(height*.085f,width*.033f)*amount;
        if(maxDepth<.35f)return;
        float touchAge=lastTouch==0?100f:(now-lastTouch)/1000f;
        float recover=Math.max(0,Math.min(1,(touchAge-.5f)/6.5f));
        recover=recover*recover*(3-2*recover);
        path.reset();
        path.moveTo(0,0);
        for(int i=0;i<nodes.length;i++){
            float x=i*width/(nodes.length-1);
            float near=lastTouch==0?0:(float)Math.exp(-Math.pow((x-touchX)/Math.max(12,width*.12f),2));
            float melt=near*(1-recover);
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
