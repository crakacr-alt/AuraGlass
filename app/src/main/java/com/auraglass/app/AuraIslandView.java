package com.auraglass.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.animation.ValueAnimator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/**
 * AURA Island v1: launcher-contained capsule.
 * It does not overlay other apps or intercept the notch/system status bar.
 */
public final class AuraIslandView extends FrameLayout {
    private static final int COLLAPSED=57;
    private final Activity host;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final GestureDetector gestures;
    private final AuraSpectrum spectrum;
    private final AuraWaveView smallWave,bigWave;
    private final Runnable eventCallback=()->post(this::refresh);
    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            if(!active)return;
            refresh();
            handler.postDelayed(this,900);
        }
    };
    private final LinearLayout detail;
    private final LinearLayout mediaBody,callBody,alertBody,otherBody;
    private final TextView compactTitle,compactCaption,title,artist,appName,position,
        duration,callTitle,callDetails,audioStatus,otherLabel,alertTitle,alertDetails;
    private final ImageView cover;
    private final SeekBar seek;
    private final Button play,next,previous,openPlayer,openCall,decline,mute,allowAudio;
    private AuraMedia.Track playing;
    private AuraEventListener.CallEvent currentCall;
    private AuraEventListener.AlertEvent currentAlert;
    private boolean active,expanded,seekDragging;
    private String state="idle";
    private ValueAnimator sizeAnimation;

    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,boolean bold){
        TextView v=new TextView(host);
        v.setText(value);v.setTextColor(0xfff7f5ff);v.setTextSize(size);
        v.setSingleLine(true);v.setEllipsize(TextUtils.TruncateAt.END);
        if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        return v;
    }
    private LinearLayout column(){
        LinearLayout view=new LinearLayout(host);
        view.setOrientation(LinearLayout.VERTICAL);return view;
    }
    private Button button(String value,Runnable action){
        Button b=new Button(host);
        b.setAllCaps(false);b.setTextSize(12);b.setTextColor(Color.WHITE);
        b.setText(value);
        GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0x8a80799e,0x596d739c});
        shape.setCornerRadius(dp(15));
        shape.setStroke(dp(1),0x4dffffff);
        b.setBackground(shape);
        b.setOnClickListener(v->action.run());
        return b;
    }
    private void addAction(LinearLayout row,Button button,int weight){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(45),weight);
        p.leftMargin=dp(3);p.rightMargin=dp(3);row.addView(button,p);
    }
    private GradientDrawable capsuleBackground(){
        GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xed222b42,0xef35324d,0xe8293a4a});
        shape.setCornerRadius(dp(28));
        shape.setStroke(dp(1),0xb3d7d7f9);
        return shape;
    }
    public AuraIslandView(Activity activity){
        super(activity);
        host=activity;setBackground(capsuleBackground());
        setElevation(dp(13));setClipToOutline(true);
        LinearLayout panel=column();panel.setPadding(dp(15),dp(3),dp(15),dp(9));
        addView(panel,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(5),0,dp(4),0);
        TextView dot=text("◈",24,true);dot.setTextColor(0xffa7e3e1);
        header.addView(dot,new LinearLayout.LayoutParams(dp(34),dp(COLLAPSED-5)));
        LinearLayout captions=column();captions.setGravity(Gravity.CENTER_VERTICAL);
        compactTitle=text("AURA Island",14,true);captions.addView(compactTitle);
        compactCaption=text("Уведомления · музыка · звук",10,false);
        compactCaption.setTextColor(0xffb9c1d6);captions.addView(compactCaption);
        header.addView(captions,new LinearLayout.LayoutParams(0,dp(COLLAPSED-5),1));
        smallWave=new AuraWaveView(activity);
        header.addView(smallWave,new LinearLayout.LayoutParams(dp(52),dp(28)));
        panel.addView(header,new LinearLayout.LayoutParams(-1,dp(COLLAPSED-4)));
        detail=column();
        detail.setVisibility(GONE);detail.setAlpha(0);
        panel.addView(detail,new LinearLayout.LayoutParams(-1,-2));
        gestures=new GestureDetector(activity,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onSingleTapUp(MotionEvent e){toggle();return true;}
            @Override public void onLongPress(MotionEvent e){settings();}
            @Override public boolean onFling(MotionEvent e1,MotionEvent e2,float vx,float vy){
                if(e2==null||e1==null)return false;
                float diff=e2.getY()-e1.getY();
                if(diff>dp(36)){setExpanded(true);return true;}
                if(diff<-dp(36)){setExpanded(false);return true;}
                if(Math.abs(e2.getX()-e1.getX())>dp(60)&&"alert".equals(state)){
                    AuraEventListener.dismissAlert();setExpanded(false);return true;
                }
                return false;
            }
        });
        header.setOnTouchListener((v,event)->{gestures.onTouchEvent(event);return true;});

        mediaBody=column();
        LinearLayout meta=new LinearLayout(activity);meta.setGravity(Gravity.CENTER_VERTICAL);
        cover=new ImageView(activity);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable coverBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xff9074bc,0xff60b0a9});
        coverBg.setCornerRadius(dp(15));cover.setBackground(coverBg);cover.setClipToOutline(true);
        LinearLayout.LayoutParams artParams=new LinearLayout.LayoutParams(dp(63),dp(63));
        artParams.rightMargin=dp(12);meta.addView(cover,artParams);
        LinearLayout textStack=column();textStack.setGravity(Gravity.CENTER_VERTICAL);
        appName=text("Музыка",11,false);appName.setTextColor(0xffb8d9eb);
        textStack.addView(appName);
        title=text("Без названия",18,true);textStack.addView(title);
        artist=text("Исполнитель",13,false);artist.setTextColor(0xffd4d3e7);
        textStack.addView(artist);
        meta.addView(textStack,new LinearLayout.LayoutParams(0,dp(68),1));
        mediaBody.addView(meta,new LinearLayout.LayoutParams(-1,dp(70)));
        bigWave=new AuraWaveView(activity);
        LinearLayout.LayoutParams wpar=new LinearLayout.LayoutParams(-1,dp(29));
        wpar.topMargin=dp(2);mediaBody.addView(bigWave,wpar);
        LinearLayout seekLine=new LinearLayout(activity);seekLine.setGravity(Gravity.CENTER_VERTICAL);
        position=text("0:00",10,false);seekLine.addView(position,new LinearLayout.LayoutParams(dp(37),dp(25)));
        seek=new SeekBar(activity);seek.setMax(1000);
        seekLine.addView(seek,new LinearLayout.LayoutParams(0,dp(38),1));
        duration=text("0:00",10,false);duration.setGravity(Gravity.END);
        seekLine.addView(duration,new LinearLayout.LayoutParams(dp(40),dp(25)));
        mediaBody.addView(seekLine,new LinearLayout.LayoutParams(-1,dp(42)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                if(fromUser&&playing!=null&&playing.durationMs>0)
                    position.setText(time(playing.durationMs*progress/1000));
            }
            @Override public void onStartTrackingTouch(SeekBar bar){seekDragging=true;}
            @Override public void onStopTrackingTouch(SeekBar bar){
                seekDragging=false;
                if(playing!=null&&playing.canSeek)
                    AuraMedia.seek(playing,playing.durationMs*bar.getProgress()/1000);
            }
        });
        LinearLayout playback=new LinearLayout(activity);
        previous=button("|◀",()->AuraMedia.previous(playing));
        play=button("▶",()->{AuraMedia.toggle(playing);postDelayed(this::refresh,250);});
        next=button("▶|",()->AuraMedia.next(playing));
        openPlayer=button("Приложение",()->{
            if(!AuraMedia.openPlayer(host,playing))
                Toast.makeText(host,"Приложение недоступно",Toast.LENGTH_SHORT).show();
        });
        addAction(playback,previous,1);addAction(playback,play,1);
        addAction(playback,next,1);addAction(playback,openPlayer,2);
        mediaBody.addView(playback,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout extras=new LinearLayout(activity);
        Button mixer=button("≋   Микшер",()->AuraAudioMixer.show(host));
        addAction(extras,mixer,1);
        allowAudio=button("Включить звуковую волну",this::requestAudio);
        addAction(extras,allowAudio,2);
        mediaBody.addView(extras,new LinearLayout.LayoutParams(-1,dp(46)));
        audioStatus=text("Для настоящей волны нужен отдельный доступ к аудиоанализу.",10,false);
        audioStatus.setTextColor(0xffc2c8dd);
        audioStatus.setGravity(Gravity.CENTER);
        mediaBody.addView(audioStatus,new LinearLayout.LayoutParams(-1,dp(19)));
        detail.addView(mediaBody,new LinearLayout.LayoutParams(-1,-2));

        callBody=column();
        callTitle=text("Входящий вызов",20,true);
        callTitle.setGravity(Gravity.CENTER);
        callBody.addView(callTitle,new LinearLayout.LayoutParams(-1,dp(39)));
        callDetails=text("Данные скрыты",13,false);
        callDetails.setGravity(Gravity.CENTER);
        callBody.addView(callDetails,new LinearLayout.LayoutParams(-1,dp(34)));
        LinearLayout callActions=new LinearLayout(activity);
        openCall=button("☎ Открыть",this::showCall);
        mute=button("Микрофон",()->callAction(currentCall==null?null:currentCall.mute));
        decline=button("Сбросить",()->callAction(currentCall==null?null:currentCall.reject));
        addAction(callActions,openCall,2);addAction(callActions,mute,2);
        addAction(callActions,decline,2);
        callBody.addView(callActions,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView notice=text("Кнопки доступны только при поддержке Samsung Phone.",11,false);
        notice.setTextColor(0xffbcc6d9);notice.setGravity(Gravity.CENTER);
        callBody.addView(notice,new LinearLayout.LayoutParams(-1,dp(32)));
        detail.addView(callBody,new LinearLayout.LayoutParams(-1,-2));

        alertBody=column();
        alertTitle=text("Новое уведомление",20,true);
        alertBody.addView(alertTitle,new LinearLayout.LayoutParams(-1,dp(40)));
        alertDetails=text("Содержание скрыто",13,false);
        alertDetails.setSingleLine(false);
        alertDetails.setTextColor(0xffc6d3e2);
        alertBody.addView(alertDetails,new LinearLayout.LayoutParams(-1,dp(62)));
        LinearLayout alertActions=new LinearLayout(activity);
        addAction(alertActions,button("Открыть",()->{
            if(currentAlert!=null)AuraEventListener.execute(currentAlert.open);
        }),1);
        addAction(alertActions,button("Скрыть в AURA",AuraEventListener::dismissAlert),1);
        alertBody.addView(alertActions,new LinearLayout.LayoutParams(-1,dp(50)));
        TextView privacy=text("Текст скрыт по умолчанию; системное уведомление сохраняется.",11,false);
        privacy.setTextColor(0xffbcc7d7);privacy.setSingleLine(false);
        alertBody.addView(privacy,new LinearLayout.LayoutParams(-1,dp(35)));
        detail.addView(alertBody,new LinearLayout.LayoutParams(-1,-2));

        otherBody=column();
        otherLabel=text("Включите доступ к уведомлениям для музыки и событий.",13,false);
        otherLabel.setSingleLine(false);otherLabel.setGravity(Gravity.CENTER);
        otherBody.addView(otherLabel,new LinearLayout.LayoutParams(-1,dp(54)));
        otherBody.addView(button("Разрешить уведомления",this::notifications),
            new LinearLayout.LayoutParams(-1,dp(47)));
        LinearLayout shortcuts=new LinearLayout(activity);
        addAction(shortcuts,button("Звук",()->AuraAudioMixer.show(host)),1);
        addAction(shortcuts,button("Настройки",this::settings),1);
        otherBody.addView(shortcuts,new LinearLayout.LayoutParams(-1,dp(48)));
        detail.addView(otherBody,new LinearLayout.LayoutParams(-1,-2));

        spectrum=new AuraSpectrum(activity,levels->{
            smallWave.setBands(levels);bigWave.setBands(levels);
        });
        showMode("idle");
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription("AURA Island. Нажмите для раскрытия.");
    }

    private static String time(long ms){
        long seconds=Math.max(0,ms/1000);
        return String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
    }
    private void notifications(){
        try{host.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}
        catch(RuntimeException ignored){
            Toast.makeText(host,"Откройте настройки доступа к уведомлениям Android",Toast.LENGTH_LONG).show();
        }
    }
    private void settings(){
        final String[] choices={"Доступ к уведомлениям","Показывать имя/номер звонящего",
            "Показывать текст уведомлений","Звук AURA","Открыть редактор AURA","Отключить островок"};
        new AlertDialog.Builder(host).setTitle("AURA Island")
            .setItems(choices,(dialog,which)->{
                switch(which){
                    case 0:notifications();break;
                    case 1:{
                        boolean show=Config.bool(host,"island_show_caller",false);
                        Config.put(host,"island_show_caller",!show);
                        Toast.makeText(host,!show?"Имя/номер будут показаны":"Данные звонящего скрыты",Toast.LENGTH_SHORT).show();
                        refresh();break;
                    }
                    case 2:{
                        boolean show=Config.bool(host,"island_show_notification_text",false);
                        Config.put(host,"island_show_notification_text",!show);
                        Toast.makeText(host,!show?"Текст уведомлений включён":"Текст уведомлений скрыт",Toast.LENGTH_SHORT).show();
                        refresh();break;
                    }
                    case 3:AuraAudioMixer.show(host);break;
                    case 4:host.startActivity(new Intent(host,MainActivity.class));break;
                    case 5:{
                        new AlertDialog.Builder(host).setTitle("Отключить островок?")
                            .setMessage("Чтобы снова включить, откройте редактор AURA.")
                            .setNegativeButton("Отмена",null)
                            .setPositiveButton("Отключить",(d,w)->{
                                Config.put(host,"island_enabled",false);
                                setVisibility(GONE);
                            }).show();
                        break;
                    }
                }
            }).show();
    }
    private void requestAudio(){
        if(host.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){
            if(!spectrum.start())audioStatus.setText("Аудиоанализ недоступен для данного устройства/источника.");
            return;
        }
        new AlertDialog.Builder(host).setTitle("Настоящая звуковая волна")
            .setMessage("Android требует разрешение на аудиоанализ для Visualizer. AURA не сохраняет и не передаёт звук. На некоторых устройствах анализ выходящего звука недоступен.")
            .setNegativeButton("Не сейчас",null)
            .setPositiveButton("Разрешить",(d,w)->host.requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO},714))
            .show();
    }
    private void showCall(){
        AuraEventListener.CallEvent call=currentCall;
        if(call==null)return;
        if(!AuraEventListener.execute(call.open))
            Toast.makeText(host,"Откройте приложение «Телефон»",Toast.LENGTH_LONG).show();
    }
    private void callAction(PendingIntent action){
        if(!AuraEventListener.execute(action))
            Toast.makeText(host,"Samsung Phone не предоставил это действие",Toast.LENGTH_SHORT).show();
    }
    private void showMode(String next){
        state=next;
        mediaBody.setVisibility("media".equals(next)?VISIBLE:GONE);
        callBody.setVisibility("call".equals(next)?VISIBLE:GONE);
        alertBody.setVisibility("alert".equals(next)?VISIBLE:GONE);
        otherBody.setVisibility("idle".equals(next)?VISIBLE:GONE);
        if(expanded)animateHeight(dp(targetHeight()),false);
    }
    private int targetHeight(){
        return "media".equals(state)?339:"call".equals(state)?224:"alert".equals(state)?257:219;
    }
    private void toggle(){
        setExpanded(!expanded);
    }
    public void setExpanded(boolean yes){
        if(expanded==yes)return;
        expanded=yes;
        if(sizeAnimation!=null)sizeAnimation.cancel();
        if(yes){detail.setVisibility(VISIBLE);detail.setAlpha(0);}
        animateHeight(dp(yes?targetHeight():COLLAPSED),true);
        setContentDescription(yes?"AURA Island. Свайп вверх чтобы свернуть.":
            "AURA Island. Нажмите для раскрытия.");
    }
    private void animateHeight(int target,boolean animate){
        ViewGroup.LayoutParams params=getLayoutParams();
        if(params==null)return;
        int from=getHeight()>0?getHeight():dp(COLLAPSED);
        if(!animate||Config.bool(host,"reduce_motion",false)){
            params.height=target;setLayoutParams(params);
            detail.setAlpha(expanded?1:0);
            detail.setVisibility(expanded?VISIBLE:GONE);
            return;
        }
        sizeAnimation=ValueAnimator.ofInt(from,target);
        sizeAnimation.setDuration(expanded?350:265);
        sizeAnimation.setInterpolator(new PathInterpolator(.21f,.82f,.18f,1));
        sizeAnimation.addUpdateListener(animation->{
            ViewGroup.LayoutParams lp=getLayoutParams();
            if(lp==null)return;
            lp.height=(int)animation.getAnimatedValue();setLayoutParams(lp);
            float progress=(lp.height-dp(COLLAPSED))/(float)Math.max(1,dp(targetHeight()-COLLAPSED));
            detail.setAlpha(Math.max(0,Math.min(1,progress)));
        });
        sizeAnimation.addListener(new android.animation.AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(android.animation.Animator a){
                if(!expanded)detail.setVisibility(GONE);
                else detail.setAlpha(1);
            }
        });
        sizeAnimation.start();
    }
    private void refresh(){
        if(!active)return;
        AuraEventListener.CallEvent call=AuraEventListener.call();
        AuraEventListener.AlertEvent alert=AuraEventListener.alert();
        AuraMedia.Track track=AuraMedia.current(host);
        currentCall=call;currentAlert=alert;playing=track;
        String mode=call!=null?"call":track!=null&&track.playing?"media":alert!=null?"alert":track!=null?"media":"idle";
        if(!mode.equals(state))showMode(mode);
        if("call".equals(mode)){
            smallWave.stopAudio();bigWave.stopAudio();spectrum.stop();
            compactTitle.setText("Входящий вызов");
            compactCaption.setText("Откройте для управления звонком");
            boolean reveal=Config.bool(host,"island_show_caller",false);
            callTitle.setText(reveal?call.caller:"Входящий вызов");
            callDetails.setText(reveal?call.detail:"Имя и номер скрыты");
            mute.setEnabled(call.mute!=null);
            decline.setEnabled(call.reject!=null);
            openCall.setEnabled(call.open!=null);
        }else if("media".equals(mode)){
            compactTitle.setText(track.title);
            compactCaption.setText(track.playing?"♫ "+track.artist:"Приостановлено · "+track.artist);
            title.setText(track.title);artist.setText(track.artist);
            appName.setText("♫  "+track.application);
            if(track.art!=null)cover.setImageBitmap(track.art);
            else cover.setImageResource(android.R.drawable.ic_media_play);
            play.setText(track.playing?"Ⅱ":"▶");
            play.setEnabled(track.canToggle);
            next.setEnabled(track.canSkipNext);
            previous.setEnabled(track.canSkipPrev);
            seek.setEnabled(track.canSeek);
            if(!seekDragging){
                seek.setProgress(track.durationMs>0?(int)Math.min(1000,
                    track.positionMs*1000/track.durationMs):0);
                position.setText(time(track.positionMs));
            }
            duration.setText(time(track.durationMs));
            boolean allowed=host.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
            allowAudio.setVisibility(allowed?GONE:VISIBLE);
            if(allowed&&track.playing){
                boolean captured=spectrum.start();
                audioStatus.setText(captured?"Реальная FFT-волна выходящего звука":
                    "Аудиоанализ не поддерживается устройством");
            }else{
                spectrum.stop();smallWave.stopAudio();bigWave.stopAudio();
                audioStatus.setText(allowed?"Воспроизведение остановлено":"Включите аудиоанализ для настоящей звуковой волны");
            }
        }else if("alert".equals(mode)){
            spectrum.stop();smallWave.stopAudio();bigWave.stopAudio();
            compactTitle.setText(alert.source);
            compactCaption.setText("Новое уведомление");
            boolean reveal=Config.bool(host,"island_show_notification_text",false);
            alertTitle.setText(reveal?alert.title:"Уведомление · "+alert.source);
            alertDetails.setText(reveal?alert.description:"Содержимое скрыто в настройках AURA");
        }else{
            spectrum.stop();smallWave.stopAudio();bigWave.stopAudio();
            compactTitle.setText("AURA Island");
            compactCaption.setText(AuraEventListener.granted(host)?
                "Нет активной музыки или звонков":"Включите доступ к уведомлениям");
            otherLabel.setText(AuraEventListener.granted(host)?
                "Музыка появится здесь автоматически, когда приложение поделится MediaSession.":
                "Разрешите уведомления, чтобы отображать музыку и звонки. Тексты скрыты по умолчанию.");
        }
    }
    public void resume(){
        if(active)return;
        active=true;
        AuraEventListener.observe(eventCallback);
        handler.removeCallbacks(ticker);
        ticker.run();
    }
    public void pause(){
        active=false;
        handler.removeCallbacks(ticker);
        AuraEventListener.stopObserving(eventCallback);
        spectrum.stop();smallWave.stopAudio();bigWave.stopAudio();
    }
    @Override protected void onDetachedFromWindow(){
        pause();
        if(sizeAnimation!=null)sizeAnimation.cancel();
        super.onDetachedFromWindow();
    }
}
