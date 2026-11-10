package com.auraglass.app;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * User-initiated GitHub-only updates. No scheduled job, no boot receiver,
 * no background network polling. Stable releases only, on application entry.
 * Never installs APKs silently or bypasses Android's source permissions.
 */
public final class UpdateCenter {
    private static final String RELEASE_API =
        "https://api.github.com/repos/crakacr-alt/AuraGlass/releases/latest";
    private static final String RELEASE_PAGE =
        "https://github.com/crakacr-alt/AuraGlass/releases";
    private static final String CHANNEL = "aura_updates";
    private static final int NOTIFICATION_ID = 4127;
    private static final Pattern TAG = Pattern.compile("v?[0-9]+\\.[0-9]+\\.[0-9]+");
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{64}");
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final long MAX_APK_SIZE = 150L * 1024 * 1024;

    public interface Result {
        void onResult(Release release, String explanation);
    }
    public interface Status {
        void onStatus(String text, boolean readyToInstall);
    }
    public static final class Release {
        public final String tag, downloadUrl, sha256, name;
        public final long size;
        Release(String tag, String url, String sha256, long size, String name) {
            this.tag=tag;this.downloadUrl=url;this.sha256=sha256;this.size=size;this.name=name;
        }
    }
    private UpdateCenter(){}

