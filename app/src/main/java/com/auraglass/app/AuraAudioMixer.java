package com.auraglass.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Real system-stream mixer. Samsung per-app mixer is delegated to SoundAssistant. */
public final class AuraAudioMixer {
    private AuraAudioMixer(){}
    private static int dp(Activity a,float n){
        return (int)(a.getResources().getDisplayMetrics().density*n+.5f);
    }
    private static GradientDrawable glass(Activity a){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xf04d4859,0xf02b303c,0xf02d2c3e});
        g.setCornerRadius(dp(a,30));
        g.setStroke(dp(a,1),0x70ffffff);
        return g;
    }
    private static TextView title(Activity a,String str,int size){
        TextView t=new TextView(a);t.setText(str);t.setTextSize(size);
        t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER);
        return t;
    }
    private static Button button(Activity a,String label,Runnable action){
        Button b=new Button(a);b.setAllCaps(false);b.setText(label);b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff635f80));
        b.setOnClickListener(v->action.run());
        return b;
    }
    public static void show(Activity activity){
        AudioManager manager=(AudioManager)activity.getSystemService(Activity.AUDIO_SERVICE);
        if(manager==null)return;
        Dialog dialog=new Dialog(activity);
        LinearLayout body=new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity,17),dp(activity,23),dp(activity,17),dp(activity,15));
        body.setBackground(glass(activity));
        body.addView(title(activity,"Звук AURA",21),
            new LinearLayout.LayoutParams(-1,dp(activity,42)));
        TextView subtitle=title(activity,"Системные каналы · реальные уровни",12);
        subtitle.setTextColor(0xffd4cfe0);
        body.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(activity,30)));
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER);
        int[] streams={AudioManager.STREAM_SYSTEM,AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,AudioManager.STREAM_ALARM,AudioManager.STREAM_MUSIC};
        String[] icons={"✦","☎","◈","◷","♫"};
        String[] labels={"Система","Звонок","Уведом.","Будильник","Музыка"};
        for(int i=0;i<streams.length;i++){
            LinearLayout column=new LinearLayout(activity);
            column.setGravity(Gravity.CENTER);
            column.setOrientation(LinearLayout.VERTICAL);
            TextView caption=title(activity,icons[i],20);
            column.addView(caption,new LinearLayout.LayoutParams(-1,dp(activity,34)));
            VolumeColumn slider=new VolumeColumn(activity,manager,streams[i]);
            column.addView(slider,new LinearLayout.LayoutParams(-1,dp(activity,223)));
            TextView name=title(activity,labels[i],10);
            column.addView(name,new LinearLayout.LayoutParams(-1,dp(activity,32)));
            row.addView(column,new LinearLayout.LayoutParams(0,-2,1));
        }
        body.addView(row,new LinearLayout.LayoutParams(-1,-2));
        TextView hint=title(activity,
            "Уровни звонка и уведомлений могут быть связаны прошивкой Samsung.",12);
        hint.setTextColor(0xffcfccda);
        hint.setPadding(dp(activity,8),dp(activity,8),dp(activity,8),dp(activity,8));
        body.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(activity);
        Button bt=button(activity,"Bluetooth",()->{
            try{activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));}
            catch(RuntimeException ignored){}
        });
        actions.addView(bt,new LinearLayout.LayoutParams(0,dp(activity,49),1));
        Button app=button(activity,"Звук приложений",()->{
            Intent launch=activity.getPackageManager().getLaunchIntentForPackage(
                "com.samsung.android.soundassistant");
            if(launch!=null){
                activity.startActivity(launch);
            }else Toast.makeText(activity,
                "Отдельная громкость приложений доступна через Samsung SoundAssistant, если он установлен",
                Toast.LENGTH_LONG).show();
        });
        LinearLayout.LayoutParams btn=new LinearLayout.LayoutParams(0,dp(activity,49),1);
        btn.leftMargin=dp(activity,8);actions.addView(app,btn);
        body.addView(actions,new LinearLayout.LayoutParams(-1,-2));
        dialog.setContentView(body);
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams p=w.getAttributes();
            p.width=WindowManager.LayoutParams.MATCH_PARENT;
            p.height=WindowManager.LayoutParams.WRAP_CONTENT;
            p.gravity=Gravity.CENTER;
            p.dimAmount=.6f;w.setAttributes(p);
        }
        dialog.show();
        if(w!=null)w.setLayout(activity.getResources().getDisplayMetrics().widthPixels-dp(activity,24),
            WindowManager.LayoutParams.WRAP_CONTENT);
    }
    private static final class VolumeColumn extends View {
        private final AudioManager manager;
        private final int stream;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int current=0,max=1,min=0;
        private final float density;
        VolumeColumn(Activity a,AudioManager manager,int stream){
            super(a);this.manager=manager;this.stream=stream;
            density=a.getResources().getDisplayMetrics().density;
            refresh();
            setContentDescription("Громкость "+stream);
        }
        private void refresh(){
            try{
                max=Math.max(1,manager.getStreamMaxVolume(stream));
                min=Build.VERSION.SDK_INT>=28?manager.getStreamMinVolume(stream):0;
                current=manager.getStreamVolume(stream);
            }catch(RuntimeException ignored){}
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight();
            float margin=6*density;
            float trackLeft=margin,trackRight=w-margin;
            float top=13*density,bottom=h-24*density;
            float radius=Math.min(28*density,(trackRight-trackLeft)/2);
            RectF track=new RectF(trackLeft,top,trackRight,bottom);
            paint.setShader(new LinearGradient(0,top,0,bottom,
                0xff222a35,0xff55536a,Shader.TileMode.CLAMP));
            canvas.drawRoundRect(track,radius,radius,paint);paint.setShader(null);
            float fraction=max==min?0:(current-min)/(float)(max-min);
            canvas.save();
            canvas.clipRect(trackLeft,bottom-(bottom-top)*fraction,trackRight,bottom);
            paint.setShader(new LinearGradient(0,top,0,bottom,
                new int[]{0xff9d8cc9,0xff69c9bd,0xffddbbf4},null,Shader.TileMode.CLAMP));
            canvas.drawRoundRect(track,radius,radius,paint);paint.setShader(null);
            canvas.restore();
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.15f*density);
            paint.setColor(0x75ffffff);canvas.drawRoundRect(track,radius,radius,paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(11*density);paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.WHITE);
            canvas.drawText(Math.round(fraction*100)+"%",w/2,top+20*density,paint);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE){
                float top=13*density,bottom=getHeight()-24*density;
                float fraction=Math.max(0,Math.min(1,(bottom-e.getY())/(bottom-top)));
                int target=Math.round(min+(max-min)*fraction);
                try{manager.setStreamVolume(stream,target,0);refresh();}
                catch(SecurityException|IllegalArgumentException ex){
                    Toast.makeText(getContext(),"Изменение заблокировано системным режимом",
                        Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP){
                performClick();return true;
            }
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
