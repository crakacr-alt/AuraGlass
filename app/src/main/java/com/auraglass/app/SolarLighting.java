package com.auraglass.app;

/**
 * Approximate cinematic daylight/twilight from actual UTC sunrise/sunset
 * instants returned by Open-Meteo. This is not a solar-position ephemeris.
 *
 * The cache contains up to 4 forecast days and is parsed only on weather
 * reload, never on every animation frame. All comparisons use UTC timestamps,
 * so display timezone and daylight-saving transitions cannot shift sunrise.
 */
public final class SolarLighting {
    private final long[] rises=new long[4],sets=new long[4];
    private int count=0;
    private static final long MIN_VALID=946684800000L; // 2000-01-01
    private static final long MAX_VALID=4102444800000L; // 2100-01-01

    public void load(String sunriseCsv,String sunsetCsv) {
        count=0;
        if(sunriseCsv==null||sunsetCsv==null)return;
        String[] a=sunriseCsv.split(","), b=sunsetCsv.split(",");
        for(int i=0;i<Math.min(4,Math.min(a.length,b.length));i++){
            try{
                long rise=Long.parseLong(a[i].trim());
                long set=Long.parseLong(b[i].trim());
                if(rise<MIN_VALID||set>MAX_VALID||set-rise<3600000L||
                    set-rise>22*3600000L)continue;
                if(count>0&&(rise<=rises[count-1]||set<=sets[count-1]))continue;
                rises[count]=rise;sets[count]=set;count++;
            }catch(NumberFormatException ignored){}
        }
    }

    private static float smooth(long from,long to,long value){
        if(to<=from)return value<from?0:1;
        float f=Math.max(0,Math.min(1,(value-from)/(float)(to-from)));
        return f*f*(3-2*f);
    }

    /**
     * Fill output[0] with day illumination and output[1] with color of
     * dawn/dusk. Return false when solar data are absent or outside the
     * forecast window, allowing deterministic offline fallback.
     */
    public boolean sample(long now,float[] output){
        if(count==0||output==null||output.length<2)return false;
        int choice=0;
        long closest=Long.MAX_VALUE;
        for(int i=0;i<count;i++){
            long noon=rises[i]+(sets[i]-rises[i])/2;
            long distance=Math.abs(now-noon);
            if(distance<closest){closest=distance;choice=i;}
        }
        long rise=rises[choice],set=sets[choice];
        // No stale solar forecast after leaving its valid date window.
        if(now<rises[0]-18*3600000L||now>sets[count-1]+18*3600000L)
            return false;
        // Twilight has soft tails; solar data determine the actual center.
        float morning=smooth(rise-35*60000L,rise+85*60000L,now);
        float evening=smooth(set-85*60000L,set+35*60000L,now);
        output[0]=morning*(1-evening);
        float dawn=smooth(rise-115*60000L,rise-20*60000L,now)*
            (1-smooth(rise-10*60000L,rise+85*60000L,now));
        float dusk=smooth(set-105*60000L,set+15*60000L,now)*
            (1-smooth(set+35*60000L,set+145*60000L,now));
        output[1]=Math.max(dawn,dusk);
        return true;
    }
}
