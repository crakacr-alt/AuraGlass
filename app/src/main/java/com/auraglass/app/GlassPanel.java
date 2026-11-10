package com.auraglass.app;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;

public final class GlassPanel extends FrameLayout {
    public GlassPanel(Context ctx){
        super(ctx);
        setBackground(new GlassSurface(ctx));
        setClipToPadding(false);
    }
    private static final class GlassSurface extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Context context;
        GlassSurface(Context c){context=c;}
        @Override public void draw(Canvas c){
            float d=context.getResources().getDisplayMetrics().density;
            float w=getBounds().width(),h=getBounds().height();
            float r=27*d;
            float opacity=Config.number(context,"glass",.67f);
            RectF rect=new RectF(1*d,1*d,w-1*d,h-1*d);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0,0,w,h,
                Color.argb((int)(210*opacity),219,216,240),
                Color.argb((int)(142*opacity),127,133,174),Shader.TileMode.CLAMP));
            c.drawRoundRect(rect,r,r,paint);
            paint.setShader(null);paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1*d);paint.setColor(0x66ffffff);
            c.drawRoundRect(rect,r,r,paint);
            paint.setColor(0x2dffffff);paint.setStrokeWidth(.6f*d);
            c.drawLine(20*d,3*d,w-20*d,3*d,paint);
            paint.setStyle(Paint.Style.FILL);
        }
        @Override public void setAlpha(int a){}
        @Override public void setColorFilter(android.graphics.ColorFilter filter){}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
}
