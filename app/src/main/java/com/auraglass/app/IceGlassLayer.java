package com.auraglass.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import java.util.Random;

/**
 * Frosted glass simulation. The pixels represent an icy surface, not an audio/video recording.
 * Low-resolution thermal field: heat diffuses, dissipates, moisture condenses, frost nucleates.
 * Deterministic dendrite skeleton is constructed once; a cached ARGB mask is refreshed at 12 Hz.
 * No full-screen per-frame allocations and no continuous background threads.
 */
public final class IceGlassLayer {
    private static final int WIDTH=160;
    private static final int HEIGHT=320;
    private static final int COUNT=WIDTH*HEIGHT;
    private static final int REBUILD_MS=80;
    private final Paint bitmapPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Rect source=new Rect(0,0,WIDTH,HEIGHT);
    private final RectF destination=new RectF();
    private final float[] heat=new float[COUNT];
    private final float[] heatNext=new float[COUNT];
    private final float[] crystal=new float[COUNT];
    private final float[] meltDepth=new float[COUNT];
    private final float[] grain=new float[COUNT];
    private final long[] touchedAt=new long[COUNT];
    private final int[] pixels=new int[COUNT];
    private final Bitmap mask=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
    private final Random rng=new Random(0xA0F09C3L);
    private final Paint sparklePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private long lastUpdate=0;
    private long lastBitmap=0;
    private long lastTouchedGlobal=0;
    private float lastStrength=-1;
    private float intensity=0.7f;
    private float recovery=1.0f;
    private float touchRadius=0.055f;
    private boolean interaction=true;
    private boolean reducedMotion=false;
    private float previousX=-1,previousY=-1;
    private int lastW,lastH;

    public IceGlassLayer(){
        buildCrystals();
        for(int y=0;y<HEIGHT;y++){
            for(int x=0;x<WIDTH;x++){
                int i=y*WIDTH+x;
                grain[i]=rng.nextFloat();
            }
        }
    }

    public void configure(float intensity,float recovery,float radius,boolean interaction,boolean reduced){
        this.intensity=Math.max(0,Math.min(1,intensity));
        this.recovery=Math.max(.4f,Math.min(2.6f,recovery));
        this.touchRadius=Math.max(.025f,Math.min(.12f,radius));
        this.interaction=interaction;
        this.reducedMotion=reduced;
        lastBitmap=0;
    }

    private void stamp(float x,float y,float value){
        int ix=(int)x,iy=(int)y;
        // Fine needle nuclei, never broad white branches.
        int radius=1;
        for(int yy=Math.max(0,iy-radius);yy<=Math.min(HEIGHT-1,iy+radius);yy++){
            for(int xx=Math.max(0,ix-radius);xx<=Math.min(WIDTH-1,ix+radius);xx++){
                float d=(xx-x)*(xx-x)+(yy-y)*(yy-y);
                float weight=1f-Math.min(1f,(float)Math.sqrt(d)/(radius+1f));
                int i=yy*WIDTH+xx;
                crystal[i]=Math.min(1f,crystal[i]+weight*(value>1.2f?.27f:.13f));
            }
        }
    }

    // Starts from cold corners/edges; a random walk provides side shoots and fractal fern tips.
    private void growBranch(float x,float y,float angle,float step,int count,int level){
        for(int j=0;j<count;j++){
            if(x<0||y<0||x>=WIDTH||y>=HEIGHT)break;
            stamp(x,y,level==0?1.3f:level==1?.95f:.65f);
            float oldX=x,oldY=y;
            angle+=(rng.nextFloat()-.5f)*.20f;
            x+=(float)Math.cos(angle)*step;
            y+=(float)Math.sin(angle)*step;
            drawCrystalLine(oldX,oldY,x,y,level==0?.52f:.31f);
            if(level<2&&j>2&&j%6==0){
                float side=(rng.nextBoolean()?1:-1)*(.64f+rng.nextFloat()*.19f);
                growBranch(x,y,angle+side,step*.67f,
                    Math.max(3,(int)((count-j)*.37f)),level+1);
                if(level==0&&j%8==0)
                    growBranch(x,y,angle-side,step*.71f,
                        Math.max(4,(int)((count-j)*.33f)),level+1);
            }
        }
    }

