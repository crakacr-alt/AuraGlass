package com.auraglass.app;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import java.util.Calendar;

/** One shared visual specification for home animated card and system widget snapshot. */
public final class WeatherCardPainter {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    public void draw(Canvas canvas,Context ctx,float width,float height,WeatherRepository.Snapshot s,float seconds){
        if(width<=0||height<=0)return;
        if(Config.bool(ctx,"reduce_motion",false))seconds=0;
        canvas.save();
        float sx=width/760f,sy=height/310f;
        canvas.scale(sx,sy);
        RectF b=new RectF(4,4,756,306);
        p.setStyle(Paint.Style.FILL);

        Calendar clock=Calendar.getInstance();
        int hour=clock.get(Calendar.HOUR_OF_DAY);
        String period=Config.str(ctx,"time_mode","auto");
        boolean night="night".equals(period)||("auto".equals(period)&&(hour>=20||hour<6));
        boolean evening="evening".equals(period)||("auto".equals(period)&&(hour>=17&&hour<20));
        int[] palette=night?new int[]{0xdf354264,0xe55a5d8e,0xd2485b7e}:
            evening?new int[]{0xe5e6b4ca,0xd9b99eb6,0xb5998eb5}:
            new int[]{0xc9dedaf0,0xc69caccd,0x998e95bb};
        p.setShader(new LinearGradient(0,0,760,310,palette,null,Shader.TileMode.CLAMP));
        canvas.drawRoundRect(b,52,52,p);p.setShader(null);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);
        p.setColor(0x96ffffff);canvas.drawRoundRect(b,52,52,p);
        p.setColor(0x4bffffff);
        canvas.drawLine(53,9,694,9,p);
        p.setStyle(Paint.Style.FILL);
        for(int i=0;i<10;i++){
            float x=530+i*18f, y=46+(float)Math.sin(i*.9+seconds*.4)*12;
            p.setColor(Color.argb(10+(i%4)*4,255,255,255));
            canvas.drawCircle(x,y,16+(i%3)*8,p);
        }
        // Ambient weather animation lives behind text and respects the theme mode.
        String chosen=Config.str(ctx,"weather_mode","auto");
        boolean rain="rain".equals(chosen)||"storm".equals(chosen)||
            ("auto".equals(chosen)&&((s.code>=51&&s.code<=67)||(s.code>=80&&s.code<=82)||s.code>=95));
        boolean snow="snow".equals(chosen)||"ice".equals(chosen)||
            ("auto".equals(chosen)&&((s.code>=71&&s.code<=77)||(s.code>=85&&s.code<=86)));
        boolean storm="storm".equals(chosen)||("auto".equals(chosen)&&s.code>=95);
        float amt=Config.number(ctx,"intensity",.58f);
        if(rain){
            p.setStrokeWidth(2);p.setStrokeCap(Paint.Cap.ROUND);
            for(int i=0;i<(int)(15+14*amt);i++){
                float x=465+(i*37%265);
                float y=29+((i*47+seconds*(68+23*(i%4)))%237);
                p.setColor(Color.argb((int)(55+38*amt),240,247,255));
                canvas.drawLine(x,y,x-5,y+19,p);
            }
            p.setStrokeWidth(1.5f);p.setStyle(Paint.Style.STROKE);
            for(int i=0;i<6;i++){
                float x=480+(i*43%217),y=72+(i*41%160);
                p.setColor(Color.argb(37,255,255,255));
                canvas.drawOval(x-3,y-8,x+3,y+8,p);
            }
            p.setStyle(Paint.Style.FILL);
        }
        if(snow){
            for(int i=0;i<(int)(17+24*amt);i++){
                float x=440+i*47%289+(float)Math.sin(seconds*.4f+i*1.8f)*8f;
                float y=23+((i*31+seconds*(9+i%4*3))%251);
                p.setColor(Color.argb(70+(i%3)*22,243,249,255));
                canvas.drawCircle(x,y,2.2f+(i%4)*.9f,p);
            }
        }
        if(storm&&Config.bool(ctx,"lightning",true)){
            float phase=seconds%37f;
            if(phase<.17f){
                p.setColor(Color.argb((int)(48*(1-phase/.17f)),241,246,255));
                canvas.drawRoundRect(b,52,52,p);
            }
        }
        if(!rain&&!snow&&!storm){
            float sunX=632,sunY=101;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);
            for(int i=0;i<8;i++){
                float a=(float)(i*Math.PI/4+seconds*.02);
                float x=sunX+(float)Math.cos(a)*53,y=sunY+(float)Math.sin(a)*53;
                p.setColor(Color.argb(night?20:37,255,253,238));
                canvas.drawLine(x,y,x+(float)Math.cos(a)*19,y+(float)Math.sin(a)*19,p);
            }
            p.setStyle(Paint.Style.FILL);
        }
        if("ice".equals(chosen)){
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f);
            p.setColor(0x83dff5ff);
            for(int branch=0;branch<5;branch++){
                float x=745,y=32+branch*51;
                canvas.drawLine(x,y,x-18,y-9,p);
                canvas.drawLine(x-8,y-4,x-14,y-15,p);
                canvas.drawLine(x-8,y-4,x-19,y+2,p);
            }
            p.setStyle(Paint.Style.FILL);
        }
        text(canvas,"AURA WEATHER",52,64,25,0xe7ffffff,true);
        text(canvas,s.available?s.city:"Выберите город",52,112,28,0xffffffff,false);
        text(canvas,s.available?(s.temp+"°"):"—",48,232,111,0xffffffff,false);
        text(canvas,s.condition(),247,184,33,0xffffffff,true);
        text(canvas,s.available?"Макс. "+s.max+"°   Мин. "+s.min+"°":"Настройте город в AURA",250,229,25,0xe3ffffff,false);
        String icon=s.symbol();
        text(canvas,icon,605,160,91,0xf7ffffff,false);
        p.setColor(0x39ffffff);canvas.drawRoundRect(new RectF(50,264,710,267),2,2,p);
        canvas.restore();
    }
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        p.setShader(null);p.setStyle(Paint.Style.FILL);
        p.setColor(color);p.setTypeface(bold?android.graphics.Typeface.create("sans-serif-medium",0):
            android.graphics.Typeface.create("sans-serif",0));
        p.setTextSize(size);
        c.drawText(s,x,y,p);
    }
}
