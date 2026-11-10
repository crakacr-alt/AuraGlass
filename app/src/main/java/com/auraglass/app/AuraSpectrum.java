package com.auraglass.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.Looper;

/** Real FFT of device output mix, ONLY when the visible user opted in. No audio is stored. */
public final class AuraSpectrum {
    public interface Listener {
        void onBands(float[] bands);
    }
    private final Context context;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Listener listener;
    private Visualizer visualizer;
    private boolean active,unsupported;
    public AuraSpectrum(Context ctx,Listener listener){
        context=ctx.getApplicationContext();this.listener=listener;
    }
    public boolean isRunning(){return active;}
    public boolean start(){
        if(active)return true;
        if(unsupported)return false;
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
            return false;
        try{
            visualizer=new Visualizer(0);
            int[] range=Visualizer.getCaptureSizeRange();
            int capture=Math.min(range[1],512);
            while(capture>range[0] && (capture&(capture-1))!=0)capture--;
            if(capture<range[0])capture=range[0];
            visualizer.setCaptureSize(capture);
            int rate=Math.min(Visualizer.getMaxCaptureRate(),16000);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener(){
                @Override public void onWaveFormDataCapture(Visualizer v,byte[] waveform,int sampleRate){}
                @Override public void onFftDataCapture(Visualizer v,byte[] fft,int sampleRate){
                    if(!active||fft==null||fft.length<12)return;
                    float[] levels=new float[24];
                    int maxBin=fft.length/2-2;
                    for(int i=0;i<levels.length;i++){
                        float x=(i+.75f)/(levels.length+.5f);
                        int bin=1+(int)(Math.pow(x,1.48)*Math.min(maxBin,110));
                        bin=Math.max(1,Math.min(maxBin,bin));
                        float sum=0;
                        int count=0;
                        for(int b=bin;b<=Math.min(maxBin,bin+2);b++){
                            int re=fft[2*b],im=fft[2*b+1];
                            sum+=(float)Math.sqrt(re*re+im*im);count++;
                        }
                        float value=count==0?0:(sum/count);
                        levels[i]=Math.min(1f,(float)Math.log1p(value)/5.2f);
                    }
                    handler.post(()->{if(active)listener.onBands(levels);});
                }
            },rate,false,true);
            active=true;
            visualizer.setEnabled(true);
            return true;
        }catch(RuntimeException|LinkageError e){
            android.util.Log.w("AuraSpectrum","Visualizer unsupported on this device",e);
            stop();
            unsupported=true;
            return false;
        }
    }
    public void stop(){
        active=false;
        if(visualizer!=null){
            try{visualizer.setEnabled(false);}catch(RuntimeException ignored){}
            try{visualizer.release();}catch(RuntimeException ignored){}
            visualizer=null;
        }
    }
}
