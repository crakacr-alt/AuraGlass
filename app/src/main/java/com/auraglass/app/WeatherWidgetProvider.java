package com.auraglass.app;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.widget.RemoteViews;

/** Standard Android widgets use bitmap updates, not continuous animation (system limitation). */
public final class WeatherWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] widgetIds){
        for(int id:widgetIds)update(c,manager,id);
        WeatherRepository.refresh(c,false,null);
    }
    public static void updateAll(Context c){
        AppWidgetManager m=AppWidgetManager.getInstance(c);
        int[] ids=m.getAppWidgetIds(new ComponentName(c,WeatherWidgetProvider.class));
        for(int id:ids)update(c,m,id);
    }
    private static void update(Context c,AppWidgetManager m,int id){
        Bitmap bitmap=Bitmap.createBitmap(600,245,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        new WeatherCardPainter().draw(canvas,c,600,245,WeatherRepository.read(c),0);
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_weather);
        v.setImageViewBitmap(R.id.widget_image,bitmap);
        Intent intent=new Intent(c,WeatherLaunchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent click=PendingIntent.getActivity(c,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_image,click);
        m.updateAppWidget(id,v);
        bitmap.recycle();
    }
}
