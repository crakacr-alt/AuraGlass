package com.auraglass.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;

/** Deterministic debug-only regression test for thermal touch and recrystallization. */
public final class FrostSelfTest {
    private FrostSelfTest(){}
    private static double averageOpacity(Bitmap b,int x,int y,int radius){
        long total=0;
        int n=0;
        for(int yy=y-radius;yy<=y+radius;yy++){
            for(int xx=x-radius;xx<=x+radius;xx++){
                if(xx<0||xx>=b.getWidth()||yy<0||yy>=b.getHeight())continue;
                total+=Color.alpha(b.getPixel(xx,yy));n++;
            }
        }
        return n==0?0:total/(double)n;
    }
    private static void render(IceGlassLayer ice,Canvas c,long millis){
        c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
        ice.draw(c,240,512,millis,1f,.5f);
    }
    public static String run(){
        IceGlassLayer frost=new IceGlassLayer();
        frost.configure(1f,1f,.065f,true,true);
        Bitmap bitmap=Bitmap.createBitmap(240,512,Bitmap.Config.ARGB_8888);
        try{
            Canvas canvas=new Canvas(bitmap);
            final int cx=13,cy=250;
            long t=100000L;
            render(frost,canvas,t);
            double initial=averageOpacity(bitmap,cx,cy,5);
            double distantBefore=averageOpacity(bitmap,120,255,5);

            frost.startStroke(cx,cy,240,512,t+80);
            frost.endStroke();
            render(frost,canvas,t+160);
            double melted=averageOpacity(bitmap,cx,cy,5);
            double distantAfter=averageOpacity(bitmap,120,255,5);

            // Step time as a real renderer would; skipping directly 10 seconds
            // does not simulate accumulated thermal diffusion.
            for(long now=t+240;now<=t+10240;now+=80)
                render(frost,canvas,now);
            double frozen=averageOpacity(bitmap,cx,cy,5);

            boolean clears=initial-melted>2;
            boolean returns=frozen-melted>2;
            boolean local=Math.abs(distantAfter-distantBefore)<3;
            boolean baseline=initial>5;
            return String.format(java.util.Locale.ROOT,
                "%s cold=%.2f warm=%.2f refrozen=%.2f distantDelta=%.2f",
                baseline&&clears&&returns&&local?"ICE_PHYSICS_OK":"ICE_PHYSICS_FAILED",
                initial,melted,frozen,Math.abs(distantAfter-distantBefore));
        }finally{
            bitmap.recycle();
        }
    }
}
