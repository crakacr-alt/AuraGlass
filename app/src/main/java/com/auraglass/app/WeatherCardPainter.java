package com.auraglass.app;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/** One shared visual specification for home animated card and system widget snapshot. */
public final class WeatherCardPainter {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    public void draw(Canvas canvas,Context ctx,float width,float height,WeatherRepository.Snapshot s,float seconds){
        if(width<=0||height<=0)return;
        canvas.save();
        float sx=width/760f,sy=height/310f;
        canvas.scale(sx,sy);
        RectF b=new RectF(4,4,756,306);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0,0,760,310,
            new int[]{0xc9dedaf0,0xc69caccd,0x998e95bb},null,Shader.TileMode.CLAMP));
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
