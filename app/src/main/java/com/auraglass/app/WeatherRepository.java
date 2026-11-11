package com.auraglass.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONArray;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WeatherRepository {
    public interface Callback { void complete(boolean ok,String message); }
    private static final ExecutorService EXEC=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final long TTL=40L*60*1000;
    private WeatherRepository(){}
    public static final class Snapshot {
        public boolean available;
        public String city;
        public int temp,code,max,min,humidity,wind;
        public long updated;
        // Actual Open-Meteo UTC sunrise and sunset instants for up to four days.
        // Cached with weather; no additional background network polling.
        public String solarRises="",solarSets="",solarTimezone="";
        public String condition(){
            if(!available)return "Выберите город";
            if(code==0)return "Ясно";
            if(code<=2)return "Переменная облачность";
            if(code==3)return "Облачно";
            if(code<=48)return "Туман";
            if(code>=95)return "Гроза";
            if((code>=71&&code<=77)||(code>=85&&code<=86))return "Снег";
            if((code>=51&&code<=67)||(code>=80&&code<=82))return "Дождь";
            return "Облачно";
        }
        public String symbol(){
            if(!available)return "☁";
            if(code<=1)return "☀";
            if(code<=3)return "☁";
            if(code<=48)return "≋";
            if(code>=95)return "ϟ";
            if((code>=71&&code<=77)||(code>=85&&code<=86))return "❄";
            if((code>=51&&code<=67)||(code>=80&&code<=82))return "☂";
            return "☁";
        }
    }
    public static Snapshot read(Context c){
        SharedPreferences p=Config.prefs(c);
        Snapshot s=new Snapshot();
        s.updated=p.getLong("weather_updated",0L);
        s.available=s.updated>0;
        s.city=p.getString("city","Мой город");
        s.temp=p.getInt("weather_temp",0);
        s.code=p.getInt("weather_code",3);
        s.max=p.getInt("weather_max",0);
        s.min=p.getInt("weather_min",0);
        s.humidity=p.getInt("weather_humidity",0);
        s.wind=p.getInt("weather_wind",0);
        s.solarRises=p.getString("solar_rises","");
        s.solarSets=p.getString("solar_sets","");
        s.solarTimezone=p.getString("solar_timezone","");
        return s;
    }
    private static void report(Callback cb,boolean ok,String msg){if(cb!=null)MAIN.post(()->cb.complete(ok,msg));}
    private static String fetch(String url) throws Exception {
        HttpURLConnection con=(HttpURLConnection)new URL(url).openConnection();
        con.setConnectTimeout(9000);con.setReadTimeout(11000);
        con.setRequestProperty("Accept","application/json");
        con.setRequestProperty("User-Agent","AuraGlass/0.1");
        try{
            if(con.getResponseCode()!=200)throw new java.io.IOException("HTTP "+con.getResponseCode());
            StringBuilder sb=new StringBuilder();
            try(BufferedReader r=new BufferedReader(new InputStreamReader(con.getInputStream(),StandardCharsets.UTF_8))){
                String line;while((line=r.readLine())!=null){
                    sb.append(line);if(sb.length()>100000)throw new java.io.IOException("Weather response too large");
                }
            }
            return sb.toString();
        }finally{con.disconnect();}
    }
    public static void setCity(Context c,String query,Callback cb){
        Context app=c.getApplicationContext();
        EXEC.execute(()->{
            try{
                String q=query.trim();
                if(q.length()<2)throw new IllegalArgumentException("Введите город");
                JSONObject data=new JSONObject(fetch("https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(q,"UTF-8")+"&count=1&language=ru&format=json"));
                JSONArray locations=data.optJSONArray("results");
                if(locations==null||locations.length()==0)throw new IllegalArgumentException("Город не найден");
                JSONObject city=locations.getJSONObject(0);
                storeCoords(app,city.getDouble("latitude"),city.getDouble("longitude"),city.optString("name",q));
                load(app,true,cb);
            }catch(Exception e){Log.w("AURA","Geocoding",e);report(cb,false,"Не удалось найти город");}
        });
    }
    private static void storeCoords(Context c,double lat,double lon,String label){
        Config.prefs(c).edit().putString("city",label)
            .putLong("lat_bits",Double.doubleToRawLongBits(lat))
            .putLong("lon_bits",Double.doubleToRawLongBits(lon))
            .putLong("weather_updated",0)
            .remove("solar_rises").remove("solar_sets").remove("solar_timezone")
            .apply();
    }
    public static void setCoordinates(Context c,double lat,double lon,String label,Callback cb){
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180){
            report(cb,false,"Неверные координаты");return;
        }
        storeCoords(c,lat,lon,label);
        refresh(c,true,cb);
    }
    public static void refresh(Context c,boolean force,Callback cb){
        Context app=c.getApplicationContext();
        EXEC.execute(()->load(app,force,cb));
    }
    private static void load(Context c,boolean force,Callback cb){
        SharedPreferences p=Config.prefs(c);
        if(!p.contains("lat_bits")){report(cb,false,"Выберите город");return;}
        long old=p.getLong("weather_updated",0);
        if(!force&&old>0&&System.currentTimeMillis()-old<TTL){
            report(cb,true,"Погода актуальна");return;
        }
        try{
            double lat=Double.longBitsToDouble(p.getLong("lat_bits",0));
            double lon=Double.longBitsToDouble(p.getLong("lon_bits",0));
            String url="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+
                "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"+
                "&daily=temperature_2m_max,temperature_2m_min,weather_code,sunrise,sunset"+
                "&timeformat=unixtime&timezone=auto&forecast_days=4";
            JSONObject json=new JSONObject(fetch(url));
            JSONObject now=json.getJSONObject("current");
            JSONObject daily=json.getJSONObject("daily");
            JSONArray hi=daily.getJSONArray("temperature_2m_max"),lo=daily.getJSONArray("temperature_2m_min");
            StringBuilder solarRises=new StringBuilder(),solarSets=new StringBuilder();
            JSONArray sunrise=daily.optJSONArray("sunrise"),sunset=daily.optJSONArray("sunset");
            if(sunrise!=null&&sunset!=null){
                int limit=Math.min(4,Math.min(sunrise.length(),sunset.length()));
                for(int i=0;i<limit;i++){
                    if(sunrise.isNull(i)||sunset.isNull(i))continue; // Polar day/night
                    long rise=sunrise.optLong(i,0)*1000L;
                    long set=sunset.optLong(i,0)*1000L;
                    if(rise<946684800000L||set<=rise||set-rise>22*3600000L)continue;
                    if(solarRises.length()>0){
                        solarRises.append(',');
                        solarSets.append(',');
                    }
                    solarRises.append(rise);
                    solarSets.append(set);
                }
            }
            p.edit().putString("solar_rises",solarRises.toString())
                .putString("solar_sets",solarSets.toString())
                .putString("solar_timezone",json.optString("timezone",""))
                .putInt("weather_temp",(int)Math.round(now.getDouble("temperature_2m")))
                .putInt("weather_code",now.getInt("weather_code"))
                .putInt("weather_humidity",now.optInt("relative_humidity_2m"))
                .putInt("weather_wind",(int)Math.round(now.optDouble("wind_speed_10m",0)))
                .putInt("weather_max",(int)Math.round(hi.getDouble(0)))
                .putInt("weather_min",(int)Math.round(lo.getDouble(0)))
                .putLong("weather_updated",System.currentTimeMillis()).apply();
            WeatherWidgetProvider.updateAll(c);
            report(cb,true,"Погода обновлена");
        }catch(Exception e){Log.w("AURA","Weather update",e);report(cb,false,"Проверьте интернет");}
    }
}
