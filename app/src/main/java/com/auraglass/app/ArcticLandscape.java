package com.auraglass.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * Cinematic arctic landscape for every wallpaper weather mode.
 *
 * Ridges are deterministic fractal terrain, cached in screen coordinates.
 * Layered atmospheric perspective, directional snow facets and a low-contrast
 * glacial foreground replace the old oscillating sinusoidal silhouettes.
 * This is an artistic illustration, not terrain data from the user's location.
 */
public final class ArcticLandscape {
    private static final int SAMPLES=256;
    private static final int LAYERS=4;
    private final Path[] terrain=new Path[LAYERS];
    private final Path[] snowFace=new Path[LAYERS];
    private final float[][] height=new float[LAYERS][SAMPLES+1];
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);
    private final Paint details=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Shader[] night=new Shader[LAYERS];
    private final Shader[] daylight=new Shader[LAYERS];
    private Shader haze,water;
    private int cachedW=-1,cachedH=-1;

    public ArcticLandscape(){
        for(int i=0;i<LAYERS;i++){
            terrain[i]=new Path();
            snowFace[i]=new Path();
        }
    }
    private static float smooth(float t){
        t=Math.max(0f,Math.min(1f,t));
        return t*t*(3f-2f*t);
    }
    private static float hash(int index,int seed){
        int h=index*0x27d4eb2d+seed*0x165667b1;
        h^=h>>>15;
        h*=0x85ebca6b;
        h^=h>>>13;
        return (h&0x7fffffff)/2147483647f;
    }
    private static float noise(float x,int seed){
        int a=(int)Math.floor(x);
        float t=smooth(x-a);
        return hash(a,seed)*(1-t)+hash(a+1,seed)*t;
    }
    private static float ridge(float x,int seed){
        float broad=noise(x*3.6f,seed);
        float peaks=noise(x*9.4f,seed+7);
        float jagged=noise(x*25.4f,seed+13);
        float detail=noise(x*67f,seed+29);
        // Broad glacial massifs with smaller, rock-like angular peaks.
        return .14f+.44f*broad+.28f*peaks+.12f*jagged+.045f*detail;
    }
    private void ensure(int w,int h){
        if(w==cachedW&&h==cachedH)return;
        cachedW=w;cachedH=h;
        float[] base={.715f,.778f,.846f,.944f};
        float[] relief={.205f,.155f,.122f,.102f};
        int[] nightTop={0xff344d66,0xff23384f,0xff162637,0xff0a1626};
        int[] nightBase={0xff101e32,0xff0c1829,0xff081323,0xff050b16};
        int[] dayTop={0xff9eafbb,0xff7f98a8,0xff536f80,0xff344d5d};
        int[] dayBase={0xff617b8c,0xff435b6f,0xff2c4555,0xff1b3344};
        float full=w*1.18f;
        for(int layer=0;layer<LAYERS;layer++){
            Path p=terrain[layer];
            Path snow=snowFace[layer];
            p.rewind();snow.rewind();
            float x0=-w*.09f;
            for(int i=0;i<=SAMPLES;i++){
                float u=i/(float)SAMPLES;
                float angular=ridge(u,21+layer*19);
                float elevation=base[layer]-relief[layer]*angular;
                // Mountain shoulders taper naturally toward the horizon.
                float y=h*elevation;
                height[layer][i]=y;
                float x=x0+u*full;
                if(i==0)p.moveTo(x,y);
                else p.lineTo(x,y);
            }
            p.lineTo(w*1.09f,h*1.02f);
            p.lineTo(-w*.09f,h*1.02f);
            p.close();
            if(layer<3){
                // Directional snow facets only on the mountain's windward
                // slopes, not white borders around the entire skyline.
                for(int i=5;i<SAMPLES-6;i+=5){
                    float x=x0+full*i/SAMPLES;
                    float y=height[layer][i];
                    float slope=(height[layer][i+4]-height[layer][i-4])/8f;
                    float summit=ridge(i/(float)SAMPLES,21+layer*19);
                    // Snow accumulates on *higher* peaks. Earlier inverted
                    // coverage gave every low shoulder identical diagonal
                    // hatching, which looked like a drawing, not a glacier.
                    float cover=Math.max(0f,(summit-.57f)*2.2f);
                    if(cover<.10f||slope<-.75f)continue;
                    float distance=full*(layer==0?.032f:.021f);
                    float drop=h*(layer==0?.020f:.014f)*(.25f+cover);
                    snow.moveTo(x-full*.004f,y+1);
                    snow.lineTo(x+distance*.55f,y+drop*.6f);
                    snow.lineTo(x+distance,y+drop*1.7f);
                    snow.lineTo(x+full*.008f,y+drop*.21f);
                    snow.close();
                }
            }
            night[layer]=new LinearGradient(0,h*(base[layer]-relief[layer]*.75f),
                0,h,nightTop[layer],nightBase[layer],Shader.TileMode.CLAMP);
            daylight[layer]=new LinearGradient(0,h*(base[layer]-relief[layer]*.75f),
                0,h,dayTop[layer],dayBase[layer],Shader.TileMode.CLAMP);
        }
        haze=new RadialGradient(w*.49f,h*.71f,w*.75f,
            new int[]{0x42678da1,0x18395d77,0x00152331},
            new float[]{0f,.48f,1f},Shader.TileMode.CLAMP);
        water=new LinearGradient(0,h*.87f,0,h,
            new int[]{0x00111c2b,0xff0c1c2e,0xff050d1a},
            new float[]{0f,.50f,1f},Shader.TileMode.CLAMP);
    }

    public void draw(Canvas canvas,int width,int height,float seconds,
                     float day,float twilight,float parallax,float quality){
        if(width<2||height<2)return;
        ensure(width,height);
        float w=width,h=height;
        fill.setStyle(Paint.Style.FILL);
        // The distant horizon gets a thin scattering veil; this is not
        // a hard white rectangle or a stack of pastel circles.
        fill.setShader(haze);
        fill.setAlpha((int)(60+35*Math.max(0f,1-day)));
        canvas.drawRect(0,h*.42f,w,h*.94f,fill);
        fill.setShader(null);
        fill.setAlpha(255);

        for(int layer=0;layer<LAYERS;layer++){
            canvas.save();
            canvas.translate(parallax*w*(.010f+.006f*layer),0);
            fill.setShader(night[layer]);
            fill.setAlpha(255);
            canvas.drawPath(terrain[layer],fill);
            if(day>.003f){
                fill.setShader(daylight[layer]);
                fill.setAlpha((int)(Math.max(0f,Math.min(1f,day))*
                    (layer==0?220:190)));
                canvas.drawPath(terrain[layer],fill);
            }
            fill.setShader(null);
            fill.setAlpha(255);

            if(layer<3){
                float lit=(.40f+.48f*day+.12f*twilight)*(1f-layer*.22f);
                details.setColor(0xffd4e8f1);
                details.setAlpha((int)(Math.min(1f,lit)*(layer==0?72:50)));
                canvas.drawPath(snowFace[layer],details);
                details.setAlpha(255);
            }
            canvas.restore();
        }
        // Foreground polar water: faint scattered reflections with tiny
        // slow-moving highlights. Separate from aurora so no fake mirror bands.
        fill.setShader(water);
        fill.setAlpha(day>.75f?145:245);
        canvas.drawRect(0,h*.87f,w,h,fill);
        fill.setShader(null);
        fill.setAlpha(255);
        if(quality>.4f){
            details.setStrokeWidth(Math.max(.7f,w*.0012f));
            for(int i=0;i<24;i++){
                float y=h*(.903f+i*.0036f);
                float spacing=(float)Math.sin(i*2.79f);
                float shift=(float)Math.sin(seconds*.08f+i*.74f)*w*.018f;
                float center=w*(.47f+spacing*.24f)+shift;
                float length=w*(.021f+(i%7)*.006f);
                details.setColor(i%3==0?0xff85bfd2:0xff637e9e);
                details.setAlpha((int)((1f-day*.55f)*(10+i%5*6)));
                canvas.drawLine(center-length,y,center+length,y,details);
            }
            details.setAlpha(255);
        }
    }
}
