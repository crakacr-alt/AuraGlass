package com.auraglass.app;

import android.content.Context;
import android.os.SystemClock;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.SparseArray;
import java.util.Calendar;
import java.util.Random;

/** Shared deterministic scene renderer: wallpaper and in-app preview use identical weather/light rules. */
public final class AtmosphereRenderer {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path sunRayPath=new Path();
    private final ArcticLandscape landscape=new ArcticLandscape();
    private final SparseArray<Shader> glowShaders=new SparseArray<>();
    private final Paint glowPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AuroraCurtainLayer auroraCurtains=new AuroraCurtainLayer();
    private final IceGlassLayer iceGlass=new IceGlassLayer();
    private final CelestialLayer celestial=new CelestialLayer();
    private final MoonPhase moonPhase=new MoonPhase();
    private final SolarLighting solarLighting=new SolarLighting();
    private final float[] solarLevels=new float[2];
    private float animatedDaylight=Float.NaN,animatedTwilight=Float.NaN;
    private java.util.TimeZone cityTimezone=java.util.TimeZone.getDefault();
    private long cachedClockAt=0;
    private float cachedClockHour=12f;
    private final float[] rx=new float[125],ry=new float[125],rv=new float[125],rs=new float[125];
    private String mode="auto", period="auto";
    // A preview owns its mode without ever writing global SharedPreferences.
    private String previewWeather=null,previewTime=null;
    public void setPreviewPreset(Context context,String weather,String time){
        previewWeather=weather;
        previewTime=time;
        reload(context);
        previousWeather=null;
        weatherChangeAt=0;
        animatedHour=Float.NaN;
        animatedDaylight=Float.NaN;
        animatedTwilight=Float.NaN;
    }
    private float intensity=.58f,speed=.65f;
    private boolean aurora=true,stars=true,lightning=true,reduceMotion=false;
    private boolean galaxy=true,comets=true,iceEnabled=true,snowEnabled=true,glassTouch=true;
    private float wind=0.17f;
    private float targetParallaxX,targetParallaxY,parallaxX,parallaxY;
    private float parallaxAmount=.35f,renderQuality=1f,climateBoost=1f;
    private boolean parallaxEnabled=false;
    private int weatherCode=0;
    private String previousWeather=null;
    private long weatherChangeAt=0;
    private float animatedHour=Float.NaN;
    private long previousFrameAt=0;
    public AtmosphereRenderer(){
        Random random=new Random(570319);
        for(int i=0;i<rx.length;i++){
            rx[i]=random.nextFloat();ry[i]=random.nextFloat();
            rv[i]=.4f+random.nextFloat()*.9f;rs[i]=.5f+random.nextFloat()*.9f;
        }
    }
    public void reload(Context ctx){
        String old=actualWeather();
        mode=previewWeather!=null?previewWeather:Config.str(ctx,"weather_mode","auto");
        period=previewTime!=null?previewTime:Config.str(ctx,"time_mode","auto");
        intensity=Config.number(ctx,"intensity",.58f);
        speed=Config.number(ctx,"speed",.65f);
        aurora=Config.bool(ctx,"aurora",true);
        stars=Config.bool(ctx,"stars",true);
        lightning=Config.bool(ctx,"lightning",true);
        galaxy=Config.bool(ctx,"galaxy",true);
        comets=Config.bool(ctx,"comets",true);
        iceEnabled=Config.bool(ctx,"ice_enabled",true);
        snowEnabled=Config.bool(ctx,"snow_enabled",true);
        glassTouch=Config.bool(ctx,"glass_touch",true);
        reduceMotion=Config.bool(ctx,"reduce_motion",false);
        parallaxEnabled=Config.bool(ctx,"parallax",false)&&!reduceMotion;
        parallaxAmount=Config.number(ctx,"parallax_strength",.35f);
        WeatherRepository.Snapshot climate=WeatherRepository.read(ctx);
        solarLighting.load(climate.solarRises,climate.solarSets);
        String zone=climate.solarTimezone;
        if(zone!=null&&!zone.isEmpty()&&
            java.util.Arrays.asList(java.util.TimeZone.getAvailableIDs()).contains(zone))
            cityTimezone=java.util.TimeZone.getTimeZone(zone);
        else cityTimezone=java.util.TimeZone.getDefault();
        cachedClockAt=0;
        wind=Math.max(-1f,Math.min(1f,climate.wind/28f));
        boolean manual=!"auto".equals(mode);
        climateBoost=ClimateGlass.frostBoost(climate,manual);
        iceGlass.configure(Config.number(ctx,"frost_strength",.92f),
            Config.number(ctx,"frost_recovery",1f)*ClimateGlass.recoveryMultiplier(climate,manual),
            Config.number(ctx,"touch_radius",.035f),
            Config.bool(ctx,"touch_melt",true),reduceMotion);
        weatherCode=climate.code;
        String next=actualWeather();
        if(!old.equals(next)){
            previousWeather=old;
            weatherChangeAt=SystemClock.uptimeMillis();
        }
    }
    private float effectPower(String current,String previous,String key,float mix){
        return (current.equals(key)?mix:0f)+(previous.equals(key)?1f-mix:0f);
    }
    public void setParallax(float x,float y){
        targetParallaxX=Math.max(-1,Math.min(1,x));
        targetParallaxY=Math.max(-1,Math.min(1,y));
    }
    public void setRenderQuality(float q){renderQuality=Math.max(.2f,Math.min(1f,q));}
    public void startTouch(float x,float y,int width,int height,long when){
        iceGlass.startStroke(x,y,width,height,when);
    }
    public void moveTouch(float x,float y,int width,int height,long when){
        iceGlass.stroke(x,y,width,height,when);
    }
    public void endTouch(){iceGlass.endStroke();}
    public void startTouch(int pointerId,float x,float y,int width,int height,long when){
        iceGlass.startStroke(pointerId,x,y,width,height,when);
    }
    public void moveTouch(int pointerId,float x,float y,int width,int height,long when){
        iceGlass.stroke(pointerId,x,y,width,height,when);
    }
    public void endTouch(int pointerId){iceGlass.endStroke(pointerId);}
    private int alpha(int color,int a){return (color&0x00ffffff)|(Math.max(0,Math.min(a,255))<<24);}
    private int blend(int a,int b,float t){
        t=Config.clamp(t,0,1);
        return Color.rgb((int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),
            (int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),
            (int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));
    }
    private float smooth(float a,float b,float v){
        float t=Config.clamp((v-a)/(b-a),0,1);
        return t*t*(3-2*t);
    }
    private String actualWeather(){
        if(!"auto".equals(mode))return mode;
        if(weatherCode>=95)return "storm";
        if(weatherCode>=71&&weatherCode<=77||weatherCode>=85&&weatherCode<=86)return "snow";
        if(weatherCode>=51&&weatherCode<=67||weatherCode>=80&&weatherCode<=82)return "rain";
        if(weatherCode>=45&&weatherCode<=48)return "fog";
        return "clear";
    }
    private float hour(){
        if("morning".equals(period))return 7.5f;
        if("day".equals(period))return 13.5f;
        if("evening".equals(period))return 18.3f;
        if("night".equals(period))return 1.5f;
        // At 120 Hz, avoid allocating a Calendar on every frame.
        long now=System.currentTimeMillis();
        if(cachedClockAt==0||now<cachedClockAt||now-cachedClockAt>=2000){
            Calendar cal=Calendar.getInstance(cityTimezone);
            cachedClockHour=cal.get(Calendar.HOUR_OF_DAY)+cal.get(Calendar.MINUTE)/60f+
                cal.get(Calendar.SECOND)/3600f;
            cachedClockAt=now;
        }
        return (cachedClockHour+(now-cachedClockAt)/3600000f)%24f;
    }
    public void draw(Canvas c,int width,int height,long ms){
        if(width<=0||height<=0)return;
        float w=width,h=height;
        float t=ms/1000f*speed;
        float targetHour=hour();
        if(Float.isNaN(animatedHour))animatedHour=targetHour;
        if(previousFrameAt==0)previousFrameAt=ms;
        float dt=Math.max(0,Math.min(150,ms-previousFrameAt));
        previousFrameAt=ms;
        float smoothing=1-(float)Math.exp(-dt/320f);
        float parallaxScale=parallaxEnabled?Math.max(0,Math.min(1,parallaxAmount)):0;
        parallaxX+=(targetParallaxX*parallaxScale-parallaxX)*smoothing;
        parallaxY+=(targetParallaxY*parallaxScale-parallaxY)*smoothing;
        float hourDifference=((targetHour-animatedHour+36f)%24f)-12f;
        animatedHour=(animatedHour+hourDifference*(1f-(float)Math.exp(-dt/1800f))+24f)%24f;
        float hr=animatedHour;
        float targetDay=smooth(5.2f,9f,hr)*(1f-smooth(17.3f,21.6f,hr));
        float targetTwilight=smooth(15.5f,18.1f,hr)*(1-smooth(20.1f,22.0f,hr));
        // Manual morning/day/evening/night presets deliberately override
        // actual solar time. In auto mode the chosen city's forecast controls
        // dawn and dusk, even when the phone is in another timezone.
        if("auto".equals(period)&&solarLighting.sample(System.currentTimeMillis(),solarLevels)){
            targetDay=solarLevels[0];
            targetTwilight=solarLevels[1];
        }
        if(Float.isNaN(animatedDaylight))animatedDaylight=targetDay;
        if(Float.isNaN(animatedTwilight))animatedTwilight=targetTwilight;
        float lightEase=1-(float)Math.exp(-dt/1800f);
        animatedDaylight+=(targetDay-animatedDaylight)*lightEase;
        animatedTwilight+=(targetTwilight-animatedTwilight)*lightEase;
        float daylight=animatedDaylight;
        float twilight=animatedTwilight;
        String weather=actualWeather();
        float mix=previousWeather==null?1:smooth(0,1,(ms-weatherChangeAt)/3400f);
        String before=previousWeather==null?weather:previousWeather;
        if(mix>=1)previousWeather=null;
        float rainPower=effectPower(weather,before,"rain",mix)+effectPower(weather,before,"storm",mix);
        float icePower=iceEnabled?
            effectPower(weather,before,"ice",mix)+.40f*effectPower(weather,before,"snow",mix):0;
        float snowPower=snowEnabled?
            effectPower(weather,before,"snow",mix)+.72f*effectPower(weather,before,"ice",mix):0;
        float fogPower=effectPower(weather,before,"fog",mix);
        boolean rain=rainPower>.005f;
        boolean ice=icePower>.005f;
        boolean snow=snowPower>.005f;
        boolean fog=fogPower>.005f;
        float motion=reduceMotion?0:t;
        // Nordic cinematic grade: deep low-light blue rather than lavender
        // pastels. Dawn brings physically motivated warm scattering at the
        // horizon, while weather desaturates the upper atmosphere.
        int top=blend(Color.rgb(5,13,28),Color.rgb(86,134,165),daylight);
        top=blend(top,Color.rgb(57,34,75),twilight*.44f);
        int center=blend(Color.rgb(17,39,65),Color.rgb(155,187,199),daylight);
        center=blend(center,Color.rgb(122,76,100),twilight*.48f);
        int bottom=blend(Color.rgb(36,62,83),Color.rgb(218,213,197),daylight);
        bottom=blend(bottom,Color.rgb(186,102,91),twilight*.61f);
        if(ice){
            top=blend(top,Color.rgb(8,33,52),.30f*icePower);
            center=blend(center,Color.rgb(24,79,96),.24f*icePower);
        }
        if(rain){
            top=blend(top,Color.rgb(26,43,57),.48f*rainPower);
            center=blend(center,Color.rgb(62,77,83),.33f*rainPower);
        }
        p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(0,0,w,h,
            new int[]{top,center,bottom},new float[]{0,.56f,1},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);p.setShader(null);
        // Broad, dim airglow and scattering instead of isolated opaque blobs.
        glow(c,w*.81f,h*.42f,w*.93f,
            alpha(0xff5f9db2,(int)(22+daylight*25)));
        glow(c,w*.17f,h*.72f,w*.86f,
            alpha(0xff6f9fad,(int)(30+daylight*15)));
        if(daylight>.08f){
            float sunX=w*(.22f+.49f*daylight);
            float sunY=h*(.34f-.22f*daylight);
            glow(c,sunX,sunY,w*.58f,alpha(Color.rgb(255,231,187),(int)(93*daylight)));
            disk(c,sunX,sunY,Math.max(6,w*.040f),
                alpha(0xffffe9c4,(int)(205*daylight)));
            p.setColor(alpha(Color.rgb(255,230,194),(int)(daylight*14)));
            for(int i=0;i<8;i++){
                double a=i*Math.PI/4+.12;
                float len=w*.65f;
                sunRayPath.rewind();
                sunRayPath.moveTo(sunX,sunY);
                sunRayPath.lineTo(sunX+(float)Math.cos(a-.055)*len,
                    sunY+(float)Math.sin(a-.055)*len);
                sunRayPath.lineTo(sunX+(float)Math.cos(a+.055)*len,
                    sunY+(float)Math.sin(a+.055)*len);
                sunRayPath.close();
                c.drawPath(sunRayPath,p);
            }
        }
        if(daylight<.9f){
            float night=1-daylight;
            c.save();
            c.translate(parallaxX*w*.045f,parallaxY*h*.023f);
            celestial.draw(c,w,h,motion,night,stars,galaxy,comets,reduceMotion,renderQuality);
            c.restore();
            float moonX=w*.72f,moonY=h*.18f;
            glow(c,moonX,moonY,w*.28f,alpha(0xffc7d8ff,(int)(49*night)));
            moonPhase.draw(c,moonX,moonY,Math.max(6,w*.045f),night,System.currentTimeMillis());
            // Use solar illumination in auto mode: an early winter sunset
            // must not hide the aurora until a fixed 20:00 clock threshold.
            if(aurora&&(ice||"clear".equals(weather)||"clear".equals(before))&&
                night>.16f&&("auto".equals(period)||hr>=20||hr<=6)){
                float clearBlend=("clear".equals(weather)?mix:0f)+("clear".equals(before)?1f-mix:0f);
                c.save();
                c.translate(-parallaxX*w*.024f,-parallaxY*h*.014f);
                auroraCurtains.draw(c,w,h,motion,night*Math.max(clearBlend,icePower),renderQuality,reduceMotion);
                c.restore();
            }
            // Smooth, separately phased time-lapse comets are handled by CelestialLayer.
        }
        // Distant atmospheric haze falls behind deterministic glacial massifs.
        landscape.draw(c,width,height,motion,daylight,twilight,parallaxX,renderQuality);
        if(rain)drawRain(c,w,h,motion,intensity*rainPower,lightning&&"storm".equals(weather));
        if(snow)drawSnow(c,w,h,motion,intensity*snowPower*renderQuality);
        if(fog)glow(c,w*.5f,h*.77f,w*.9f,alpha(0xffe4e7fa,(int)(75*fogPower)));
        // Foreground cold glass: the fingertip warms the film, then moisture returns.
        float glassFilm=glassTouch?.21f:0f;
        float frozenSurface=Math.max(glassFilm,icePower*climateBoost);
        iceGlass.draw(c,width,height,ms,frozenSurface,renderQuality);
    }
    private void disk(Canvas c,float x,float y,float r,int color){p.setShader(null);p.setColor(color);c.drawCircle(x,y,r,p);}
    private void glow(Canvas c,float x,float y,float radius,int color){
        if(radius<=.5f||Color.alpha(color)==0)return;
        // A few hues repeat every frame (sun, moon, fog, horizon).
        // Cache halo shaders in local unit coordinates; Canvas transforms
        // position/radius without allocating a new shader at 120 FPS.
        int rgb=color&0x00ffffff;
        Shader halo=glowShaders.get(rgb);
        if(halo==null){
            halo=new RadialGradient(0f,0f,1f,
                new int[]{0xff000000|rgb,rgb},null,Shader.TileMode.CLAMP);
            glowShaders.put(rgb,halo);
        }
        c.save();
        c.translate(x,y);
        c.scale(radius,radius);
        glowPaint.setShader(halo);
        glowPaint.setAlpha(Color.alpha(color));
        c.drawCircle(0f,0f,1f,glowPaint);
        glowPaint.setShader(null);
        glowPaint.setAlpha(255);
        c.restore();
    }
    private void drawRain(Canvas c,float w,float h,float t,float amount,boolean storm){
        int count=Math.max(9,(int)(rx.length*amount*.78f));
        for(int i=0;i<count;i++){
            float drift=(t*rv[i]*.13f+rx[i])%1f;
            float y=((ry[i]+t*rv[i]*.26f)%1f)*h;
            float x=((drift+ry[i]*.21f)%1.13f)*w;
            p.setColor(alpha(0xffdceaff,(int)(42+55*rs[i])));
            p.setStrokeWidth(Math.max(1f,w*.0019f*rs[i]));
            p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(x,y,x-w*.015f,y+h*.025f*rs[i],p);
        }
        // Sparse wet-glass lenslets; small white edge gives the impression of refraction.
        for(int i=0;i<13;i++){
            float x=w*rx[85+i],y=h*ry[85+i];
            float r=Math.max(2,w*.008f*rs[85+i]);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,w*.0013f));
            p.setColor(alpha(0xfff7fbff,(int)(26+amount*54)));
            c.drawOval(x-r*.6f,y-r,x+r*.6f,y+r,p);
            p.setStyle(Paint.Style.FILL);
        }
        if(storm){
            float phase=t%37;
            if(phase<.15f){
                p.setColor(alpha(Color.WHITE,(int)(24*(1-phase/.15f))));
                c.drawRect(0,0,w,h,p);
            }
        }
    }
    private void drawSnow(Canvas c,float w,float h,float t,float amount){
        if(amount<.001f)return;
        int count=Math.min(rx.length,Math.max(3,(int)(100*amount)));
        p.setStyle(Paint.Style.FILL);
        for(int i=0;i<count;i++){
            float depth=.45f+rs[i]*.6f;
            float slow=t*(.013f+.022f*rv[i])*depth;
            float fall=(ry[i]+slow)%1.11f;
            float gust=wind*t*.0065f*depth;
            float sway=(float)Math.sin(t*.3f+i*.82f)*.028f*depth;
            float x=((rx[i]+gust+sway+2)%1.08f)*w;
            float y=(fall-.06f)*h;
            float radius=Math.max(.65f,w*.0017f*depth);
            int opacity=(int)(Math.max(0,Math.min(1,amount))*(70+95*depth));
            p.setColor(alpha(0xffeffaff,opacity));
            c.drawCircle(x,y,radius,p);
            if(i%13==0&&depth>.7f){
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(.55f,radius*.45f));
                c.drawLine(x-radius*2,y,x+radius*2,y,p);
                c.drawLine(x,y-radius*2,x,y+radius*2,p);
                p.setStyle(Paint.Style.FILL);
            }
        }
    }
}
