package com.auraglass.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.widget.FrameLayout;

/** Glass card with tactile thermal/optical response, without stealing child clicks. */
public final class GlassPanel extends FrameLayout {
    private final Paint sparkle=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Shader warmRipple=new RadialGradient(0,0,1f,
        new int[]{0x2beafbff,0x1dcceff9,0x00ffffff},
        new float[]{0,.35f,1},Shader.TileMode.CLAMP);

    // A static cold film is shared by untouched cards. This avoids creating
    // ~6 MB of heat/bitmap arrays for every visible panel on the Home screen.
    // A card gets its own thermal layer only while the user draws on it.
    private static final class IdleFilm {
        static final IceGlassLayer LAYER=new IceGlassLayer();
        static float intensity=Float.NaN;
        static boolean reducedMotion;
        static void configure(float desired,boolean reduced){
            if(Float.isNaN(intensity)||Math.abs(desired-intensity)>.003f||
                reducedMotion!=reduced){
                intensity=desired;reducedMotion=reduced;
                LAYER.configure(desired,1f,.035f,false,reduced);
            }
        }
    }
    private final Path clip=new Path();
    private final RectF bounds=new RectF();
    private IceGlassLayer ice;
    private final SnowEdgeLayer snowEdge=new SnowEdgeLayer();
    private float pressedX,pressedY;
    private long pressStartedAt,pressEndedAt,lastWarmAt;
    private boolean holding;

