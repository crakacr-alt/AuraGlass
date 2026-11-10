package com.auraglass.app;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.os.SystemClock;
import android.view.View;
public final class WeatherCardView extends View {
    private final WeatherCardPainter painter=new WeatherCardPainter();
    private long started=SystemClock.uptimeMillis();
    private boolean running,held;
    private float fingerX,fingerY;
    private long lastPress;
    private final Paint ripplePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip=new Path();
    private final SnowEdgeLayer snowEdge=new SnowEdgeLayer();
    private final RenderBudget budget;
    private WeatherRepository.Snapshot cachedWeather;
    private long weatherReadAt;
    public WeatherCardView(Context c){
        super(c);budget=new RenderBudget(c);
        setOnClickListener(v->{if(!SamsungWeather.open(getContext()))getContext().startActivity(
            new android.content.Intent(getContext(),MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));});
    }
    @Override protected void onDraw(Canvas c){
        long frameStart=SystemClock.uptimeMillis();
        super.onDraw(c);
        long now=SystemClock.uptimeMillis();
        if(cachedWeather==null||now-weatherReadAt>30000){
            cachedWeather=WeatherRepository.read(getContext());
            weatherReadAt=now;
        }
        painter.draw(c,getContext(),getWidth(),getHeight(),cachedWeather,
            (now-started)/1000f);
        String mode=Config.str(getContext(),"weather_mode","auto");
        WeatherRepository.Snapshot weather=cachedWeather;
        boolean snow="snow".equals(mode)||"ice".equals(mode)||
            "auto".equals(mode)&&weather.available&&
            (weather.code>=71&&weather.code<=77||weather.code>=85&&weather.code<=86);
        if(snow&&Config.bool(getContext(),"snow_accumulation",true)){
            float d=getResources().getDisplayMetrics().density;
            clip.reset();
            clip.addRoundRect(new RectF(0,0,getWidth(),getHeight()),28*d,28*d,Path.Direction.CW);
            c.save();c.clipPath(clip);
            snowEdge.draw(c,getWidth(),getHeight(),now,.65f);
            c.restore();
        }
        long age=now-lastPress;
        if(lastPress>0&&(held||age<660)){
            float fade=held?1f:Math.max(0,1-age/660f);
            float radius=(52f+(held?0:age*.10f))*getResources().getDisplayMetrics().density;
            ripplePaint.setShader(new RadialGradient(fingerX,fingerY,radius,
                new int[]{((int)(57*fade)<<24)|0x00f6f9ff,((int)(25*fade)<<24)|0x00a3fff3,0x00ffffff},
                null,Shader.TileMode.CLAMP));
            clip.reset();clip.addRoundRect(new RectF(0,0,getWidth(),getHeight()),
                28f*getResources().getDisplayMetrics().density,
                28f*getResources().getDisplayMetrics().density,Path.Direction.CW);
            c.save();c.clipPath(clip);c.drawCircle(fingerX,fingerY,radius,ripplePaint);c.restore();
            ripplePaint.setShader(null);
        }
        if(running&&isShown())
            postInvalidateDelayed(budget.nextDelay(frameStart,
                SystemClock.uptimeMillis()-frameStart));
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            if(Config.bool(getContext(),"haptics",true))
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            snowEdge.touch(event.getX(),SystemClock.uptimeMillis());
            fingerX=event.getX();fingerY=event.getY();held=true;lastPress=SystemClock.uptimeMillis();
            invalidate();
        }else if(action==MotionEvent.ACTION_MOVE){
            fingerX=event.getX();fingerY=event.getY();invalidate();
        }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            held=false;lastPress=SystemClock.uptimeMillis();invalidate();
        }
        return super.onTouchEvent(event);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();running=true;invalidate();}
    @Override protected void onDetachedFromWindow(){running=false;super.onDetachedFromWindow();}
    public void refresh(){
        weatherReadAt=0;
        invalidate();
    }
}
