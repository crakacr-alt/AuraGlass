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
        if("folders".equals(target)){
            boolean success=false;
            String id=null;
            int before=FolderStore.all(this).size();
            try{
                success=FolderStore.create(this,"AURA_QA_Folder",
                    "com.android.settings/.Settings");
                java.util.List<FolderStore.Folder> after=FolderStore.all(this);
                success=success&&after.size()==before+1;
                if(success){
                    id=after.get(after.size()-1).id;
                    success=FolderStore.add(this,id,"com.android.chrome/.Main");
                    java.util.List<FolderStore.Folder> added=FolderStore.all(this);
                    success=success&&added.get(added.size()-1).items.size()==2;
                    FolderStore.rename(this,id,"AURA_QA_Renamed");
                    success=success&&"AURA_QA_Renamed".equals(
                        FolderStore.all(this).get(FolderStore.all(this).size()-1).name);
                    FolderStore.removeItem(this,id,"com.android.chrome/.Main");
                    success=success&&FolderStore.all(this).get(
                        FolderStore.all(this).size()-1).items.size()==1;
                }
            }catch(Exception error){
                android.util.Log.e("AURA_QA","FOLDER_SMOKE_EXCEPTION",error);
                success=false;
            }finally{
                if(id!=null)FolderStore.delete(this,id);
                success=success&&FolderStore.all(this).size()==before;
            }
            android.util.Log.i("AURA_QA",success?"FOLDERS_OK":"FOLDERS_FAILED");
            finish();return;
        }
        if("diagnostics".equals(target))page=DiagnosticsActivity.class;
        else if("widgets".equals(target))page=WidgetBoardActivity.class;
        else if("ice".equals(target))page=IceShowcaseActivity.class;
        else if("editor".equals(target))page=MainActivity.class;
        if(page!=null)startActivity(new Intent(this,page));
        finish();
    }
}
