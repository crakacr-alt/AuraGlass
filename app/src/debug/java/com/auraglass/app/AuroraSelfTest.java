package com.auraglass.app;

/** Debug-only numerical checks for seam-free, time-continuous auroral emission. */
public final class AuroraSelfTest {
    private AuroraSelfTest(){}
    private static long difference(int[] a,int[] b){
        long delta=0L;
        for(int i=0;i<a.length;i++){
            delta+=Math.abs((a[i]>>>24)-(b[i]>>>24));
        }
        return delta;
    }
    public static String run(){
        final int w=192,h=128;
        AuroraField field=new AuroraField(w,h);
        field.render(0f);
        int[] start=field.pixels().clone();
        field.render(0f);
        boolean deterministic=java.util.Arrays.equals(start,field.pixels());
        field.render(.5f);
        int[] shortFrame=field.pixels().clone();
        field.render(6f);
        int[] longFrame=field.pixels();
        int luminous=0,maxJump=0;
        for(int y=0;y<h;y++){
            for(int x=0;x<w;x++){
                int a=start[y*w+x]>>>24;
                if(a>4)luminous++;
                if(x>0)maxJump=Math.max(maxJump,
                    Math.abs(a-(start[y*w+x-1]>>>24)));
                if(y>0)maxJump=Math.max(maxJump,
                    Math.abs(a-(start[(y-1)*w+x]>>>24)));
            }
        }
        double shortDelta=difference(start,shortFrame)/(double)(w*h);
        double longDelta=difference(start,longFrame)/(double)(w*h);
        boolean smooth=maxJump<65;
        boolean evolving=shortDelta>.08&&longDelta>shortDelta*1.15;
        boolean filled=luminous>w*h/8;
        boolean okay=deterministic&&smooth&&evolving&&filled;
        return String.format(java.util.Locale.ROOT,
            "%s coverage=%d/%d maxJump=%d delta0.5=%.3f delta6=%.3f deterministic=%s",
            okay?"AURORA_FIELD_OK":"AURORA_FIELD_FAILED",
            luminous,w*h,maxJump,shortDelta,longDelta,deterministic);
    }
}
