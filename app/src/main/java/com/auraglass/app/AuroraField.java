package com.auraglass.app;

/**
 * Resolution-independent, deterministic approximation of a glowing auroral
 * curtain. Smooth wave-driven folds and a depth-fading emission field replace
 * adjacent polygon strips, so no strip seams or hard vertical boundaries exist.
 *
 * This is atmospheric artwork, not a scientific aurora forecast.
 * The field is independent of Android and can be regression-tested on its own.
 */
public final class AuroraField {
    private static final int LAYERS=3;
    private static final float[] RED={70f,105f,183f};
    private static final float[] GREEN={235f,161f,124f};
    private static final float[] BLUE={189f,251f,251f};
    private static final float[] ENERGY={.64f,.43f,.31f};

    private final int width,height;
    private final float[][] ceiling;
    private final float[][] depth;
    private final float[][] filament;
    private final float[] pulse=new float[LAYERS];
    private final int[] pixels;

    public AuroraField(int width,int height){
        if(width<8||height<8)throw new IllegalArgumentException("Invalid aurora field size");
        this.width=width;this.height=height;
        pixels=new int[width*height];
        ceiling=new float[LAYERS][width];
        depth=new float[LAYERS][width];
        filament=new float[LAYERS][width];
    }

    public int[] pixels(){return pixels;}

    private static float smooth(float a,float b,float value){
        float v=Math.max(0f,Math.min(1f,(value-a)/(b-a)));
        return v*v*(3f-2f*v);
    }

    /**
     * Fixed-size, reusable memory. Cost is O(width*height*3), with sine waves
     * evaluated only once per column/layer (not at every screen pixel).
     */
    public void render(float seconds){
        if(!Float.isFinite(seconds))seconds=0f;
        for(int layer=0;layer<LAYERS;layer++){
            float phase=layer*1.79f;
            pulse[layer]=.82f+.18f*(float)Math.sin(seconds*.17f+phase);
            for(int x=0;x<width;x++){
                float u=(x+.5f)/width;
                float slow=(float)Math.sin(u*7.4f+seconds*.14f+phase);
                float secondary=(float)Math.sin(u*13.7f-seconds*.082f+phase*1.18f);
                float bend=slow*.034f+secondary*.013f;
                // Ribbons drift across one another; the top and depth share
                // a smoothly varying fold so the silhouette has no flat bands.
                ceiling[layer][x]=.12f+layer*.075f+bend;
                depth[layer][x]=.39f+layer*.057f+
                    .045f*(float)Math.sin(u*5.6f+seconds*.075f+phase);
                // Very low-contrast vertical fibrils, blurred by the broad
                // emission envelope rather than painted as separate bars.
                filament[layer][x]=.79f+
                    .13f*(float)Math.sin(u*23.1f-seconds*.21f+phase)+
                    .08f*(float)Math.sin(u*49.7f+seconds*.12f+phase*.6f);
            }
        }
        int offset=0;
        for(int y=0;y<height;y++){
            float v=(y+.5f)/height;
            for(int x=0;x<width;x++){
                float energy=0f,r=0f,g=0f,b=0f,transmission=1f;
                for(int layer=0;layer<LAYERS;layer++){
                    float position=(v-ceiling[layer][x])/depth[layer][x];
                    if(position<=0f||position>=1f)continue;
                    float entry=smooth(0f,.19f,position);
                    float exit=1f-smooth(.40f,1f,position);
                    float e=ENERGY[layer]*entry*exit*
                        filament[layer][x]*pulse[layer];
                    if(e<=.0001f)continue;
                    // Front-to-back coverage accumulation; smooth layers
                    // overlap without producing opaque geometry or seams.
                    float opacity=Math.min(.72f,e);
                    transmission*=1f-opacity;
                    energy+=e;
                    float tint=smooth(.27f,.88f,position);
                    r+=e*(RED[layer]+tint*23f);
                    g+=e*(GREEN[layer]-tint*26f);
                    b+=e*(BLUE[layer]+tint*4f);
                }
                if(energy<.001f){
                    pixels[offset++]=0;
                }else{
                    int a=Math.min(255,Math.max(0,(int)((1f-transmission)*255f+.5f)));
                    int rr=Math.min(255,(int)(r/energy+.5f));
                    int gg=Math.min(255,(int)(g/energy+.5f));
                    int bb=Math.min(255,(int)(b/energy+.5f));
                    pixels[offset++]=(a<<24)|(rr<<16)|(gg<<8)|bb;
                }
            }
        }
    }
}
