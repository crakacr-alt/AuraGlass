package com.auraglass.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** No fake bass: static fine line without valid captured FFT data. */
public final class AuraWaveView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] values=new float[24];
    private final float[] smoothed=new float[24];
    private boolean actualAudio=false;
    public AuraWaveView(Context context){super(context);}
    public void setBands(float[] levels){
        if(levels==null)return;
        actualAudio=true;
        for(int i=0;i<24;i++){
            values[i]=Math.max(0,Math.min(1,levels[Math.min(i,levels.length-1)]));
        }
        invalidate();
    }
    public void stopAudio(){
        actualAudio=false;
        for(int i=0;i<24;i++)values[i]=0;
        invalidate();
    }
    public boolean isRealAudio(){return actualAudio;}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float width=getWidth(),height=getHeight();
        if(width<=0||height<=0)return;
        float step=width/24f;
        float bar=Math.max(1.25f,step*.46f);
        paint.setShader(new LinearGradient(0,0,0,height,
            new int[]{0xffd6bcff,0xff82f7e3,0xff76b4f3},null,Shader.TileMode.CLAMP));
        for(int i=0;i<24;i++){
            float target=actualAudio?Math.max(.09f,values[i]):.12f;
            smoothed[i]=smoothed[i]*.60f+target*.40f;
            float half=Math.max(1.5f,height*.48f*smoothed[i]);
            float x=step*(i+.5f);
            c.drawRoundRect(new RectF(x-bar/2,height/2-half,x+bar/2,height/2+half),bar/2,bar/2,paint);
        }
        paint.setShader(null);
    }
}
