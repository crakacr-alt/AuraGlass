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
        if("pages".equals(target)){
            String old0=Config.str(this,"pinned_apps","");
            float oldCount=Config.number(this,"home_pages_count",1f);
            String old1=Config.str(this,"home_page_1","");
            String old2=Config.str(this,"home_page_2","");
            boolean okay=false;
            try{
                if(WorkspacePager.pageCount(this)==1){
                    boolean first=WorkspacePager.addPage(this);
                    boolean second=WorkspacePager.addPage(this);
                    boolean a=WorkspacePager.add(this,1,"aura.qa/.Alpha",8);
                    boolean b=WorkspacePager.add(this,1,"aura.qa/.Beta",8);
                    boolean rejected= !WorkspacePager.add(this,2,"aura.qa/.Alpha",8);
                    boolean reordered=WorkspacePager.move(this,1,0,1,1,8);
                    boolean ordered=WorkspacePager.shortcuts(this,1).get(0)
                        .equals("aura.qa/.Beta");
                    boolean moved=WorkspacePager.move(this,1,1,2,0,8);
                    boolean transferred=WorkspacePager.shortcuts(this,2)
                        .contains("aura.qa/.Alpha");
                    boolean cleared=WorkspacePager.removeEverywhere(this,"aura.qa/.Alpha");
                    boolean removedLast=WorkspacePager.deleteLastEmpty(this);
                    boolean cleaned=WorkspacePager.removeEverywhere(this,"aura.qa/.Beta");
                    boolean removedNext=WorkspacePager.deleteLastEmpty(this);
                    okay=first&&second&&a&&b&&rejected&&reordered&&ordered&&moved&&
                        transferred&&cleared&&removedLast&&cleaned&&removedNext&&
                        WorkspacePager.pageCount(this)==1;
                }
            }catch(Exception error){
                android.util.Log.e("AURA_QA","WORKSPACE_SMOKE_EXCEPTION",error);
            }finally{
                // The check is reversible and never keeps QA shortcuts.
                Config.put(this,"pinned_apps",old0);
                Config.put(this,"home_pages_count",oldCount);
                Config.put(this,"home_page_1",old1);
                Config.put(this,"home_page_2",old2);
            }
            android.util.Log.i("AURA_QA",okay?"PAGES_OK":"PAGES_FAILED");
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
