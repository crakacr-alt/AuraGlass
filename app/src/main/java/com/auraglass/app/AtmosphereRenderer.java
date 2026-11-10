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
import java.util.Calendar;
import java.util.Random;

/** Shared deterministic scene renderer: wallpaper and in-app preview use identical weather/light rules. */
public final class AtmosphereRenderer {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final IceGlassLayer iceGlass=new IceGlassLayer();
    private final CelestialLayer celestial=new CelestialLayer();
    private final float[] rx=new float[125],ry=new float[125],rv=new float[125],rs=new float[125];
    private String mode="auto", period="auto";
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
        mode=Config.str(ctx,"weather_mode","auto");
        period=Config.str(ctx,"time_mode","auto");
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
        wind=Math.max(-1f,Math.min(1f,climate.wind/28f));
        boolean manual=!"auto".equals(mode);
        climateBoost=ClimateGlass.frostBoost(climate,manual);
        iceGlass.configure(Config.number(ctx,"frost_strength",.92f),
            Config.number(ctx,"frost_recovery",1f)*ClimateGlass.recoveryMultiplier(climate,manual),
            Config.number(ctx,"touch_radius",.057f),
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
        Calendar cal=Calendar.getInstance();
        return cal.get(Calendar.HOUR_OF_DAY)+cal.get(Calendar.MINUTE)/60f;
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
        float daylight=smooth(5.2f,9f,hr)*(1f-smooth(17.3f,21.6f,hr));
        float twilight=smooth(15.5f,18.1f,hr)*(1-smooth(20.1f,22.0f,hr));
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
        int top=blend(Color.rgb(13,21,50),Color.rgb(116,155,193),daylight);
        top=blend(top,Color.rgb(113,86,142),twilight*.68f);
        int center=blend(Color.rgb(41,45,92),Color.rgb(200,186,211),daylight);
        center=blend(center,Color.rgb(220,131,157),twilight*.72f);
        int bottom=blend(Color.rgb(88,75,116),Color.rgb(225,201,204),daylight);
        bottom=blend(bottom,Color.rgb(255,169,129),twilight*.63f);
        if(ice){top=blend(top,Color.rgb(57,103,144),.28f*icePower);bottom=blend(bottom,Color.rgb(151,194,214),.20f*icePower);}
        if(rain){top=blend(top,Color.rgb(43,57,83),.32f*rainPower);center=blend(center,Color.rgb(75,89,122),.25f*rainPower);}
        p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(0,0,w,h,
            new int[]{top,center,bottom},new float[]{0,.56f,1},Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);p.setShader(null);
        glow(c,w*.76f,h*(.24f+.11f*(1-daylight)),w*.75f,alpha(Color.rgb(241,179,221),(int)(61+daylight*35)));
        glow(c,w*.12f,h*.77f,w*.7f,alpha(Color.rgb(122,165,225),72));
        if(daylight>.08f){
            float sunX=w*(.22f+.49f*daylight);
            float sunY=h*(.34f-.22f*daylight);
            glow(c,sunX,sunY,w*.58f,alpha(Color.rgb(255,231,187),(int)(93*daylight)));
            disk(c,sunX,sunY,Math.max(6,w*.045f),alpha(Color.WHITE,(int)(190*daylight)));
            for(int i=0;i<8;i++){
                double a=i*Math.PI/4+.12;
                float len=w*.65f;
                Path ray=new Path();ray.moveTo(sunX,sunY);
                ray.lineTo(sunX+(float)Math.cos(a-.055)*len,sunY+(float)Math.sin(a-.055)*len);
                ray.lineTo(sunX+(float)Math.cos(a+.055)*len,sunY+(float)Math.sin(a+.055)*len);
                ray.close();p.setColor(alpha(Color.rgb(255,230,194),(int)(daylight*14)));c.drawPath(ray,p);
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
            disk(c,moonX,moonY,Math.max(6,w*.045f),alpha(0xffe7e9ff,(int)(175*night)));
            p.setColor(alpha(top,(int)(210*night)));
            c.drawCircle(moonX+w*.013f,moonY-w*.012f,Math.max(5,w*.042f),p);
            if(aurora&&(ice||"clear".equals(weather)||"clear".equals(before))&&(hr>=20||hr<=6)){
                float clearBlend=("clear".equals(weather)?mix:0f)+("clear".equals(before)?1f-mix:0f);
                c.save();
                c.translate(-parallaxX*w*.024f,-parallaxY*h*.014f);
                drawAurora(c,w,h,motion,night*Math.max(clearBlend,icePower));
                c.restore();
            }
            // Smooth, separately phased time-lapse comets are handled by CelestialLayer.
        }
        for(int i=0;i<4;i++){
            float fx=rx[80+i]*w+ (float)Math.sin(motion*.025+i)*w*.045f;
            float fy=h*(.29f+i*.14f);
            float size=w*(.22f+.1f*rs[80+i]);
            glow(c,fx,fy,size,alpha(Color.WHITE,(int)(10+daylight*19)));
        }
        drawRidge(c,w,h,motion,daylight,twilight);
        if(rain)drawRain(c,w,h,motion,intensity*rainPower,lightning&&"storm".equals(weather));
        if(snow)drawSnow(c,w,h,motion,intensity*snowPower*renderQuality);
        if(fog)glow(c,w*.5f,h*.77f,w*.9f,alpha(0xffe4e7fa,(int)(75*fogPower)));
        // Foreground cold glass: the fingertip warms the film, then moisture returns.
        float glassFilm=glassTouch?.21f:0f;
        float frozenSurface=Math.max(glassFilm,icePower*climateBoost);
        iceGlass.draw(c,width,height,ms,frozenSurface);
    }
    private void disk(Canvas c,float x,float y,float r,int color){p.setShader(null);p.setColor(color);c.drawCircle(x,y,r,p);}
    private void glow(Canvas c,float x,float y,float radius,int color){
        p.setShader(new RadialGradient(x,y,radius,color,alpha(color,0),Shader.TileMode.CLAMP));
        c.drawCircle(x,y,radius,p);p.setShader(null);
    }
    private void drawAurora(Canvas c,float w,float h,float t,float night){
        // Layered curtains: slowly moving harmonics, feathered vertical falloff.
        int curtains=renderQuality<.6f?2:4;
        for(int curtain=0;curtain<curtains;curtain++){
            int col=curtain%3==0?0xff6ef9d9:curtain%3==1?0xff92b8ff:0xffc78eff;
            float base=h*(.06f+curtain*.032f);
            int layers=renderQuality<.6f?3:5;
            for(int layer=0;layer<layers;layer++){
                float strip=h*(.044f+layer*.024f);
                Path shape=new Path();
                for(int n=0;n<=32;n++){
                    float x=w*(n/32f*1.23f-.13f);
                    float s=(float)Math.sin(n*.27f+t*.13f+curtain*.83f);
                    float fold=(float)Math.sin(n*.57f-t*.064f+curtain*1.3f);
                    float y=base+h*.034f*s+h*.024f*fold+layer*h*.006f;
                    if(n==0)shape.moveTo(x,y);
                    else shape.lineTo(x,y);
                }
                for(int n=32;n>=0;n--){
                    float x=w*(n/32f*1.23f-.13f);
                    float s=(float)Math.sin(n*.27f+t*.13f+curtain*.83f);
                    float fold=(float)Math.sin(n*.57f-t*.064f+curtain*1.3f);
                    float y=base+h*.034f*s+h*.024f*fold+layer*h*.006f+strip;
                    shape.lineTo(x,y);
                }
                shape.close();
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0,base,0,base+h*.23f,
                    alpha(col,(int)(night*(26-layer*3))),alpha(col,0),Shader.TileMode.CLAMP));
                c.drawPath(shape,p);
                p.setShader(null);
            }
        }
    }
    private void drawRidge(Canvas c,float w,float h,float t,float day,float eve){
        for(int layer=0;layer<3;layer++){
            float base=h*(.78f+layer*.09f);
            Path path=new Path();path.moveTo(0,h);
            path.lineTo(0,base);
            for(int x=0;x<=24;x++){
                float px=w*x/24f;
                float py=base+(float)Math.sin(x*.46f+layer*2.1f+t*.007f)*h*.025f
                    +(float)Math.sin(x*.91f+layer*.7f)*h*.015f;
                path.lineTo(px,py);
            }
            path.lineTo(w,h);path.close();
            int near=blend(0xff232844,0xff536f83,day*.78f);
            p.setColor(alpha(near,layer==0?84:layer==1?145:205));
            c.drawPath(path,p);
        }
        glow(c,w*.6f,h*.95f,w*.6f,alpha(0xffffd7c3,(int)(eve*37)));
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
