package com.auraglass.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ClipData;
import android.view.DragEvent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Native launcher: real applications, editable favorites, searchable drawer, existing wallpaper remains visible. */
public final class HomeActivity extends Activity {
    private FrameLayout root, drawer;
    private TextView clock,date;
    private WeatherCardView weatherCard;
    private AuraIslandView island;
    private int homePage=0;
    private boolean arrangeMode=false;
    private AppWidgetHost desktopWidgetHost;
    private AppWidgetManager desktopWidgetManager;
    private boolean widgetListening=false;
    private String widgetLayoutSnapshot="";
    private static final class ShortcutDrag {
        final int page,slot;
        ShortcutDrag(int page,int slot){this.page=page;this.slot=slot;}
    }
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final List<ResolveInfo> applications = new ArrayList<>();
    private final Runnable clockTick = new Runnable(){
        @Override public void run(){updateClock();timer.postDelayed(this,30000);}
    };
    private int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
    private TextView label(String value,int size,boolean bold){
        TextView text=new TextView(this);
        text.setText(value);text.setTextColor(0xfffaf6ff);text.setTextSize(size);
        if(bold)text.setTypeface(Typeface.create("sans-serif-medium",0));
        return text;
    }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        RefreshRateSupport.applyToActivity(this);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(0xff181a2d);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        root=new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);
        setContentView(root);
        desktopWidgetHost=new AppWidgetHost(this,70901);
        desktopWidgetManager=AppWidgetManager.getInstance(this);
        loadApplications();
        widgetLayoutSnapshot=widgetLayoutState();
        showHome();
    }
    private void loadApplications(){
        Intent request=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm=getPackageManager();
        applications.clear();
        applications.addAll(pm.queryIntentActivities(request,0));
        Collections.sort(applications,Comparator.comparing(
            app->String.valueOf(app.loadLabel(pm)),String.CASE_INSENSITIVE_ORDER));
    }
    private String componentKey(ResolveInfo item){
        return new ComponentName(item.activityInfo.packageName,item.activityInfo.name).flattenToShortString();
    }
    private List<ResolveInfo> favorites(){
        if(homePage>0){
            List<ResolveInfo> result=new ArrayList<>();
            for(String key:WorkspacePager.shortcuts(this,homePage)){
                ResolveInfo app=findApp(key);
                if(app!=null)result.add(app);
            }
            return result;
        }
        String saved=Config.str(this,"pinned_apps","");
        if(!Config.prefs(this).contains("pinned_apps")){
            List<ResolveInfo> suggested=selectByPackages(new String[]{
                "com.openai.chatgpt","org.telegram.messenger","com.whatsapp","com.yandex",
                "com.android.gallery","com.sec.android.gallery3d","com.android.camera",
                "com.android.settings"},8);
            if(suggested.size()<8){
                Set<String> existing=new HashSet<>();
                for(ResolveInfo r:suggested)existing.add(componentKey(r));
                for(ResolveInfo r:applications){
                    if(suggested.size()>=8)break;
                    if(existing.add(componentKey(r)))suggested.add(r);
                }
            }
            persistFavorites(suggested);
            return suggested;
        }
        List<ResolveInfo> result=new ArrayList<>();
        for(String key:saved.split(";")){
            for(ResolveInfo r:applications){
                if(componentKey(r).equals(key)){
                    result.add(r);break;
                }
            }
            if(result.size()>=8)break;
        }
        return result;
    }
    private List<ResolveInfo> dockEntries(){
        if(!Config.prefs(this).contains("dock_apps")){
            List<ResolveInfo> selected=selectByPackages(new String[]{
                "com.google.android.dialer","com.samsung.android.dialer","com.android.dialer",
                "com.google.android.apps.messaging","com.samsung.android.messaging",
                "com.android.chrome","com.sec.android.app.sbrowser",
                "com.sec.android.app.camera","com.android.camera","com.android.settings"},5);
            if(selected.size()<5){
                Set<String> used=new HashSet<>();
                for(ResolveInfo item:selected)used.add(componentKey(item));
                for(ResolveInfo item:applications){
                    if(selected.size()>=5)break;
                    if(used.add(componentKey(item)))selected.add(item);
                }
            }
            persistDock(selected);
            return selected;
        }
        List<ResolveInfo> chosen=new ArrayList<>();
        String stored=Config.str(this,"dock_apps","");
        for(String key:stored.split(";")){
            for(ResolveInfo app:applications){
                if(componentKey(app).equals(key)){chosen.add(app);break;}
            }
            if(chosen.size()>=5)break;
        }
        return chosen;
    }
    private void persistDock(List<ResolveInfo> entries){
        StringBuilder sb=new StringBuilder();
        for(ResolveInfo app:entries){
            if(sb.length()>0)sb.append(';');
            sb.append(componentKey(app));
        }
        Config.put(this,"dock_apps",sb.toString());
    }
    private void changeDock(ResolveInfo entry,boolean add){
        List<ResolveInfo> list=dockEntries();
        String key=componentKey(entry);
        if(add){
            if(list.size()>=5){Toast.makeText(this,"В доке максимум 5 приложений",Toast.LENGTH_SHORT).show();return;}
            for(ResolveInfo item:list)if(componentKey(item).equals(key)){
                Toast.makeText(this,"Уже находится в доке",Toast.LENGTH_SHORT).show();return;
            }
            list.add(entry);
        }else list.removeIf(app->componentKey(app).equals(key));
        persistDock(list);
        if(drawer!=null)closeDrawer(false);
        showHome();
    }
    private void shiftItem(ResolveInfo entry,boolean inDock,int direction){
        List<ResolveInfo> entries=inDock?dockEntries():favorites();
        String key=componentKey(entry);
        int index=-1;
        for(int i=0;i<entries.size();i++)
            if(key.equals(componentKey(entries.get(i)))){index=i;break;}
        int next=index+direction;
        if(index<0||next<0||next>=entries.size())return;
        Collections.swap(entries,index,next);
        if(inDock)persistDock(entries);
        else persistFavorites(entries);
        showHome();
    }
    private List<ResolveInfo> selectByPackages(String[] matches,int max){
        List<ResolveInfo> result=new ArrayList<>();
        Set<String> visited=new HashSet<>();
        for(String want:matches){
            for(ResolveInfo r:applications){
                String pkg=r.activityInfo.packageName.toLowerCase(Locale.ROOT);
                if(pkg.contains(want.toLowerCase(Locale.ROOT)) && visited.add(componentKey(r))){
                    result.add(r);break;
                }
            }
            if(result.size()>=max)break;
        }
        return result;
    }
    private void persistFavorites(List<ResolveInfo> pinned){
        List<String> keys=new ArrayList<>();
        for(ResolveInfo item:pinned)keys.add(componentKey(item));
        WorkspacePager.save(this,homePage,keys);
    }
    private void changeFavorite(ResolveInfo item,boolean add){
        List<ResolveInfo> current=favorites();
        String id=componentKey(item);
        if(add){
            if(WorkspacePager.hasAnywhere(this,id)){
                Toast.makeText(this,"Приложение уже закреплено на одной из страниц",Toast.LENGTH_SHORT).show();
                return;
            }
            int max=WorkspacePager.SLOTS-(homePage==0?FolderStore.all(this).size():0);
            if(current.size()>=max){
                Toast.makeText(this,"Страница заполнена. Можно добавить новую.",Toast.LENGTH_SHORT).show();
                return;
            }
            current.add(item);
        }else{
            current.removeIf(r->componentKey(r).equals(id));
        }
        persistFavorites(current);
        if(drawer!=null)closeDrawer(false);
        showHome();
        Toast.makeText(this,add?"Ярлык добавлен":"Ярлык удалён",Toast.LENGTH_SHORT).show();
    }
    private ResolveInfo findApp(String key){
        for(ResolveInfo info:applications)if(componentKey(info).equals(key))return info;
        return null;
    }
    private List<ResolveInfo> folderApps(FolderStore.Folder folder){
        List<ResolveInfo> entries=new ArrayList<>();
        for(String key:folder.items){
            ResolveInfo resolved=findApp(key);
            if(resolved!=null)entries.add(resolved);
        }
        return entries;
    }
    private void removePinIfPresent(ResolveInfo app){
        WorkspacePager.removeEverywhere(this,componentKey(app));
    }
    private void createFolder(ResolveInfo first){
        List<String> pinned=WorkspacePager.shortcuts(this,0);
        boolean isPinned=pinned.contains(componentKey(first));
        if(FolderStore.all(this).size()>=8||
           !isPinned&&pinned.size()+FolderStore.all(this).size()>=8){
            Toast.makeText(this,"Сначала освободите место на главном экране",Toast.LENGTH_LONG).show();
            return;
        }
        EditText name=new EditText(this);
        name.setSingleLine(true);name.setText("Новая папка");
        name.setPadding(dp(18),dp(7),dp(18),dp(7));
        new AlertDialog.Builder(this).setTitle("Создать папку")
            .setView(name).setNegativeButton("Отмена",null)
            .setPositiveButton("Создать",(dialog,which)->{
                if(FolderStore.create(this,name.getText().toString(),componentKey(first))){
                    removePinIfPresent(first);
                    if(drawer!=null)closeDrawer(false);
                    showHome();
                }else Toast.makeText(this,"Не получилось создать папку",Toast.LENGTH_SHORT).show();
            }).show();
    }
    private void chooseFolder(ResolveInfo app){
        List<FolderStore.Folder> folders=FolderStore.all(this);
        if(folders.isEmpty()){createFolder(app);return;}
        String[] names=new String[folders.size()+1];
        for(int i=0;i<folders.size();i++)names[i]=folders.get(i).name;
        names[folders.size()]="＋ Создать новую папку";
        new AlertDialog.Builder(this).setTitle("Добавить в папку")
            .setItems(names,(dialog,which)->{
                if(which==folders.size()){createFolder(app);return;}
                if(FolderStore.add(this,folders.get(which).id,componentKey(app))){
                    removePinIfPresent(app);
                    if(drawer!=null)closeDrawer(false);
                    showHome();
                }else Toast.makeText(this,"Папка заполнена",Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Отмена",null).show();
    }
    private void openFolder(FolderStore.Folder folder){
        List<ResolveInfo> members=folderApps(folder);
        GridView icons=icons(members,3,false,false);
        icons.setVerticalSpacing(dp(6));
        icons.setPadding(dp(8),dp(8),dp(8),dp(8));
        new AlertDialog.Builder(this).setTitle("▣  "+folder.name)
            .setView(icons)
            .setNegativeButton("Закрыть",null)
            .setPositiveButton("Изменить",(d,w)->editFolder(folder))
            .show();
        ViewGroup.LayoutParams lp=icons.getLayoutParams();
        if(lp!=null){lp.height=dp(220);icons.setLayoutParams(lp);}
    }
    private void editFolder(FolderStore.Folder folder){
        String[] choices={"Переименовать","Добавить приложение","Убрать приложение","Удалить папку"};
        new AlertDialog.Builder(this).setTitle(folder.name)
            .setItems(choices,(dialog,option)->{
                if(option==0){
                    EditText name=new EditText(this);
                    name.setSingleLine(true);name.setText(folder.name);
                    new AlertDialog.Builder(this).setTitle("Название папки").setView(name)
                        .setNegativeButton("Отмена",null)
                        .setPositiveButton("Сохранить",(d,w)->{
                            FolderStore.rename(this,folder.id,name.getText().toString());
                            showHome();
                        }).show();
                }else if(option==1){
                    if(applications.isEmpty())return;
                    String[] labels=new String[applications.size()];
                    for(int i=0;i<applications.size();i++)
                        labels[i]=String.valueOf(applications.get(i).loadLabel(getPackageManager()));
                    new AlertDialog.Builder(this).setTitle("Добавить в "+folder.name)
                        .setItems(labels,(d,w)->{
                            ResolveInfo app=applications.get(w);
                            if(FolderStore.add(this,folder.id,componentKey(app))){
                                removePinIfPresent(app);showHome();
                            }else Toast.makeText(this,"Папка заполнена",Toast.LENGTH_SHORT).show();
                        }).setNegativeButton("Отмена",null).show();
                }else if(option==2){
                    List<ResolveInfo> members=folderApps(folder);
                    if(members.isEmpty())return;
                    String[] names=new String[members.size()];
                    for(int i=0;i<members.size();i++)
                        names[i]=String.valueOf(members.get(i).loadLabel(getPackageManager()));
                    new AlertDialog.Builder(this).setTitle("Убрать из папки")
                        .setItems(names,(d,w)->{
                            FolderStore.removeItem(this,folder.id,componentKey(members.get(w)));
                            showHome();
                        }).setNegativeButton("Отмена",null).show();
                }else{
                    new AlertDialog.Builder(this).setTitle("Удалить папку?")
                        .setMessage("Ярлыки внутри папки больше не будут закреплены, приложения останутся установлены.")
                        .setNegativeButton("Отмена",null)
                        .setPositiveButton("Удалить",(d,w)->{
                            FolderStore.delete(this,folder.id);showHome();
                        }).show();
                }
            }).setNegativeButton("Отмена",null).show();
    }
    private final class HomeAdapter extends BaseAdapter {
        private final List<FolderStore.Folder> folders=homePage==0?
            FolderStore.all(HomeActivity.this):Collections.emptyList();
        private final List<ResolveInfo> apps;
        private final AppAdapter appAdapter;
        HomeAdapter(List<ResolveInfo> apps){
            this.apps=apps;this.appAdapter=new AppAdapter(apps,true,false);
        }
        @Override public int getCount(){return WorkspacePager.SLOTS;}
        @Override public Object getItem(int position){
            if(position<folders.size())return folders.get(position);
            int index=position-folders.size();
            return index<apps.size()?apps.get(index):null;
        }
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View recycled,ViewGroup parent){
            if(position>=folders.size()){
                int item=position-folders.size();
                if(item<apps.size()){
                    View cell=appAdapter.getView(item,recycled,parent);
                    if(arrangeMode){
                        cell.setOnLongClickListener(v->startShortcutDrag(v,position));
                        cell.setOnDragListener((v,event)->dropOnSlot(event,position));
                    }
                    return cell;
                }
                LinearLayout blank=new LinearLayout(HomeActivity.this);
                blank.setGravity(Gravity.CENTER);
                blank.setMinimumHeight(dp(72));
                if(arrangeMode){
                    TextView plus=label("＋",22,false);
                    plus.setGravity(Gravity.CENTER);
                    plus.setTextColor(0x80d5e6ff);
                    blank.addView(plus,new LinearLayout.LayoutParams(dp(48),dp(48)));
                }
                blank.setOnDragListener((v,event)->dropOnSlot(event,position));
                return blank;
            }
            FolderStore.Folder folder=folders.get(position);
            LinearLayout cell=new LinearLayout(HomeActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(3),dp(4),dp(3),dp(2));
            FrameLayout preview=new FrameLayout(HomeActivity.this);
            GradientDrawable bg=new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,new int[]{0xa96f8ca7,0xb5565f88});
            bg.setCornerRadius(dp(12));bg.setStroke(dp(1),0x8cc7e4f6);
            preview.setBackground(bg);
            List<ResolveInfo> members=folderApps(folder);
            for(int i=0;i<Math.min(4,members.size());i++){
                ImageView icon=new ImageView(HomeActivity.this);
                try{icon.setImageDrawable(members.get(i).loadIcon(getPackageManager()));}
                catch(Exception ignored){}
                FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(20),dp(20));
                lp.leftMargin=dp(4+(i%2)*22);lp.topMargin=dp(3+(i/2)*22);
                preview.addView(icon,lp);
            }
            cell.addView(preview,new LinearLayout.LayoutParams(dp(49),dp(49)));
            TextView caption=label(folder.name,10,false);
            caption.setGravity(Gravity.CENTER);caption.setSingleLine(true);
            caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cell.addView(caption,new LinearLayout.LayoutParams(-1,dp(22)));
            cell.setOnClickListener(v->openFolder(folder));
            cell.setOnLongClickListener(v->{editFolder(folder);return true;});
            cell.setOnDragListener((v,event)->dropOnSlot(event,position));
            return cell;
        }
    }
    private boolean startShortcutDrag(View cell,int slot){
        if(!arrangeMode)return false;
        ClipData data=ClipData.newPlainText("aura-shortcut","workspace");
        ShortcutDrag drag=new ShortcutDrag(homePage,slot);
        return cell.startDragAndDrop(data,new View.DragShadowBuilder(cell),drag,0);
    }
    private boolean dropOnSlot(DragEvent event,int destinationSlot){
        Object state=event.getLocalState();
        if(!(state instanceof ShortcutDrag))return false;
        ShortcutDrag drag=(ShortcutDrag)state;
        switch(event.getAction()){
            case DragEvent.ACTION_DRAG_STARTED:return true;
            case DragEvent.ACTION_DRAG_ENTERED:return true;
            case DragEvent.ACTION_DRAG_EXITED:return true;
            case DragEvent.ACTION_DROP:{
                int folderCount=homePage==0?FolderStore.all(this).size():0;
                int sourceFolderCount=drag.page==0?FolderStore.all(this).size():0;
                int sourceIndex=drag.slot-sourceFolderCount;
                if(sourceIndex<0)return false;
                List<String> from=WorkspacePager.shortcuts(this,drag.page);
                if(sourceIndex>=from.size())return false;
                String key=from.get(sourceIndex);
                if(destinationSlot<folderCount){
                    FolderStore.Folder folder=FolderStore.all(this).get(destinationSlot);
                    if(!FolderStore.add(this,folder.id,key))return false;
                    WorkspacePager.removeEverywhere(this,key);
                    showHome();
                    return true;
                }
                int destIndex=destinationSlot-folderCount;
                int capacity=WorkspacePager.SLOTS-folderCount;
                boolean moved=WorkspacePager.move(this,drag.page,sourceIndex,homePage,destIndex,capacity);
                if(moved)showHome();
                return moved;
            }
            case DragEvent.ACTION_DRAG_ENDED:return true;
        }
        return true;
    }
    private void changePage(int requested){
        if(requested<0||requested>=WorkspacePager.pageCount(this))return;
        homePage=requested;
        showHome();
    }
    private void addPage(){
        if(!WorkspacePager.addPage(this)){
            Toast.makeText(this,"Максимум четыре страницы",Toast.LENGTH_SHORT).show();
            return;
        }
        homePage=WorkspacePager.pageCount(this)-1;
        showHome();
    }
    private void removeEmptyLastPage(){
        if(homePage!=WorkspacePager.pageCount(this)-1 ||
           homePage==0 || !WorkspacePager.deleteLastEmpty(this)){
            Toast.makeText(this,"Можно удалить только последнюю пустую страницу",Toast.LENGTH_SHORT).show();
            return;
        }
        homePage=Math.max(0,homePage-1);
        showHome();
    }
    private void moveShortcutToOtherPage(ResolveInfo app){
        int pages=WorkspacePager.pageCount(this);
        String[] labels=new String[pages];
        for(int i=0;i<pages;i++)labels[i]=(i==homePage?"✓ ":"")+"Страница "+(i+1);
        new AlertDialog.Builder(this).setTitle("Переместить ярлык")
            .setItems(labels,(dialog,which)->{
                if(which==homePage)return;
                List<String> source=WorkspacePager.shortcuts(this,homePage);
                int from=source.indexOf(componentKey(app));
                int capacity=WorkspacePager.SLOTS-(which==0?FolderStore.all(this).size():0);
                boolean moved=WorkspacePager.move(this,homePage,from,which,
                    WorkspacePager.shortcuts(this,which).size(),capacity);
                if(moved){homePage=which;showHome();}
                else Toast.makeText(this,"На этой странице нет места",Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Отмена",null)
            .show();
    }
    private TextView pageControl(String value,int width,Runnable click){
        TextView control=label(value,13,true);
        control.setGravity(Gravity.CENTER);
        control.setContentDescription(value);
        control.setOnClickListener(v->click.run());
        control.setMinHeight(dp(38));
        return control;
    }
    private void showPageStrip(LinearLayout vertical){
        LinearLayout strip=new LinearLayout(this);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        TextView left=pageControl("❮",38,()->changePage(homePage-1));
        strip.addView(left,new LinearLayout.LayoutParams(dp(38),dp(39)));
        TextView counter=pageControl("Страница "+(homePage+1)+" / "+
            WorkspacePager.pageCount(this),0,()->{
                if(arrangeMode)new AlertDialog.Builder(this)
                    .setTitle("Удалить пустую страницу?")
                    .setNegativeButton("Отмена",null)
                    .setPositiveButton("Удалить",(d,w)->removeEmptyLastPage())
                    .show();
            });
        strip.addView(counter,new LinearLayout.LayoutParams(0,dp(39),1));
        TextView right=pageControl("❯",38,()->changePage(homePage+1));
        strip.addView(right,new LinearLayout.LayoutParams(dp(38),dp(39)));
        TextView add=pageControl("＋",42,this::addPage);
        add.setContentDescription("Добавить страницу");
        strip.addView(add,new LinearLayout.LayoutParams(dp(42),dp(39)));
        TextView edit=pageControl(arrangeMode?"✓ Готово":"Изменить",75,()->{
            arrangeMode=!arrangeMode;showHome();
        });
        edit.setContentDescription("Режим перемещения ярлыков");
        strip.addView(edit,new LinearLayout.LayoutParams(dp(75),dp(39)));
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,dp(42));
        layout.topMargin=dp(8);
        vertical.addView(strip,layout);
    }
    private void openWidgetBoard(){
        Intent intent=new Intent(this,WidgetBoardActivity.class);
        intent.putExtra("home_page",homePage);
        startActivity(intent);
    }
    private String widgetLayoutState(){
        String ids=Config.str(this,"widget_board_ids","");
        StringBuilder snapshot=new StringBuilder(ids);
        for(String text:ids.split(",")){
            try{
                int id=Integer.parseInt(text);
                snapshot.append('|').append(id).append(':')
                    .append(Config.number(this,"widget_page_"+id,0))
                    .append(':').append(Config.number(this,"widget_height_"+id,0));
            }catch(NumberFormatException ignored){}
        }
        return snapshot.toString();
    }
    private void showInlineWidgets(LinearLayout container){
        if(desktopWidgetHost==null||desktopWidgetManager==null)return;
        String ids=Config.str(this,"widget_board_ids","");
        int shown=0,hidden=0;
        LinearLayout widgetsArea=new LinearLayout(this);
        widgetsArea.setOrientation(LinearLayout.VERTICAL);
        for(String item:ids.split(",")){
            int id;
            try{id=Integer.parseInt(item);}
            catch(NumberFormatException ignored){continue;}
            if(id<=0||(int)Config.number(this,"widget_page_"+id,0)!=homePage)continue;
            if(shown>=4){hidden++;continue;}
            AppWidgetProviderInfo info;
            try{info=desktopWidgetManager.getAppWidgetInfo(id);}
            catch(RuntimeException error){continue;}
            if(info==null)continue;
            try{
                AppWidgetHostView content=desktopWidgetHost.createView(this,id,info);
                content.setAppWidget(id,info);
                GlassPanel card=new GlassPanel(this);
                card.setPadding(dp(7),dp(7),dp(7),dp(8));
                LinearLayout body=new LinearLayout(this);
                body.setOrientation(LinearLayout.VERTICAL);
                TextView caption=label(String.valueOf(info.loadLabel(getPackageManager())),12,true);
                caption.setTextColor(0xffecf6ff);
                caption.setPadding(dp(9),0,dp(5),0);
                body.addView(caption,new LinearLayout.LayoutParams(-1,dp(27)));
                int height=(int)Config.number(this,"widget_height_"+id,
                    Math.max(110,info.minHeight));
                height=Math.max(110,Math.min(480,height));
                body.addView(content,new LinearLayout.LayoutParams(-1,dp(height)));
                card.addView(body,new FrameLayout.LayoutParams(-1,-2));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
                cp.bottomMargin=dp(10);
                widgetsArea.addView(card,cp);
                shown++;
            }catch(RuntimeException error){
                android.util.Log.w("AuraHome","Unable to render widget "+id,error);
            }
        }
        if(shown==0)return;
        LinearLayout heading=new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=label("ВИДЖЕТЫ · СТРАНИЦА "+(homePage+1),12,true);
        label.setTextColor(0xffd0f4f4);
        heading.addView(label,new LinearLayout.LayoutParams(0,dp(38),1));
        TextView edit=label("Изменить  ›",12,false);
        edit.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        edit.setOnClickListener(v->openWidgetBoard());
        heading.addView(edit,new LinearLayout.LayoutParams(dp(100),dp(38)));
        container.addView(heading,new LinearLayout.LayoutParams(-1,dp(38)));
        container.addView(widgetsArea,new LinearLayout.LayoutParams(-1,-2));
        if(hidden>0){
            TextView extra=label("Ещё "+hidden+" виджетов · открыть редактор",11,false);
            extra.setGravity(Gravity.CENTER);
            extra.setOnClickListener(v->openWidgetBoard());
            container.addView(extra,new LinearLayout.LayoutParams(-1,dp(34)));
        }
    }
    private void showHome(){
        if(island!=null)island.pause();
        root.removeAllViews();drawer=null;island=null;
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout vertical=new LinearLayout(this);
        vertical.setOrientation(LinearLayout.VERTICAL);
        vertical.setPadding(dp(17),dp(101),dp(17),dp(18));
        vertical.setOnApplyWindowInsetsListener((view,insets)->{
            int insetBottom=insets.getSystemWindowInsetBottom();
            view.setPadding(dp(17),Math.max(dp(101),insets.getSystemWindowInsetTop()+dp(68)),dp(17),Math.max(dp(18),dp(12)+insetBottom));
            return insets;
        });
        scroll.addView(vertical,new ScrollView.LayoutParams(-1,-2));

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts=new LinearLayout(this);texts.setOrientation(LinearLayout.VERTICAL);
        clock=label("",42,false);clock.setTypeface(Typeface.create("sans-serif-light",0));
        clock.setShadowLayer(dp(6),0,dp(2),0x99000000);
        texts.addView(clock);
        date=label("",12,false);
        date.setShadowLayer(dp(4),0,dp(1),0x99000000);
        texts.addView(date);
        header.addView(texts,new LinearLayout.LayoutParams(0,dp(88),1));
        TextView widgetButton=label("▦",27,false);
        widgetButton.setGravity(Gravity.CENTER);
        widgetButton.setContentDescription("Открыть страницу виджетов AURA");
        widgetButton.setOnClickListener(v->openWidgetBoard());
        header.addView(widgetButton,new LinearLayout.LayoutParams(dp(50),dp(50)));
        TextView gear=label("⚙",28,false);
        gear.setGravity(Gravity.CENTER);
        gear.setContentDescription("Открыть редактор AURA");
        gear.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
        header.addView(gear,new LinearLayout.LayoutParams(dp(50),dp(50)));
        vertical.addView(header,new LinearLayout.LayoutParams(-1,dp(88)));

        weatherCard=new WeatherCardView(this);
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,dp(150));
        wp.topMargin=dp(13);
        vertical.addView(weatherCard,wp);

        GlassPanel search=new GlassPanel(this);
        TextView searchText=label("⌕    Поиск приложений",14,false);
        searchText.setGravity(Gravity.CENTER_VERTICAL);
        searchText.setPadding(dp(19),0,0,0);
        search.addView(searchText,new FrameLayout.LayoutParams(-1,-1));
        search.setOnClickListener(v->openDrawer());
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(48));
        sp.topMargin=dp(15);vertical.addView(search,sp);

        vertical.addView(new View(this),new LinearLayout.LayoutParams(-1,0,1));
        showPageStrip(vertical);
        GlassPanel shortPanel=new GlassPanel(this);
        shortPanel.setPadding(dp(8),dp(8),dp(8),dp(5));
        GridView favorites=icons(favorites(),4,true,false);
        favorites.setContentDescription("Ярлыки страницы "+(homePage+1));
        shortPanel.addView(favorites,new FrameLayout.LayoutParams(-1,dp(153)));
        vertical.addView(shortPanel,new LinearLayout.LayoutParams(-1,dp(167)));
        showInlineWidgets(vertical);

        TextView more=label("⌃   Все приложения",13,true);
        more.setGravity(Gravity.CENTER);
        more.setShadowLayer(dp(4),0,1,0x77000000);
        more.setOnClickListener(v->openDrawer());
        vertical.addView(more,new LinearLayout.LayoutParams(-1,dp(42)));

        GlassPanel dock=new GlassPanel(this);
        dock.setPadding(dp(8),dp(4),dp(8),dp(4));
        List<ResolveInfo> common=dockEntries();
        dock.addView(icons(common,5,false,true),new FrameLayout.LayoutParams(-1,dp(71)));
        vertical.addView(dock,new LinearLayout.LayoutParams(-1,dp(80)));
        if(Config.bool(this,"island_enabled",true)){
            island=new AuraIslandView(this);
            int collapsedWidth=Math.min(getResources().getDisplayMetrics().widthPixels-dp(62),dp(258));
            FrameLayout.LayoutParams islandLp=new FrameLayout.LayoutParams(collapsedWidth,dp(57),
                Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            islandLp.topMargin=dp(34);
            root.addView(island,islandLp);
            island.setOnApplyWindowInsetsListener((v,insets)->{
                FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)v.getLayoutParams();
                int safeTop=Math.max(dp(34),insets.getSystemWindowInsetTop()+dp(5));
                if(lp.topMargin!=safeTop){lp.topMargin=safeTop;v.setLayoutParams(lp);}
                return insets;
            });
            island.requestApplyInsets();
        }
        updateClock();
    }
    private GridView icons(List<ResolveInfo> items,int columns,boolean pinnable,boolean dock){
        GridView grid=new GridView(this);
        grid.setNumColumns(columns);
        grid.setGravity(Gravity.CENTER);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(android.R.color.transparent);
        grid.setVerticalSpacing(dp(4));
        grid.setHorizontalSpacing(dp(3));
        grid.setAdapter(pinnable?new HomeAdapter(items):new AppAdapter(items,pinnable,dock));
        return grid;
    }
    private void updateClock(){
        if(clock!=null)clock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
        if(date!=null)date.setText(new SimpleDateFormat("EEEE, d MMMM",new Locale("ru")).format(new Date()));
    }
    private void openDrawer(){
        if(drawer!=null)return;
        drawer=new FrameLayout(this);
        drawer.setBackgroundColor(0xf21b1b31);
        root.addView(drawer,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(17),dp(55),dp(17),dp(27));
        drawer.addView(content,new FrameLayout.LayoutParams(-1,-1));
        TextView title=label("Все приложения     ✕",22,true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setOnClickListener(v->closeDrawer(true));
        content.addView(title,new LinearLayout.LayoutParams(-1,dp(57)));
        TextView hint=label("Удерживайте иконку, чтобы закрепить на главном экране",12,false);
        hint.setTextColor(0xffbfc0d7);
        content.addView(hint,new LinearLayout.LayoutParams(-1,dp(36)));
        EditText filter=new EditText(this);
        filter.setSingleLine(true);filter.setTextColor(Color.WHITE);
        filter.setHintTextColor(0xffbbbace);filter.setHint("Поиск");
        content.addView(filter,new LinearLayout.LayoutParams(-1,dp(54)));
        List<ResolveInfo> results=new ArrayList<>(applications);
        GridView list=icons(results,4,false,false);
        AppAdapter adapter=(AppAdapter)list.getAdapter();
        LinearLayout.LayoutParams grid=new LinearLayout.LayoutParams(-1,0,1);
        grid.topMargin=dp(12);content.addView(list,grid);
        filter.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int n,int a){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                results.clear();
                String q=s.toString().toLowerCase(Locale.ROOT);
                for(ResolveInfo app:applications)
                    if(String.valueOf(app.loadLabel(getPackageManager())).toLowerCase(Locale.ROOT).contains(q))
                        results.add(app);
                adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable editable){}
        });
        drawer.setAlpha(0);
        drawer.animate().alpha(1).setDuration(230)
            .setInterpolator(new DecelerateInterpolator()).start();
    }
    private void closeDrawer(boolean animate){
        FrameLayout old=drawer;
        if(old==null)return;
        drawer=null;
        if(animate){
            old.animate().alpha(0).setDuration(160)
                .withEndAction(()->root.removeView(old)).start();
        }else root.removeView(old);
    }
    private void launch(ResolveInfo app){
        try{
            Intent intent=new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(app.activityInfo.packageName,app.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }catch(Exception e){
            Toast.makeText(this,"Приложение не открылось",Toast.LENGTH_SHORT).show();
        }
    }
    private final class AppAdapter extends BaseAdapter {
        private final List<ResolveInfo> items;
        private final boolean onHome,dock;
        AppAdapter(List<ResolveInfo> items,boolean onHome,boolean dock){
            this.items=items;this.onHome=onHome;this.dock=dock;
        }
        @Override public int getCount(){return items.size();}
        @Override public Object getItem(int position){return items.get(position);}
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View recycled,ViewGroup parent){
            ResolveInfo entry=items.get(position);
            LinearLayout cell=new LinearLayout(HomeActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(dp(3),dp(4),dp(3),dp(2));
            ImageView image=new ImageView(HomeActivity.this);
            try{image.setImageDrawable(entry.loadIcon(getPackageManager()));}
            catch(Exception ignored){}
            cell.addView(image,new LinearLayout.LayoutParams(dp(dock?43:44),dp(dock?43:44)));
            TextView text=label(String.valueOf(entry.loadLabel(getPackageManager())),dock?9:10,false);
            text.setSingleLine(true);
            text.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.setGravity(Gravity.CENTER);
            text.setShadowLayer(dp(2),0,1,0x88000000);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(22));
            cell.addView(text,lp);
            cell.setOnClickListener(v->launch(entry));
            cell.setOnLongClickListener(v->{
                String[] options;
                if(dock)options=new String[]{"Сдвинуть влево","Сдвинуть вправо","Убрать из дока"};
                else if(onHome)options=new String[]{"Сдвинуть влево","Сдвинуть вправо","Убрать с главного","Создать папку","Добавить в папку","На другую страницу"};
                else options=new String[]{"Закрепить на главном","Добавить в док","Создать папку","Добавить в папку"};
                new AlertDialog.Builder(HomeActivity.this)
                    .setTitle(entry.loadLabel(getPackageManager()))
                    .setItems(options,(dialog,which)->{
                        if(dock||onHome){
                            if(which==0)shiftItem(entry,dock,-1);
                            else if(which==1)shiftItem(entry,dock,1);
                            else if(which==2){
                                if(dock)changeDock(entry,false);
                                else changeFavorite(entry,false);
                            }else if(which==3)createFolder(entry);
                            else if(which==4)chooseFolder(entry);
                            else if(which==5)moveShortcutToOtherPage(entry);
                        }else if(which==0)changeFavorite(entry,true);
                        else if(which==1)changeDock(entry,true);
                        else if(which==2)createFolder(entry);
                        else if(which==3)chooseFolder(entry);
                    })
                    .setNegativeButton("Отмена",null)
                    .show();
                return true;
            });
            return cell;
        }
    }
    private void refreshInstalledApps(){
        ArrayList<String> before=new ArrayList<>();
        for(ResolveInfo item:applications)before.add(componentKey(item));
        loadApplications();
        ArrayList<String> after=new ArrayList<>();
        for(ResolveInfo item:applications)after.add(componentKey(item));
        if(!before.equals(after))showHome();
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        if(intent!=null&&Intent.ACTION_MAIN.equals(intent.getAction())&&
           intent.hasCategory(Intent.CATEGORY_HOME)&&drawer!=null)closeDrawer(true);
    }
    @Override protected void onResume(){
        super.onResume();
        RefreshRateSupport.applyToActivity(this);
        if(!widgetListening&&desktopWidgetHost!=null){
            try{desktopWidgetHost.startListening();widgetListening=true;}
            catch(RuntimeException error){
                android.util.Log.w("AuraHome","Widget host cannot listen",error);
            }
        }
        String layout=widgetLayoutState();
        if(!layout.equals(widgetLayoutSnapshot)){
            widgetLayoutSnapshot=layout;
            showHome();
        }
        refreshInstalledApps();
        timer.removeCallbacks(clockTick);clockTick.run();
        WeatherRepository.refresh(this,false,(ok,msg)->{
            if(weatherCard!=null)weatherCard.refresh();
        });
        if(weatherCard!=null)weatherCard.refresh();
        if((island!=null)!=Config.bool(this,"island_enabled",true))showHome();
        if(island!=null)island.resume();
    }
    @Override protected void onPause(){
        if(island!=null)island.pause();
        if(widgetListening&&desktopWidgetHost!=null){
            try{desktopWidgetHost.stopListening();}
            catch(RuntimeException ignored){}
            widgetListening=false;
        }
        timer.removeCallbacks(clockTick);super.onPause();
    }
    @Override public void onBackPressed(){
        if(drawer!=null)closeDrawer(true);
        // The default HOME activity stays open on back, like regular launchers.
    }
}
