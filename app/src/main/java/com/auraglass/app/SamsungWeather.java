package com.auraglass.app;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.List;
public final class SamsungWeather {
    private SamsungWeather(){}
    public static boolean open(Context c){
        PackageManager pm=c.getPackageManager();
        for(String pkg:new String[]{"com.sec.android.daemonapp","com.samsung.android.weather"}){
            Intent launch=pm.getLaunchIntentForPackage(pkg);
            if(launch==null){
                Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg);
                List<ResolveInfo> matches=pm.queryIntentActivities(query,0);
                if(!matches.isEmpty()){
                    ResolveInfo m=matches.get(0);
                    launch=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(m.activityInfo.packageName,m.activityInfo.name);
                }
            }
            if(launch!=null){
                try{c.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return true;}
                catch(Exception ignored){}
            }
        }
        return false;
    }
}