    private void drawCrystalLine(float x1,float y1,float x2,float y2,float amount){
        int count=Math.max(1,(int)Math.hypot(x2-x1,y2-y1)*2);
        for(int i=0;i<=count;i++){
            float f=i/(float)count;
            int x=Math.round(x1+(x2-x1)*f);
            int y=Math.round(y1+(y2-y1)*f);
            if(x>=0&&x<WIDTH&&y>=0&&y<HEIGHT){
                int index=y*WIDTH+x;
                crystal[index]=Math.min(1f,crystal[index]+amount*.24f);
            }
        }
    }

    private void buildCrystals(){
        for(int edge=0;edge<4;edge++){
            int colonies=edge<2?6:5;
            for(int n=0;n<colonies;n++){
                float lane=(n+rng.nextFloat()*.65f)/colonies;
                float x,y,theta;
                if(edge==0){x=1;y=lane*(HEIGHT-1);theta=0;}
                else if(edge==1){x=WIDTH-2;y=lane*(HEIGHT-1);theta=(float)Math.PI;}
                else if(edge==2){x=lane*(WIDTH-1);y=1;theta=(float)Math.PI/2;}
                else{x=lane*(WIDTH-1);y=HEIGHT-2;theta=-(float)Math.PI/2;}
                theta+=(rng.nextFloat()-.5f)*.68f;
                growBranch(x,y,theta,1.7f+rng.nextFloat()*.75f,19+rng.nextInt(18),0);
            }
        }
    }

    public void startStroke(float x,float y,int width,int height,long now){
        if(!interaction)return;
        previousX=-1;previousY=-1;
        stroke(x,y,width,height,now);
    }

    public void stroke(float x,float y,int width,int height,long now){
        if(!interaction||width<=0||height<=0)return;
        lastW=width;lastH=height;
        lastTouchedGlobal=now;
        float nx=Math.max(0,Math.min(1,x/width));
        float ny=Math.max(0,Math.min(1,y/height));
        float px=nx*WIDTH,py=ny*HEIGHT;
        if(previousX>=0){
            float distance=(float)Math.hypot(px-previousX,py-previousY);
            int steps=Math.min(45,Math.max(1,(int)(distance/1.8f)));
            for(int s=0;s<steps;s++){
                float f=s/(float)steps;
                melt(previousX+(px-previousX)*f,previousY+(py-previousY)*f,width,height,now);
            }
        }
        melt(px,py,width,height,now);
        previousX=px;previousY=py;
        lastBitmap=0;
    }

    public void endStroke(){previousX=-1;previousY=-1;}

    private void melt(float x,float y,int width,int height,long now){
        // Circular contact radius in *screen pixels*, not elliptical normalized coordinates.
        float radiusPx=Math.min(width,height)*touchRadius;
        float rx=Math.max(1,radiusPx*WIDTH/width);
        float ry=Math.max(1,radiusPx*HEIGHT/height);
        int left=Math.max(0,(int)(x-rx*1.25f)),right=Math.min(WIDTH-1,(int)(x+rx*1.25f));
        int top=Math.max(0,(int)(y-ry*1.25f)),bottom=Math.min(HEIGHT-1,(int)(y+ry*1.25f));
        for(int yy=top;yy<=bottom;yy++){
            float dy=(yy-y)/ry;
            for(int xx=left;xx<=right;xx++){
                float dx=(xx-x)/rx;
                float d=(float)Math.sqrt(dx*dx+dy*dy);
                if(d>1.16f)continue;
                int i=yy*WIDTH+xx;
                float deposit=Math.max(0,1-d/1.16f);
                heat[i]=Math.max(heat[i],Math.min(1f,.37f+deposit*.87f));
                if(d<.92f){
                    touchedAt[i]=now;
                    meltDepth[i]=Math.max(.1f,1-d*.8f);
                }
            }
        }
    }

    private static float fade(float a,float b,float x){
        float f=Math.max(0,Math.min(1,(x-a)/(b-a)));
        return f*f*(3-2*f);
    }

    private void thermalStep(long now){
        if(lastUpdate==0){lastUpdate=now;return;}
        float dt=Math.max(0,Math.min(100,now-lastUpdate));
        if(dt<30)return;
        lastUpdate=now;
        float diffusion=.10f*(dt/60f);
        float cooling=(float)Math.exp(-dt/(1500f*recovery));
        for(int y=0;y<HEIGHT;y++){
            int row=y*WIDTH;
            for(int x=0;x<WIDTH;x++){
                int i=row+x;
                float v=heat[i];
                float left=heat[x>0?i-1:i],right=heat[x<WIDTH-1?i+1:i];
                float up=heat[y>0?i-WIDTH:i],down=heat[y<HEIGHT-1?i+WIDTH:i];
                heatNext[i]=Math.max(0,(v+diffusion*(left+right+up+down-4*v))*cooling);
            }
        }
        System.arraycopy(heatNext,0,heat,0,COUNT);
    }

