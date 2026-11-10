package com.auraglass.app;
import android.content.Context;
import android.content.SharedPreferences;
public final class Config {
    private Config(){}
    public static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences("aura_glass_settings_v1",Context.MODE_PRIVATE);}
    public static boolean bool(Context c,String k,boolean d){return prefs(c).getBoolean(k,d);}
    public static float number(Context c,String k,float d){return prefs(c).getFloat(k,d);}
    public static String str(Context c,String k,String d){return prefs(c).getString(k,d);}
    public static void put(Context c,String k,boolean v){prefs(c).edit().putBoolean(k,v).apply();}
    public static void put(Context c,String k,float v){prefs(c).edit().putFloat(k,v).apply();}
    public static void put(Context c,String k,String v){prefs(c).edit().putString(k,v).apply();}
    public static float clamp(float x,float lo,float hi){return Math.max(lo,Math.min(hi,x));}
    public static float mix(float a,float b,float t){return a+(b-a)*t;}
}
