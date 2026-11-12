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
    private static final int WIDTH=240;
    private static final int HEIGHT=512;
    private static final int COUNT=WIDTH*HEIGHT;
    private static final int REBUILD_MS=80;
    private final Paint bitmapPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Rect source=new Rect(0,0,WIDTH,HEIGHT);
    private final RectF destination=new RectF();
    private final float[] heat=new float[COUNT];
    private final float[] heatNext=new float[COUNT];
    private final float[] crystal=new float[COUNT];
    // One-time subpixel coverage filter makes diagonal dendrites continuous
    // instead of leaving pixel-spaced gaps when the mask is enlarged.
    private final float[] crystalCoverage=new float[COUNT];
    private final float[] meltDepth=new float[COUNT];
    private final float[] grain=new float[COUNT];
    private final long[] touchedAt=new long[COUNT];
    private final int[] pixels=new int[COUNT];
    private final int[] dewPixels=new int[COUNT];
    private final Bitmap mask=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
    private final Bitmap dewMask=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
    private boolean hasDewSheen=false;
    private boolean wasCooling=false;
    private final Random rng=new Random(0xA0F09C3L);
    private final Paint sparklePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private long lastUpdate=0;
    private long lastBitmap=0;
    private long lastTouchedGlobal=0;
    private float lastStrength=-1;
    private float intensity=0.7f;
    private float recovery=1.0f;
    private float touchRadius=0.035f;
    private boolean interaction=true;
    private boolean reducedMotion=false;
    private float previousX=-1,previousY=-1;
    private int lastW,lastH;

    public IceGlassLayer(){
        buildCrystals();
        prepareCrystalCoverage();
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
        this.touchRadius=Math.max(.018f,Math.min(.085f,radius));
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
                growBranch(x,y,theta,1.6f+rng.nextFloat()*.7f,21+rng.nextInt(17),0);
            }
        }
    }

    private void prepareCrystalCoverage(){
        // Static anti-aliasing: build once. Touch/refreeze changes only opacity.
        // Along diagonals, 5-sample weighted coverage connects fine fern needles
        // without thickening them into opaque white stripes.
        for(int y=0;y<HEIGHT;y++){
            int row=y*WIDTH;
            for(int x=0;x<WIDTH;x++){
                int i=row+x;
                float center=crystal[i];
                float horizontal=crystal[x>0?i-1:i]+crystal[x<WIDTH-1?i+1:i];
                float vertical=crystal[y>0?i-WIDTH:i]+crystal[y<HEIGHT-1?i+WIDTH:i];
                crystalCoverage[i]=Math.min(1f,center*.62f+
                    horizontal*.095f+vertical*.095f);
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
            // Preserve coverage even for fast long swipes across the display.
            // The old 45-stamp cap could leave visible gaps on tall screens.
            int steps=Math.min(320,Math.max(1,(int)Math.ceil(distance/1.8f)));
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
                    float contact=Math.max(0,1-d/.93f);
                    meltDepth[i]=contact*contact;
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
        // The film updates at ~12 Hz; a faster thermal solver wastes CPU
        // while its visual alpha mask stays unchanged.
        if(dt<REBUILD_MS)return;
        lastUpdate=now;
        // Explicit 2-D heat equation: alpha*dt/dx^2 must stay <= 1/4
        // for the five-point stencil to remain stable and non-oscillatory.
        float diffusion=Math.min(.24f,.10f*(dt/60f));
        float cooling=(float)Math.exp(-dt/(1500f*recovery));
        for(int y=0;y<HEIGHT;y++){
            int row=y*WIDTH;
            for(int x=0;x<WIDTH;x++){
                int i=row+x;
                float v=heat[i];
                float left=heat[x>0?i-1:i],right=heat[x<WIDTH-1?i+1:i];
                float up=heat[y>0?i-WIDTH:i],down=heat[y<HEIGHT-1?i+WIDTH:i];
                heatNext[i]=Math.max(0,Math.min(1f,
                    (v+diffusion*(left+right+up+down-4f*v))*cooling));
            }
        }
        System.arraycopy(heatNext,0,heat,0,COUNT);
    }

    private void rebuild(long now,float sceneStrength){
        float strength=intensity*sceneStrength;
        boolean warmVisible=false;
        boolean coolingActive=lastTouchedGlobal>0&&now-lastTouchedGlobal<11500;
        for(int y=0;y<HEIGHT;y++){
            float fy=y/(float)(HEIGHT-1);
            for(int x=0;x<WIDTH;x++){
                int i=y*WIDTH+x;
                float fx=x/(float)(WIDTH-1);
                float edge=Math.max(0,1-Math.min(Math.min(fx,1-fx)*4.4f,
                    Math.min(fy,1-fy)*2.9f));
                edge=edge*edge;
                float baseMist=.075f+edge*.13f;
                float vein=crystalCoverage[i];
                float dendrite=(crystal[i]*.65f+vein*.35f)*(.50f+.20f*edge);
                float age=touchedAt[i]==0?1000:(now-touchedAt[i])/(1000f*recovery);
                // Recondensation after 1.3 s, soft droplets followed by crystallization at 3–7 s.
                float moisture=fade(1.25f,3.2f,age);
                // Deeper melted centers recrystallize later than the cooler rim.
                float freezeStart=2.2f+1.9f*meltDepth[i];
                float frozen=fade(freezeStart,freezeStart+3.9f,age);
                float warmth=heat[i];
                // Finger heat melts crystalline lattice first; a little moist
                // condensation remains instead of a solid black marker stroke.
                // Never erase the entire glass film into a black-looking stroke.
                // A thin wet haze stays visible while the frozen lattice melts.
                float mist=baseMist*(.50f+.50f*moisture)*(1f-.15f*warmth);
                float ice=dendrite*(.05f+.95f*frozen)*(1f-.80f*warmth);
                // Microfacet lighting is based on the local slope of the
                // smoothed frost ridge. It stays attached to each crystal
                // through melting and regrowth; no artificial random flashing.
                float leftVein=crystalCoverage[x>0?i-1:i];
                float rightVein=crystalCoverage[x<WIDTH-1?i+1:i];
                float topVein=crystalCoverage[y>0?i-WIDTH:i];
                float bottomVein=crystalCoverage[y<HEIGHT-1?i+WIDTH:i];
                // Height-field normals approximate the local crystal slope.
                // A normalized half-vector yields view-dependent specular
                // facets instead of uniformly bright, painted white branches.
                float nx=(leftVein-rightVein)*.85f;
                float ny=(topVein-bottomVein)*.85f;
                float nz=1f/(float)Math.sqrt(1f+nx*nx+ny*ny);
                nx*=nz; ny*=nz;
                float ndoth=Math.max(0f,nx*.33f+ny*(-.24f)+nz*.91f);
                // x^36 using multiplies avoids 120k Math.pow calls per mask rebuild.
                float s2=ndoth*ndoth;
                float s4=s2*s2;
                float s8=s4*s4;
                float s16=s8*s8;
                float specular=s16*s16*s4;
                // Schlick Fresnel for air (n=1) to ice (n~1.31).
                float f0=.018009f;
                float oneMinusCos=1f-Math.max(0f,nz);
                float om2=oneMinusCos*oneMinusCos;
                float fresnel=f0+(1f-f0)*om2*om2*oneMinusCos;
                float facet=Math.min(.16f,(specular*.10f+fresnel*.18f)*vein);
                float sparkle=(grain[i]>.982f?vein*.12f*frozen:0)*(1f-warmth);
                float crystalLight=facet*frozen*(1f-.85f*warmth);
                // The liquid boundary behaves like a curved meniscus: its
                // slope produces a restrained highlight around a warm stroke.
                // The rim follows the thermal gradient, not a fixed touch circle.
                float hLeft=heat[x>0?i-1:i];
                float hRight=heat[x<WIDTH-1?i+1:i];
                float hTop=heat[y>0?i-WIDTH:i];
                float hBottom=heat[y<HEIGHT-1?i+WIDTH:i];
                float rimSlope=(float)Math.sqrt(
                    (hRight-hLeft)*(hRight-hLeft)+
                    (hBottom-hTop)*(hBottom-hTop));
                float liquidRim=Math.min(.11f,rimSlope*.75f)*
                    (1f-fade(.5f,1f,warmth));
                float opacity=(mist+ice+sparkle+crystalLight+liquidRim)*strength;
                // Soft contact-pressure falloff: no hard-edged black marker stroke.
                if(age<1.3f){
                    float warmOpacity=(1f-fade(.12f,1.3f,age))*meltDepth[i];
                    opacity*=1f-.25f*warmOpacity;
                }
                opacity=Math.max(0,Math.min(.55f,opacity));
                int alpha=(int)(opacity*255);
                // Cooler crystal highlights; subtle translucent condensation grains.
                int rgb=vein>.31f||crystalLight>.03f||liquidRim>.025f?
                    0xffe5f9ff:0xffb8d6e7;
                int vary=(int)((grain[i]-.5f)*12);
                int r=Math.max(0,Math.min(255,((rgb>>16)&255)+vary));
                int g=Math.max(0,Math.min(255,((rgb>>8)&255)+vary));
                int b=Math.max(0,Math.min(255,(rgb&255)+vary));
                pixels[i]=(alpha<<24)|(r<<16)|(g<<8)|b;
                // Cold glass loses its cloudy frost under a finger, exposing the
                // dark sky. A very faint translucent liquid-film highlight makes
                // the trail look like warmed wet glass, not a black painted mark.
                // The extra layer exists only during cooling, not on idle frames.
                int dew=(int)(Math.min(.075f,
                    .063f*warmth*(.8f+.2f*meltDepth[i]))*255f*strength);
                if(!coolingActive)dew=0;
                if(dew>0)warmVisible=true;
                dewPixels[i]=(dew<<24)|0x00c7edff;
            }
        }
        hasDewSheen=warmVisible;
        mask.setPixels(pixels,0,WIDTH,0,0,WIDTH,HEIGHT);
        if(warmVisible)dewMask.setPixels(dewPixels,0,WIDTH,0,0,WIDTH,HEIGHT);
        lastBitmap=now;
        lastStrength=sceneStrength;
    }

    public void draw(Canvas canvas,int width,int height,long now,float sceneStrength){
        draw(canvas,width,height,now,sceneStrength,1f);
    }
    public void draw(Canvas canvas,int width,int height,long now,float sceneStrength,
                     float renderQuality){
        if(sceneStrength<=.002f||intensity<=0||width<=0||height<=0)return;
        boolean activeCooling=lastTouchedGlobal>0&&now-lastTouchedGlobal<11500;
        boolean coolingEnded=wasCooling&&!activeCooling;
        wasCooling=activeCooling;
        if(activeCooling)thermalStep(now);
        else lastUpdate=now;
        // Stable frost is cached. Only a finger trail or a weather crossfade
        // should wake the expensive pixel field; sky/star animation is separate.
        if(lastBitmap==0||coolingEnded||Math.abs(sceneStrength-lastStrength)>.012f||
            activeCooling&&now-lastBitmap>=REBUILD_MS)
            rebuild(now,sceneStrength);
        destination.set(0,0,width,height);
        bitmapPaint.setAlpha(255);
        // Thin offset highlight imitates refracted light at micro-crystal edges,
        // without claiming real screen-space refraction on a software Canvas.
        if(renderQuality>=.72f){
            bitmapPaint.setAlpha(Math.min(50,(int)(25*sceneStrength)));
            destination.offset(Math.max(1f,width*.0008f),Math.max(1f,height*.0005f));
            canvas.drawBitmap(mask,source,destination,bitmapPaint);
            destination.offset(-Math.max(1f,width*.0008f),-Math.max(1f,height*.0005f));
        }
        bitmapPaint.setAlpha(255);
        canvas.drawBitmap(mask,source,destination,bitmapPaint);
        if(hasDewSheen)
            canvas.drawBitmap(dewMask,source,destination,bitmapPaint);
        // Glints on nucleation tips stay small and do not flash or allocate on every frame.
        if(!reducedMotion&&sceneStrength>.2f){
            sparklePaint.setStrokeWidth(Math.max(1,width*.0012f));
            sparklePaint.setStrokeCap(Paint.Cap.ROUND);
            for(int k=0;k<9;k++){
                float x=((k*47+23)%233)/240f*width;
                float y=((k*79+11)%503)/512f*height;
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
