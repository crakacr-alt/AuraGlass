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
    private static final int WIDGET_REQUEST=9024;
    private int pendingWidgetId=-1;
    // UIAutomator can trigger an Android configuration change while the
    // Settings consent dialog is open. Starting a second bind request cancels
    // the first. Both the saved instance and process-level guard prevent that.
    private static int activeBindId=-1;
    private static long activeBindAt=0;
    private static final long BIND_EXPIRY_MS=120000;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null)pendingWidgetId=state.getInt("qa_pending_widget",-1);
        if((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)==0){
            finish();return;
        }
        String target=getIntent()==null?"":getIntent().getStringExtra("target");
        Class<? extends Activity> page=null;
        if("solar_cycle".equals(target)){
            try{android.util.Log.i("AURA_QA",SolarSelfTest.run());}
            catch(Throwable e){
                android.util.Log.e("AURA_QA","SOLAR_CYCLE_EXCEPTION",e);
                android.util.Log.i("AURA_QA","SOLAR_CYCLE_FAILED");
            }
            finish();return;
        }
        if("ice_multitouch".equals(target)){
            new Thread(()->{
                try{android.util.Log.i("AURA_QA",FrostSelfTest.runMultitouch());}
                catch(Throwable e){
                    android.util.Log.e("AURA_QA","ICE_MULTITOUCH_EXCEPTION",e);
                    android.util.Log.i("AURA_QA","ICE_MULTITOUCH_FAILED");
                }finally{runOnUiThread(this::finish);}
            },"AURA-multitouch-qa").start();
            return;
        }
        if("ice_physics".equals(target)){
            new Thread(()->{
                try{
                    android.util.Log.i("AURA_QA",FrostSelfTest.run());
                }catch(Throwable e){
                    android.util.Log.e("AURA_QA","ICE_PHYSICS_EXCEPTION",e);
                    android.util.Log.i("AURA_QA","ICE_PHYSICS_FAILED");
                }finally{
                    runOnUiThread(this::finish);
                }
            },"AURA-ice-qa").start();
            return;
        }
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
        if("widget_bind".equals(target)){
            if(pendingWidgetId>0){
                android.util.Log.i("AURA_QA",
                    "WIDGET_BIND_RESUMED id="+pendingWidgetId);
                // Wait for the already-open Settings consent activity to return.
                return;
            }
            synchronized(QaEntryActivity.class){
                if(activeBindId>0&&
                    android.os.SystemClock.elapsedRealtime()-activeBindAt<
                        BIND_EXPIRY_MS){
                    android.util.Log.i("AURA_QA",
                        "WIDGET_BIND_DUPLICATE_PREVENTED id="+activeBindId);
                    finish();return;
                }
            }
            try{
                android.appwidget.AppWidgetHost appHost=
                    new android.appwidget.AppWidgetHost(this,70901);
                pendingWidgetId=appHost.allocateAppWidgetId();
                synchronized(QaEntryActivity.class){
                    activeBindId=pendingWidgetId;
                    activeBindAt=android.os.SystemClock.elapsedRealtime();
                }
                android.util.Log.i("AURA_QA",
                    "WIDGET_BIND_STARTED id="+pendingWidgetId);
                android.appwidget.AppWidgetManager manager=
                    android.appwidget.AppWidgetManager.getInstance(this);
                android.content.ComponentName provider=
                    new android.content.ComponentName(this,WeatherWidgetProvider.class);
                if(manager.bindAppWidgetIdIfAllowed(pendingWidgetId,provider)){
                    finishWidgetBinding();
                }else{
                    android.util.Log.i("AURA_QA","WIDGET_PERMISSION_REQUESTED");
                    Intent request=new Intent(
                        android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND);
                    request.putExtra(
                        android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidgetId);
                    request.putExtra(
                        android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,provider);
                    startActivityForResult(request,WIDGET_REQUEST);
                }
            }catch(Exception e){
                android.util.Log.e("AURA_QA","WIDGET_BIND_EXCEPTION",e);
                cleanWidgetBinding();
                android.util.Log.i("AURA_QA","WIDGET_BIND_FAILED");
                finish();
            }
            return;
        }
        if("diagnostics".equals(target))page=DiagnosticsActivity.class;
        else if("widgets".equals(target))page=WidgetBoardActivity.class;
        else if("ice".equals(target))page=IceShowcaseActivity.class;
        else if("editor".equals(target))page=MainActivity.class;
        if(page!=null)startActivity(new Intent(this,page));
        finish();
    }
    @Override protected void onStart(){
        super.onStart();
        if(pendingWidgetId>0)
            android.util.Log.i("AURA_QA","WIDGET_ACTIVITY_ON_START pending="+pendingWidgetId);
    }
    @Override protected void onResume(){
        super.onResume();
        if(pendingWidgetId>0)
            android.util.Log.i("AURA_QA","WIDGET_ACTIVITY_ON_RESUME pending="+pendingWidgetId);
    }
    @Override protected void onPause(){
        if(pendingWidgetId>0)
            android.util.Log.i("AURA_QA","WIDGET_ACTIVITY_ON_PAUSE pending="+pendingWidgetId);
        super.onPause();
    }
    @Override protected void onStop(){
        if(pendingWidgetId>0)
            android.util.Log.i("AURA_QA","WIDGET_ACTIVITY_ON_STOP pending="+pendingWidgetId);
        super.onStop();
    }
    @Override protected void onDestroy(){
        if(pendingWidgetId>0)
            android.util.Log.i("AURA_QA","WIDGET_ACTIVITY_ON_DESTROY pending="+pendingWidgetId+
                " changingConfig="+isChangingConfigurations()+" finishing="+isFinishing());
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        if(pendingWidgetId>0){
            android.util.Log.i("AURA_QA","WIDGET_ACTIVITY_SAVE_STATE pending="+pendingWidgetId);
            state.putInt("qa_pending_widget",pendingWidgetId);
        }
        super.onSaveInstanceState(state);
    }
    private void clearBindingGuard(){
        synchronized(QaEntryActivity.class){
            if(activeBindId==pendingWidgetId){
                activeBindId=-1;
                activeBindAt=0;
            }
        }
    }
    private void cleanWidgetBinding(){
        clearBindingGuard();
        if(pendingWidgetId>0){
            try{new android.appwidget.AppWidgetHost(this,70901)
                .deleteAppWidgetId(pendingWidgetId);}
            catch(RuntimeException ignored){}
            pendingWidgetId=-1;
        }
    }
    private void finishWidgetBinding(){
        try{
            android.appwidget.AppWidgetManager manager=
                android.appwidget.AppWidgetManager.getInstance(this);
            if(pendingWidgetId<=0||manager.getAppWidgetInfo(pendingWidgetId)==null)
                throw new IllegalStateException("Widget not bound");
            String previous=Config.str(this,"widget_board_ids","");
            String item=String.valueOf(pendingWidgetId);
            Config.put(this,"widget_board_ids",
                previous.isEmpty()?item:previous+","+item);
            Config.put(this,"widget_page_"+pendingWidgetId,0f);
            WeatherWidgetProvider.updateAll(this);
            android.util.Log.i("AURA_QA","WIDGET_BIND_OK");
            clearBindingGuard();
            pendingWidgetId=-1;
            startActivity(new Intent(this,HomeActivity.class));
        }catch(Exception e){
            android.util.Log.e("AURA_QA","WIDGET_BIND_EXCEPTION",e);
            android.util.Log.i("AURA_QA","WIDGET_BIND_FAILED");
            cleanWidgetBinding();
        }
        finish();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=WIDGET_REQUEST)return;
        android.util.Log.i("AURA_QA",
            "WIDGET_BIND_RESULT result="+result+" pending="+pendingWidgetId);
        if(result==RESULT_OK){
            if(data!=null)
                pendingWidgetId=data.getIntExtra(
                    android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidgetId);
            finishWidgetBinding();
        }else{
            android.util.Log.i("AURA_QA","WIDGET_BIND_DECLINED");
            cleanWidgetBinding();finish();
        }
    }
}