    public GlassPanel(Context ctx){
        super(ctx);
        setBackground(new GlassSurface(ctx));
        setClipToPadding(false);
        setWillNotDraw(false);
    }
    private float frostStrength(){
        String mode=Config.str(getContext(),"weather_mode","auto");
        boolean iceMode="ice".equals(mode)||"snow".equals(mode);
        if(iceMode&&Config.bool(getContext(),"ice_enabled",true))return .82f;
        return Config.bool(getContext(),"glass_touch",true)?.18f:0;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        long now=event.getEventTime();
        float strength=frostStrength();
        boolean enabled=Config.bool(getContext(),"touch_melt",true);
        if(action==MotionEvent.ACTION_DOWN){
            if(Config.bool(getContext(),"haptics",true))
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            snowEdge.touch(event.getX(),now);
            pressedX=event.getX();pressedY=event.getY();
            pressStartedAt=now;pressEndedAt=0;holding=true;
            if(enabled&&strength>.01f){
                if(ice==null)ice=new IceGlassLayer();
                ice.configure(Config.number(getContext(),"frost_strength",.92f),
                    Config.number(getContext(),"frost_recovery",1f),
                    Config.number(getContext(),"touch_radius",.035f),true,
                    Config.bool(getContext(),"reduce_motion",false));
                ice.startStroke(pressedX,pressedY,getWidth(),getHeight(),now);
                lastWarmAt=now;
            }
            invalidate();
        }else if(action==MotionEvent.ACTION_MOVE){
            // Include coalesced historical points so fast swipes leave no gaps.
            for(int i=0;i<event.getHistorySize();i++)
                snowEdge.touch(event.getHistoricalX(i),event.getHistoricalEventTime(i));
            snowEdge.touch(event.getX(),now);
            pressedX=event.getX();pressedY=event.getY();
            if(ice!=null&&enabled&&strength>.01f){
                for(int i=0;i<event.getHistorySize();i++){
                    ice.stroke(event.getHistoricalX(i),event.getHistoricalY(i),
                        getWidth(),getHeight(),event.getHistoricalEventTime(i));
                }
                ice.stroke(pressedX,pressedY,getWidth(),getHeight(),now);
                lastWarmAt=now;
            }
            invalidate();
        }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            if(action==MotionEvent.ACTION_UP)snowEdge.touch(event.getX(),now);
            holding=false;pressEndedAt=now;
            if(ice!=null)ice.endStroke();
            invalidate();
        }
        return super.dispatchTouchEvent(event);
    }

    @Override protected void dispatchDraw(Canvas canvas){
        float w=getWidth(),h=getHeight();
        float strength=frostStrength();
        long now=SystemClock.uptimeMillis();
        boolean transientCold=lastWarmAt>0&&now-lastWarmAt<16000;
        if(ice!=null&&!transientCold&&!holding)ice=null;
        if(w>0&&h>0){
            bounds.set(0,0,w,h);
            clip.reset();
            float radius=27*getResources().getDisplayMetrics().density;
            clip.addRoundRect(bounds,radius,radius,Path.Direction.CW);
        }
        if(w>0&&h>0&&(strength>.01f||transientCold)){
            canvas.save();
            canvas.clipPath(clip);
            if(strength>.01f){
                if(ice!=null)ice.draw(canvas,getWidth(),getHeight(),now,strength);
                else{
                    IdleFilm.configure(Config.number(getContext(),"frost_strength",.92f),
                        Config.bool(getContext(),"reduce_motion",false));
                    IdleFilm.LAYER.draw(canvas,getWidth(),getHeight(),now,strength);
                }
            }
            canvas.restore();
        }
        super.dispatchDraw(canvas);
        if(w>0&&h>0&&Config.bool(getContext(),"snow_accumulation",true)){
            String current=Config.str(getContext(),"weather_mode","auto");
            WeatherRepository.Snapshot weather=WeatherRepository.read(getContext());
            boolean snowfall="snow".equals(current)||"ice".equals(current)||
                "auto".equals(current)&&weather.available&&
                (weather.code>=71&&weather.code<=77||weather.code>=85&&weather.code<=86);
            if(snowfall){
                canvas.save();
                canvas.clipPath(clip);
                snowEdge.draw(canvas,w,h,now,"ice".equals(current)?.75f:.56f);
                canvas.restore();
                if(getWindowVisibility()==VISIBLE&&!Config.bool(getContext(),"reduce_motion",false))
                    postInvalidateDelayed(220);
            }
        }
        if(w>0&&h>0&&(holding||pressEndedAt>0&&now-pressEndedAt<720)){
            float age=holding?0:(now-pressEndedAt)/720f;
            float fade=(1-Math.max(0,Math.min(1,age)));
            float d=getResources().getDisplayMetrics().density;
            float radius=(holding?56:58+age*84)*d;
            sparkle.setShader(warmRipple);
            sparkle.setAlpha((int)(255*fade));
            canvas.save();
            canvas.clipPath(clip);
            canvas.translate(pressedX,pressedY);
            canvas.scale(radius,radius);
            canvas.drawCircle(0f,0f,1f,sparkle);
            canvas.restore();
            sparkle.setShader(null);
            sparkle.setAlpha(255);
            if(!Config.bool(getContext(),"reduce_motion",false))postInvalidateDelayed(18);
        }else if(transientCold && strength>.01f){
            // Only active melting/refreezing is updated. Idle glass panels do not animate.
            postInvalidateDelayed(80);
        }
    }

    private static final class GlassSurface extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Context context;
        private final RectF rect=new RectF();
        private Shader surfaceShader;
        private int shaderWidth=-1,shaderHeight=-1;
        private float shaderOpacity=-1f;
        GlassSurface(Context ctx){context=ctx;}
        @Override public void draw(Canvas canvas){
            float d=context.getResources().getDisplayMetrics().density;
            float w=getBounds().width(),h=getBounds().height();
            float r=27*d;
            float opacity=Config.number(context,"glass",.67f);
            rect.set(1*d,1*d,Math.max(1,w-1*d),Math.max(1,h-1*d));
            paint.setStyle(Paint.Style.FILL);
            int wi=Math.round(w),he=Math.round(h);
            if(surfaceShader==null||shaderWidth!=wi||shaderHeight!=he||
                Math.abs(shaderOpacity-opacity)>.004f){
                shaderWidth=wi;shaderHeight=he;shaderOpacity=opacity;
                surfaceShader=new LinearGradient(0,0,w,Math.max(1,h),
                    Color.argb((int)(205*opacity),225,223,242),
                    Color.argb((int)(153*opacity),114,130,166),
                    Shader.TileMode.CLAMP);
            }
            paint.setShader(surfaceShader);
            canvas.drawRoundRect(rect,r,r,paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1*d);
            paint.setColor(0x70ffffff);
            canvas.drawRoundRect(rect,r,r,paint);
            paint.setColor(0x37ffffff);
            paint.setStrokeWidth(.7f*d);
            canvas.drawLine(19*d,3*d,Math.max(19*d,w-19*d),3*d,paint);
            paint.setStyle(Paint.Style.FILL);
        }
        @Override public void setAlpha(int alpha){}
        @Override public void setColorFilter(android.graphics.ColorFilter filter){}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
}
