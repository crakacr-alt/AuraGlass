package com.auraglass.app;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
public final class WeatherLaunchActivity extends Activity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        if(!SamsungWeather.open(this)){
            startActivity(new Intent(this,MainActivity.class));
        }
        finish();
    }
}