    private void rebuild(long now,float sceneStrength){
        float strength=intensity*sceneStrength;
        for(int y=0;y<HEIGHT;y++){
            float fy=y/(float)(HEIGHT-1);
            for(int x=0;x<WIDTH;x++){
                int i=y*WIDTH+x;
                float fx=x/(float)(WIDTH-1);
                float edge=Math.max(0,1-Math.min(Math.min(fx,1-fx)*4.4f,
                    Math.min(fy,1-fy)*2.9f));
                edge=edge*edge;
                float baseMist=.075f+edge*.13f;
                float dendrite=crystal[i]*(.50f+.20f*edge);
                float age=touchedAt[i]==0?1000:(now-touchedAt[i])/(1000f*recovery);
                // Recondensation after 1.3 s, soft droplets followed by crystallization at 3–7 s.
                float moisture=fade(1.25f,3.2f,age);
                // Deeper melted centers recrystallize later than the cooler rim.
                float freezeStart=2.2f+1.9f*meltDepth[i];
                float frozen=fade(freezeStart,freezeStart+3.9f,age);
                float heatClear=Math.max(0,1-heat[i]*.94f);
                float mist=baseMist*moisture;
                float ice=dendrite*frozen;
                float sparkle=grain[i]>.982f?crystal[i]*.12f*frozen:0;
                float opacity=(mist+ice+sparkle)*heatClear*strength;
                if(age<1.15f)opacity*=.04f+.96f*fade(.26f,1.15f,age);
                opacity=Math.max(0,Math.min(.55f,opacity));
                int alpha=(int)(opacity*255);
                // Cooler crystal highlights; subtle translucent condensation grains.
                int rgb=crystal[i]>.42f?0xffe5f9ff:0xffb8d6e7;
                int vary=(int)((grain[i]-.5f)*12);
                int r=Math.max(0,Math.min(255,((rgb>>16)&255)+vary));
                int g=Math.max(0,Math.min(255,((rgb>>8)&255)+vary));
                int b=Math.max(0,Math.min(255,(rgb&255)+vary));
                pixels[i]=(alpha<<24)|(r<<16)|(g<<8)|b;
            }
        }
        mask.setPixels(pixels,0,WIDTH,0,0,WIDTH,HEIGHT);
        lastBitmap=now;
        lastStrength=sceneStrength;
    }

    public void draw(Canvas canvas,int width,int height,long now,float sceneStrength){
        if(sceneStrength<=.002f||intensity<=0||width<=0||height<=0)return;
        boolean activeCooling=lastTouchedGlobal>0&&now-lastTouchedGlobal<11500;
        if(activeCooling)thermalStep(now);
        else lastUpdate=now;
        // Stable frost is cached. Only a finger trail or a weather crossfade
        // should wake the expensive pixel field; sky/star animation is separate.
        if(lastBitmap==0||Math.abs(sceneStrength-lastStrength)>.012f||
            activeCooling&&now-lastBitmap>=REBUILD_MS)
            rebuild(now,sceneStrength);
        destination.set(0,0,width,height);
        bitmapPaint.setAlpha(255);
        canvas.drawBitmap(mask,source,destination,bitmapPaint);
        // Glints on nucleation tips stay small and do not flash or allocate on every frame.
        if(!reducedMotion&&sceneStrength>.2f){
            sparklePaint.setStrokeWidth(Math.max(1,width*.0012f));
            sparklePaint.setStrokeCap(Paint.Cap.ROUND);
            for(int k=0;k<9;k++){
                float x=((k*47+23)%157)/160f*width;
                float y=((k*79+11)%311)/320f*height;
                int index=Math.min(COUNT-1,
                    (int)(y/height*(HEIGHT-1))*WIDTH+(int)(x/width*(WIDTH-1)));
                if(crystal[index]<.4f||heat[index]>.18f)continue;
                float pulse=.52f+.48f*(float)Math.sin(now*.0005+k*2.41f);
                sparklePaint.setColor(((int)(28*pulse*intensity*sceneStrength)<<24)|0x00ebfbff);
                float length=Math.min(width,height)*.0045f;
                canvas.drawLine(x-length,y,x+length,y,sparklePaint);
                canvas.drawLine(x,y-length,x,y+length,sparklePaint);
            }
        }
    }
}