    private static long[] version(String raw) {
        if(raw==null)return null;
        String clean=raw.trim();
        if(clean.startsWith("v"))clean=clean.substring(1);
        int suffix=clean.indexOf('-');
        if(suffix>=0)clean=clean.substring(0,suffix);
        if(!TAG.matcher(clean).matches()&& !TAG.matcher("v"+clean).matches())return null;
        String[] items=clean.split("\\.");
        try{return new long[]{Long.parseLong(items[0]),Long.parseLong(items[1]),
            Long.parseLong(items[2])};}
        catch(NumberFormatException ignored){return null;}
    }
    private static boolean newer(String candidate,String installed) {
        long[] c=version(candidate),i=version(installed);
        if(c==null||i==null)return false;
        for(int k=0;k<3;k++){
            if(c[k]>i[k])return true;
            if(c[k]<i[k])return false;
        }
        return false;
    }
    private static String installedVersion(Context ctx){
        try{
            String name=ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),0).versionName;
            return name==null?"0.0.0":name;
        }catch(Exception ignored){return "0.0.0";}
    }
    private static String readResponse(HttpURLConnection con)throws Exception{
        StringBuilder text=new StringBuilder();
        try(InputStream in=con.getInputStream()){
            byte[] buffer=new byte[8192];
            for(int n;(n=in.read(buffer))!=-1;){
                text.append(new String(buffer,0,n,java.nio.charset.StandardCharsets.UTF_8));
                if(text.length()>200000)throw new java.io.IOException("Release response too large");
            }
        }
        return text.toString();
    }
    public static void checkOnEntry(Activity activity,Result callback){
        final Context app=activity.getApplicationContext();
        NETWORK.execute(()->{
            Release found=null;
            String info=null;
            HttpURLConnection conn=null;
            try{
                conn=(HttpURLConnection)new URL(RELEASE_API).openConnection();
                conn.setConnectTimeout(7000);conn.setReadTimeout(8000);
                conn.setRequestProperty("Accept","application/vnd.github+json");
                conn.setRequestProperty("User-Agent","AuraGlass-Android-UpdateChecker");
                int response=conn.getResponseCode();
                if(response==404){
                    info="Стабильный релиз пока не опубликован";
                }else if(response!=200){
                    info="GitHub временно недоступен (HTTP "+response+")";
                }else{
                    JSONObject data=new JSONObject(readResponse(conn));
                    if(!data.optBoolean("draft")&&!data.optBoolean("prerelease")){
                        String tag=data.optString("tag_name","");
                        if(newer(tag,installedVersion(app))){
                            JSONArray assets=data.optJSONArray("assets");
                            if(assets!=null)for(int k=0;k<assets.length();k++){
                                JSONObject asset=assets.optJSONObject(k);
                                if(asset==null)continue;
                                String name=asset.optString("name","");
                                String url=asset.optString("browser_download_url","");
                                String digest=asset.optString("digest","");
                                long size=asset.optLong("size",0);
                                if(!name.endsWith(".apk")||size<10000||size>MAX_APK_SIZE)continue;
                                if(!url.startsWith("https://github.com/crakacr-alt/AuraGlass/releases/download/"))continue;
                                if(!digest.startsWith("sha256:")||
                                    !HEX.matcher(digest.substring(7)).matches())continue;
                                found=new Release(tag,url,digest.substring(7).toLowerCase(Locale.ROOT),
                                    size,name);
                                break;
                            }
                            if(found==null)info="Новая версия есть, но безопасный APK с SHA-256 пока не опубликован";
                        }else info="Последняя стабильная версия уже установлена";
                    }
                }
            }catch(Exception err){info="Не удалось проверить GitHub — проверьте соединение";}
            finally{if(conn!=null)conn.disconnect();}
            final Release result=found;
            final String resultText=info;
            UI.post(()->{
                if(activity.isFinishing()||activity.isDestroyed())return;
                callback.onResult(result,resultText);
                if(result!=null)showOptionalNotification(app,result);
            });
        });
    }

    private static void showOptionalNotification(Context ctx,Release release){
        if(!Config.bool(ctx,"updates_notification",false))return;
        // One update notification per stable version, even if AURA is reopened often.
        if(release.tag.equals(Config.str(ctx,"updates_last_notification_tag","")))return;
        if(Build.VERSION.SDK_INT>=33&&ctx.checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel channel=new NotificationChannel(CHANNEL,"Обновления AURA",
                NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Только при открытии AURA, когда опубликован новый стабильный релиз");
            nm.createNotificationChannel(channel);
        }
        Intent intent=new Intent(ctx,SetupActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(ctx,29,intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?
            new Notification.Builder(ctx,CHANNEL):new Notification.Builder(ctx);
        Notification note=builder
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("AURA GLASS: доступна версия "+release.tag)
            .setContentText("Откройте AURA, чтобы скачать и установить обновление")
            .setContentIntent(open).setAutoCancel(true).build();
        nm.notify(NOTIFICATION_ID,note);
        Config.put(ctx,"updates_last_notification_tag",release.tag);
    }

    private static String filename(String tag){
        if(tag==null||!TAG.matcher(tag).matches())return null;
        return "AuraGlass-update-"+tag+".apk";
    }

    /** Only a direct button click may start a download. */
    public static boolean beginDownload(Activity activity,Release release){
        if(release==null||filename(release.tag)==null)return false;
        try{
            File dir=activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if(dir==null)throw new IllegalStateException("Нет каталога загрузок");
            String fileName=filename(release.tag);
            File target=new File(dir,fileName);
            if(target.exists()&&!target.delete())
                throw new IllegalStateException("Не удалось заменить старый файл");
            DownloadManager dm=(DownloadManager)activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if(dm==null)throw new IllegalStateException("Служба загрузки Android недоступна");
            DownloadManager.Request request=new DownloadManager.Request(Uri.parse(release.downloadUrl));
            request.setTitle("AURA GLASS "+release.tag);
            request.setDescription("Подписанный APK из официального GitHub Releases");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setDestinationInExternalFilesDir(activity,Environment.DIRECTORY_DOWNLOADS,fileName);
            long id=dm.enqueue(request);
            Config.prefs(activity).edit()
                .putLong("update_download_id",id)
                .putString("update_download_file",fileName)
                .putString("update_download_digest",release.sha256)
                .putString("update_download_tag",release.tag)
                .putLong("update_download_size",release.size)
                .apply();
            return true;
        }catch(Exception e){
            Toast.makeText(activity,"Не удалось начать загрузку: "+e.getMessage(),Toast.LENGTH_LONG).show();
            return false;
        }
    }

    public static void downloadStatus(Activity activity,Status callback){
        long id=Config.prefs(activity).getLong("update_download_id",-1);
        if(id<=0){callback.onStatus("",false);return;}
        DownloadManager dm=(DownloadManager)activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if(dm==null){callback.onStatus("Служба загрузки недоступна",false);return;}
        try(Cursor cursor=dm.query(new DownloadManager.Query().setFilterById(id))){
            if(cursor==null||!cursor.moveToFirst()){
                callback.onStatus("Загрузка не найдена",false);return;
            }
            int status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if(status==DownloadManager.STATUS_SUCCESSFUL)
                callback.onStatus("APK загружен. Проверьте подпись и установите обновление.",true);
            else if(status==DownloadManager.STATUS_FAILED)
                callback.onStatus("Загрузка не удалась. Нажмите «Скачать» ещё раз.",false);
            else callback.onStatus("APK загружается через Android DownloadManager…",false);
        }catch(Exception e){callback.onStatus("Не удалось получить статус загрузки",false);}
    }

    private static Signature[] signers(PackageInfo pkg){
        if(pkg==null)return null;
        if(Build.VERSION.SDK_INT>=28){
            if(pkg.signingInfo==null)return null;
            return pkg.signingInfo.getApkContentsSigners();
        }
        return pkg.signatures;
    }
    private static long versionCode(PackageInfo pkg){
        return Build.VERSION.SDK_INT>=28?pkg.getLongVersionCode():pkg.versionCode;
    }
    private static String hash(File file)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(FileInputStream stream=new FileInputStream(file)){
            byte[] buf=new byte[32768];
            int n;
            while((n=stream.read(buf))>=0)if(n>0)digest.update(buf,0,n);
        }
        StringBuilder result=new StringBuilder();
        for(byte b:digest.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));
        return result.toString();
    }
    public static void installDownloaded(Activity activity,Status callback){
        String name=Config.str(activity,"update_download_file","");
        String expected=Config.str(activity,"update_download_digest","");
        String tag=Config.str(activity,"update_download_tag","");
        long size=Config.prefs(activity).getLong("update_download_size",0);
        if(!name.equals(filename(tag))||!HEX.matcher(expected).matches()){
            callback.onStatus("Нет подтверждённой загрузки. Проверьте обновления снова.",false);return;
        }
        File dir=activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if(dir==null){callback.onStatus("Нет доступа к каталогу загрузок",false);return;}
        File apk=new File(dir,name);
        callback.onStatus("Проверяю APK, SHA-256 и сертификат подписи…",false);
        NETWORK.execute(()->{
            String err=null;
            try{
                if(!apk.isFile()||apk.length()<10000||apk.length()!=size||
                    apk.length()>MAX_APK_SIZE)throw new IllegalStateException("Неполная загрузка");
                if(!expected.equalsIgnoreCase(hash(apk)))
                    throw new SecurityException("Контрольная сумма не совпадает");
                PackageManager pm=activity.getPackageManager();
                int flags=Build.VERSION.SDK_INT>=28?
                    PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
                PackageInfo archive=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags);
                PackageInfo installed=pm.getPackageInfo(activity.getPackageName(),flags);
                if(archive==null||!activity.getPackageName().equals(archive.packageName))
                    throw new SecurityException("APK не принадлежит AURA GLASS");
                Signature[] oldKeys=signers(installed),newKeys=signers(archive);
                if(oldKeys==null||newKeys==null||oldKeys.length==0||
                    !Arrays.equals(oldKeys,newKeys))
                    throw new SecurityException("Подпись отличается от установленной версии. Обновление поверх неё невозможно");
                if(versionCode(archive)<=versionCode(installed))
                    throw new SecurityException("Версия APK не новее установленной");
            }catch(Exception e){err=e.getMessage()==null?"Проверка APK не пройдена":e.getMessage();}
            final String failed=err;
            UI.post(()->{
                if(activity.isFinishing()||activity.isDestroyed())return;
                if(failed!=null){
                    callback.onStatus(failed,false);
                    return;
                }
                if(Build.VERSION.SDK_INT>=26&&
                    !activity.getPackageManager().canRequestPackageInstalls()){
                    callback.onStatus("Android требует разрешить установку из AURA. Затем нажмите «Установить» снова.",true);
                    try{
                        Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:"+activity.getPackageName()));
                        activity.startActivity(settings);
                    }catch(Exception ignored){}
                    return;
                }
                try{
                    Uri content=Uri.parse("content://"+activity.getPackageName()+
                        ".updates/"+name);
                    Intent install=new Intent(Intent.ACTION_INSTALL_PACKAGE);
                    install.setDataAndType(content,"application/vnd.android.package-archive");
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    activity.startActivity(install);
                    callback.onStatus("Подтвердите установку в системном окне Android",true);
                }catch(Exception e){
                    callback.onStatus("Не удалось открыть установщик: "+e.getMessage(),true);
                }
            });
        });
    }

    public static void openReleases(Activity activity){
        try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(RELEASE_PAGE)));}
        catch(Exception ignored){}
    }
}
