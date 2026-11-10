package com.auraglass.app;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;

/** Subtle optional depth from the accelerometer. Never runs when invisible. */
public final class ParallaxMotion implements SensorEventListener {
    private final Context context;
    private final AtmosphereRenderer renderer;
    private final SensorManager sensors;
    private final Sensor gravity;
    private boolean listening;
    private float gx,gy;
    private long lastSensorAt;

    public ParallaxMotion(Context context,AtmosphereRenderer renderer){
        this.context=context.getApplicationContext();
        this.renderer=renderer;
        sensors=(SensorManager)this.context.getSystemService(Context.SENSOR_SERVICE);
        gravity=sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
    }
    public void start(){
        PowerManager pm=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        if(listening||gravity==null||sensors==null ||
            !Config.bool(context,"parallax",false)||
            Config.bool(context,"reduce_motion",false)||
            pm!=null&&pm.isPowerSaveMode())return;
        gx=0;gy=0;lastSensorAt=0;
        listening=sensors.registerListener(this,gravity,SensorManager.SENSOR_DELAY_GAME);
    }
    public void stop(){
        if(listening&&sensors!=null)sensors.unregisterListener(this);
        listening=false;
        lastSensorAt=0;
        renderer.setParallax(0,0);
    }
    @Override public void onSensorChanged(SensorEvent event){
        if(!listening||event.values.length<2)return;
        long now=android.os.SystemClock.uptimeMillis();
        if(now-lastSensorAt<38)return;
        lastSensorAt=now;
        float x=Math.max(-1,Math.min(1,event.values[0]/9.81f));
        float y=Math.max(-1,Math.min(1,event.values[1]/9.81f));
        gx=.86f*gx+.14f*x;
        gy=.86f*gy+.14f*y;
        renderer.setParallax(gx,gy);
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
}
