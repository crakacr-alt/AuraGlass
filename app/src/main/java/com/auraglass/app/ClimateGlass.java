package com.auraglass.app;

/**
 * Artistic glass-weather heuristic, not measured surface thermodynamics.
 * Uses actual Open-Meteo temperature and relative humidity when the cached
 * forecast is present and fresh. Manual ice mode is never suppressed.
 */
public final class ClimateGlass {
    private ClimateGlass(){}
    private static float clamp(float x){return Math.max(0,Math.min(1,x));}
    public static boolean fresh(WeatherRepository.Snapshot s){
        return s.available&&s.updated>0&&
            System.currentTimeMillis()-s.updated<3L*60*60*1000;
    }
    public static float dewPoint(int temperatureC,int relativeHumidity){
        float rh=Math.max(1,Math.min(100,relativeHumidity))/100f;
        double gamma=Math.log(rh)+17.625*temperatureC/(243.04+temperatureC);
        return (float)(243.04*gamma/(17.625-gamma));
    }
    public static float frostBoost(WeatherRepository.Snapshot s,boolean manual){
        if(manual||!fresh(s))return 1f;
        float humidity=clamp((s.humidity-35f)/65f);
        float cold=clamp((4-s.temp)/20f);
        return .55f+.65f*cold*(.52f+.48f*humidity);
    }
    public static float recoveryMultiplier(WeatherRepository.Snapshot s,boolean manual){
        if(manual||!fresh(s))return 1f;
        float humidity=clamp((s.humidity-35f)/65f);
        float cold=clamp((2-s.temp)/17f);
        // High humidity and cold speed up visible recondensation / growth.
        return Math.max(.65f,Math.min(1.4f,1.28f-.33f*humidity-.24f*cold));
    }
}
