package com.auraglass.app;

/** Deterministic regression test of solar transitions and stale forecast fallback. */
public final class SolarSelfTest {
    private SolarSelfTest(){}
    public static String run(){
        final long day=1735689600000L; // 2025-01-01T00:00 UTC
        final long rise=day+7*3600000L,set=day+17*3600000L;
        SolarLighting cycle=new SolarLighting();
        cycle.load(rise+","+(rise+86400000L),set+","+(set+86400000L));
        float[] levels=new float[2];
        boolean noon=cycle.sample(day+12*3600000L,levels)
            &&levels[0]>.98f&&levels[1]<.04f;
        boolean dawn=cycle.sample(rise-60*60000L,levels)
            &&levels[0]<.05f&&levels[1]>.08f;
        boolean dusk=cycle.sample(set-45*60000L,levels)&&levels[1]>.10f;
        boolean midnight=cycle.sample(day+23*3600000L,levels)
            &&levels[0]<.02f&&levels[1]<.02f;
        boolean nextDay=cycle.sample(day+36*3600000L,levels)&&levels[0]>.98f;
        boolean stale=!cycle.sample(day+5*86400000L,levels);
        cycle.load("bad","data");
        boolean corrupt=!cycle.sample(day+12*3600000L,levels);
        String state=noon&&dawn&&dusk&&midnight&&nextDay&&stale&&corrupt?
            "SOLAR_CYCLE_OK":"SOLAR_CYCLE_FAILED";
        return state+" noon="+noon+" dawn="+dawn+" dusk="+dusk+
            " night="+midnight+" nextDay="+nextDay+
            " stale="+stale+" corrupt="+corrupt;
    }
}
