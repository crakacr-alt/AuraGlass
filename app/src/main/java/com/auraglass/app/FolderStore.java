package com.auraglass.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistent launcher folders, separate from app discovery and widget IDs.
 * The launcher never fabricates a shortcut: each entry is a real component.
 */
public final class FolderStore {
    private static final String KEY="home_folders";
    private static final int MAX_FOLDERS=8;
    private static final int MAX_ITEMS=16;
    private FolderStore(){}
    public static final class Folder {
        public final String id;
        public String name;
        public final ArrayList<String> items=new ArrayList<>();
        private Folder(String id,String name){this.id=id;this.name=name;}
    }
    private static String safeName(String name){
        if(name==null)return "Папка";
        String clean=name.trim().replace('\n',' ');
        return clean.length()>24?clean.substring(0,24):clean.isEmpty()?"Папка":clean;
    }
    public static List<Folder> all(Context context){
        ArrayList<Folder> result=new ArrayList<>();
        try{
            JSONArray array=new JSONArray(Config.str(context,KEY,"[]"));
            for(int i=0;i<Math.min(array.length(),MAX_FOLDERS);i++){
                JSONObject data=array.optJSONObject(i);
                if(data==null)continue;
                String id=data.optString("id","");
                if(id.isEmpty()||id.length()>72)continue;
                Folder folder=new Folder(id,safeName(data.optString("name","Папка")));
                JSONArray apps=data.optJSONArray("items");
                if(apps!=null)for(int j=0;j<Math.min(apps.length(),MAX_ITEMS);j++){
                    String key=apps.optString(j,"");
                    if(key.length()>0&&key.length()<260&&!folder.items.contains(key))
                        folder.items.add(key);
                }
                result.add(folder);
            }
        }catch(Exception ignored){}
        return result;
    }
    private static void save(Context context,List<Folder> folders){
        JSONArray array=new JSONArray();
        for(Folder folder:folders){
            if(array.length()>=MAX_FOLDERS)break;
            try{
                JSONObject data=new JSONObject();
                data.put("id",folder.id);
                data.put("name",safeName(folder.name));
                JSONArray items=new JSONArray();
                for(String key:folder.items){
                    if(items.length()>=MAX_ITEMS)break;
                    items.put(key);
                }
                data.put("items",items);
                array.put(data);
            }catch(Exception ignored){}
        }
        Config.put(context,KEY,array.toString());
    }
    public static boolean create(Context ctx,String label,String firstApp){
        List<Folder> folders=all(ctx);
        if(folders.size()>=MAX_FOLDERS||firstApp==null||firstApp.isEmpty())return false;
        Folder folder=new Folder(UUID.randomUUID().toString(),safeName(label));
        folder.items.add(firstApp);
        folders.add(folder);
        save(ctx,folders);
        return true;
    }
    public static boolean add(Context ctx,String id,String appKey){
        List<Folder> folders=all(ctx);
        if(appKey==null||appKey.isEmpty())return false;
        for(Folder folder:folders)if(folder.id.equals(id)){
            if(folder.items.contains(appKey))return true;
            if(folder.items.size()>=MAX_ITEMS)return false;
            folder.items.add(appKey);
            save(ctx,folders);
            return true;
        }
        return false;
    }
    public static void removeItem(Context ctx,String id,String appKey){
        List<Folder> folders=all(ctx);
        for(Folder folder:folders)if(folder.id.equals(id)){
            folder.items.remove(appKey);
            if(folder.items.isEmpty())folders.remove(folder);
            save(ctx,folders);
            return;
        }
    }
    public static void rename(Context ctx,String id,String name){
        List<Folder> folders=all(ctx);
        for(Folder folder:folders)if(folder.id.equals(id)){
            folder.name=safeName(name);save(ctx,folders);return;
        }
    }
    public static void delete(Context ctx,String id){
        List<Folder> folders=all(ctx);
        folders.removeIf(folder->folder.id.equals(id));
        save(ctx,folders);
    }
}
