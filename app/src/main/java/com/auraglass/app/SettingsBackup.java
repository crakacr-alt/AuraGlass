package com.auraglass.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * User-controlled JSON backup through Android Storage Access Framework.
 * Never exports cached notification content, downloads, tokens, or runtime state.
 */
public final class SettingsBackup {
    public static final int EXPORT_REQUEST=971;
    public static final int IMPORT_REQUEST=972;
    private static final int SCHEMA=1;
    private static final int MAX_FILE_BYTES=64*1024;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler UI=new Handler(Looper.getMainLooper());
    private static final Set<String> KEYS=new HashSet<>(Arrays.asList(
        "weather_mode","time_mode","render_quality","frame_rate","high_fps",
        "reduce_motion","intensity","speed","glass","aurora","lightning",
        "stars","galaxy","comets","ice_enabled","snow_enabled","glass_touch",
        "touch_melt","frost_strength","frost_recovery","touch_radius",
        "snow_accumulation","haptics","parallax","parallax_intensity",
        "island_enabled","island_show_caller","island_show_notification_text",
        "pinned_apps","dock_apps","home_folders",
        "manual_city","weather_city","city_name","city",
        "weather_provider","location_mode","units","language",
        "updates_on_open","updates_notification"
    ));
    private SettingsBackup(){}

    public static void export(Activity activity){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE,"AuraGlass-settings.json");
        try{activity.startActivityForResult(intent,EXPORT_REQUEST);}
        catch(Exception e){toast(activity,"Не удалось открыть сохранение файла");}
    }
    public static void importFromFile(Activity activity){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        try{activity.startActivityForResult(intent,IMPORT_REQUEST);}
        catch(Exception e){toast(activity,"Не удалось открыть выбор файла");}
    }
    public static void onResult(Activity activity,int requestCode,int resultCode,Intent data){
        if(resultCode!=Activity.RESULT_OK||data==null||data.getData()==null)return;
        final Uri uri=data.getData();
        if(requestCode==EXPORT_REQUEST)save(activity,uri);
        if(requestCode==IMPORT_REQUEST)read(activity,uri);
    }
    private static void toast(Activity a,String message){
        Toast.makeText(a,message,Toast.LENGTH_LONG).show();
    }
    private static void save(Activity a,Uri uri){
        Map<String,?> prefs=Config.prefs(a).getAll();
        IO.execute(()->{
            try{
                JSONObject out=new JSONObject();
                out.put("format","AURA_GLASS_SETTINGS");
                out.put("schema",SCHEMA);
                JSONObject settings=new JSONObject();
                for(Map.Entry<String,?> e:prefs.entrySet()){
                    if(!KEYS.contains(e.getKey()))continue;
                    Object value=e.getValue();
                    if(value instanceof Boolean||value instanceof Integer||
                       value instanceof Long||value instanceof Float||value instanceof String){
                        settings.put(e.getKey(),value);
                    }
                }
                out.put("settings",settings);
                byte[] encoded=out.toString(2).getBytes(StandardCharsets.UTF_8);
                if(encoded.length>MAX_FILE_BYTES)throw new IllegalStateException("Слишком большой файл");
                try(OutputStream stream=a.getContentResolver().openOutputStream(uri,"wt")){
                    if(stream==null)throw new IllegalStateException("Не удалось записать документ");
                    stream.write(encoded);
                    stream.flush();
                }
                UI.post(()->toast(a,"Настройки AURA сохранены"));
            }catch(Exception e){
                UI.post(()->toast(a,"Ошибка экспорта: "+e.getMessage()));
            }
        });
    }
    private static void read(Activity a,Uri uri){
        IO.execute(()->{
            try{
                byte[] data;
                try(InputStream stream=a.getContentResolver().openInputStream(uri)){
                    if(stream==null)throw new IllegalStateException("Невозможно открыть файл");
                    java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
                    byte[] block=new byte[4096];
                    int n;
                    while((n=stream.read(block))!=-1){
                        if(bytes.size()+n>MAX_FILE_BYTES)
                            throw new IllegalStateException("Файл больше 64 КБ");
                        bytes.write(block,0,n);
                    }
                    data=bytes.toByteArray();
                }
                JSONObject doc=new JSONObject(new String(data,StandardCharsets.UTF_8));
                if(!"AURA_GLASS_SETTINGS".equals(doc.optString("format")) ||
                   doc.optInt("schema",-1)!=SCHEMA)
                    throw new IllegalStateException("Неподдерживаемый формат резервной копии");
                JSONObject settings=doc.optJSONObject("settings");
                if(settings==null)throw new IllegalStateException("Нет настроек");
                SharedPreferences current=Config.prefs(a);
                SharedPreferences.Editor replacement=current.edit();
                int count=0;
                for(java.util.Iterator<String> it=settings.keys();it.hasNext();){
                    String key=it.next();
                    if(!KEYS.contains(key))continue;
                    Object value=settings.get(key);
                    Object previous=current.getAll().get(key);
                    if(value instanceof Boolean){
                        if(previous==null||previous instanceof Boolean){
                            replacement.putBoolean(key,(Boolean)value);count++;
                        }
                    }else if(value instanceof Number){
                        double number=((Number)value).doubleValue();
                        if(!Double.isFinite(number)||Math.abs(number)>100000)continue;
                        if(previous instanceof Integer)
                            replacement.putInt(key,(int)number);
                        else if(previous instanceof Long)
                            replacement.putLong(key,(long)number);
                        else replacement.putFloat(key,(float)number);
                        count++;
                    }else if(value instanceof String && ((String)value).length()<=8192){
                        if(previous==null||previous instanceof String){
                            replacement.putString(key,(String)value);count++;
                        }
                    }
                }
                int finalCount=count;
                UI.post(()->{
                    if(a.isFinishing()||a.isDestroyed())return;
                    new AlertDialog.Builder(a)
                        .setTitle("Восстановить настройки AURA?")
                        .setMessage("Из файла будет импортировано "+finalCount+
                            " параметров. Текущие значения этих параметров изменятся. "+
                            "Системный лаунчер и обои автоматически не переключаются.")
                        .setNegativeButton("Отмена",null)
                        .setPositiveButton("Восстановить",(dialog,which)->{
                            replacement.apply();
                            toast(a,"Настройки восстановлены");
                            a.recreate();
                        }).show();
                });
            }catch(Exception e){
                UI.post(()->toast(a,"Ошибка импорта: "+e.getMessage()));
            }
        });
    }
}
