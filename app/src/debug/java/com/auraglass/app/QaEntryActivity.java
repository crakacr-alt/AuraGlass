package com.auraglass.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

/**
 * Instrumentation-only entry point. Android Gradle packages src/debug into
 * debug APKs ONLY; this component is absent from release builds.
 */
public final class QaEntryActivity extends Activity {
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        if((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)==0){
            finish();return;
        }
        String target=getIntent()==null?"":getIntent().getStringExtra("target");
        Class<? extends Activity> page=null;
        if("diagnostics".equals(target))page=DiagnosticsActivity.class;
        else if("widgets".equals(target))page=WidgetBoardActivity.class;
        else if("ice".equals(target))page=IceShowcaseActivity.class;
        else if("editor".equals(target))page=MainActivity.class;
        if(page!=null)startActivity(new Intent(this,page));
        finish();
    }
}
