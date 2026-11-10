package com.auraglass.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.lang.ref.WeakReference;
import java.util.Locale;

/** Local, opt-in event bridge. No notifications are uploaded or persisted. */
public final class AuraEventListener extends NotificationListenerService {
    public static final class CallEvent {
        public final String key, packageName;
        public final String caller, detail;
        public final PendingIntent open, reject, mute;
        CallEvent(String key,String pkg,String caller,String detail,
                  PendingIntent open,PendingIntent reject,PendingIntent mute){
            this.key=key;this.packageName=pkg;this.caller=caller;this.detail=detail;
            this.open=open;this.reject=reject;this.mute=mute;
        }
    }
    public static final class AlertEvent {
        public final String key,source,title,description;
        public final PendingIntent open;
        public final long time;
        AlertEvent(String key,String source,String title,String description,PendingIntent open,long time){
            this.key=key;this.source=source;this.title=title;this.description=description;this.open=open;
            this.time=time;
        }
    }
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static volatile CallEvent call;
    private static volatile AlertEvent alert;
    private static WeakReference<Runnable> changeListener=new WeakReference<>(null);

    public static void observe(Runnable listener){
        changeListener=new WeakReference<>(listener);
    }
    public static void stopObserving(Runnable listener){
        if(changeListener.get()==listener)changeListener=new WeakReference<>(null);
    }
    private static void notifyUi(){
        MAIN.post(()->{
            Runnable current=changeListener.get();
            if(current!=null)current.run();
        });
    }
    public static CallEvent call(){return call;}
    public static AlertEvent alert(){return alert;}
    public static void dismissAlert(){alert=null;notifyUi();}
    public static boolean granted(Context context){
        ComponentName component=new ComponentName(context,AuraEventListener.class);
        if(Build.VERSION.SDK_INT>=27){
            NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            return manager!=null&&manager.isNotificationListenerAccessGranted(component);
        }
        String enabled=Settings.Secure.getString(context.getContentResolver(),
            "enabled_notification_listeners");
        return enabled!=null&&enabled.contains(component.flattenToString());
    }
    @Override public void onListenerConnected(){
        super.onListenerConnected();
        try{
            for(StatusBarNotification item:getActiveNotifications()){
                updateCall(item);updateAlert(item);
            }
        }catch(RuntimeException ignored){}
        notifyUi();
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn){
        updateCall(sbn);updateAlert(sbn);notifyUi();
    }
    @Override public void onNotificationRemoved(StatusBarNotification sbn){
        if(call!=null&&call.key.equals(sbn.getKey()))call=null;
        if(alert!=null&&alert.key.equals(sbn.getKey()))alert=null;
        notifyUi();
    }
    @Override public void onListenerDisconnected(){
        call=null;alert=null;notifyUi();
        super.onListenerDisconnected();
    }
    private static String bound(CharSequence s,String fallback){
        if(s==null||s.length()==0)return fallback;
        String v=s.toString().replace('\n',' ').trim();
        return v.length()>90?v.substring(0,90):v;
    }
    private void updateCall(StatusBarNotification sbn){
        if(sbn==null)return;
        Notification n=sbn.getNotification();
        if(n==null||n.extras==null||!Notification.CATEGORY_CALL.equals(n.category))return;
        String caller=bound(n.extras.getCharSequence(Notification.EXTRA_TITLE),"Вызов");
        String detail=bound(n.extras.getCharSequence(Notification.EXTRA_TEXT),"Телефон");
        PendingIntent reject=null,mute=null;
        if(n.actions!=null){
            for(Notification.Action a:n.actions){
                if(a==null||a.actionIntent==null)continue;
                String title=a.title==null?"":a.title.toString().toLowerCase(Locale.ROOT);
                if(title.contains("отклон")||title.contains("сброс")||
                   title.contains("decline")||title.contains("reject")||title.contains("hang up")){
                    reject=a.actionIntent;
                }else if(title.contains("микрофон")||title.contains("mute")){
                    mute=a.actionIntent;
                }
            }
        }
        call=new CallEvent(sbn.getKey(),sbn.getPackageName(),caller,detail,n.contentIntent,reject,mute);
    }
    private void updateAlert(StatusBarNotification sbn){
        if(sbn==null||getPackageName().equals(sbn.getPackageName()))return;
        Notification n=sbn.getNotification();
        if(n==null||n.extras==null||Notification.CATEGORY_CALL.equals(n.category)||
           Notification.CATEGORY_TRANSPORT.equals(n.category)||
           n.visibility==Notification.VISIBILITY_SECRET||
           (n.flags&Notification.FLAG_ONGOING_EVENT)!=0)return;
        if(n.contentIntent==null)return;
        String title=bound(n.extras.getCharSequence(Notification.EXTRA_TITLE),"Уведомление");
        String description=bound(n.extras.getCharSequence(Notification.EXTRA_TEXT),"");
        String source=sbn.getPackageName();
        try{
            android.content.pm.PackageManager pm=getPackageManager();
            source=pm.getApplicationLabel(pm.getApplicationInfo(source,0)).toString();
        }catch(Exception ignored){}
        AlertEvent next=new AlertEvent(sbn.getKey(),source,title,description,n.contentIntent,sbn.getPostTime());
        if(alert==null||next.time>=alert.time)alert=next;
    }
    public static boolean execute(PendingIntent action){
        if(action==null)return false;
        try{action.send();return true;}
        catch(PendingIntent.CanceledException|RuntimeException ignored){return false;}
    }
}
