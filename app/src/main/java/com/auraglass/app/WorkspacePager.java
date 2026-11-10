package com.auraglass.app;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Small, testable persistence layer for Home workspaces.
 * Page 0 continues using the legacy pinned_apps preference to avoid data loss.
 * Other pages use home_page_1 ... home_page_3.
 */
public final class WorkspacePager {
    public static final int MAX_PAGES=4;
    public static final int SLOTS=8;
    private static final String COUNT="home_pages_count";
    private WorkspacePager(){}

    public static int pageCount(Context context){
        return Math.max(1,Math.min(MAX_PAGES,
            (int)Config.number(context,COUNT,1)));
    }
    public static boolean addPage(Context context){
        int count=pageCount(context);
        if(count>=MAX_PAGES)return false;
        Config.put(context,COUNT,count+1);
        Config.put(context,"home_page_"+count,"");
        return true;
    }
    public static boolean deleteLastEmpty(Context context){
        int count=pageCount(context);
        if(count<=1||!shortcuts(context,count-1).isEmpty())return false;
        Config.put(context,COUNT,count-1);
        Config.prefs(context).edit().remove("home_page_"+(count-1)).apply();
        return true;
    }
    private static String key(int page){
        if(page<0||page>=MAX_PAGES)throw new IllegalArgumentException("Page out of range");
        return page==0?"pinned_apps":"home_page_"+page;
    }
    public static List<String> shortcuts(Context context,int page){
        ArrayList<String> result=new ArrayList<>();
        String value=Config.str(context,key(page),"");
        Set<String> unique=new HashSet<>();
        for(String item:value.split(";")){
            if(item.length()>0&&item.length()<260&&unique.add(item)){
                result.add(item);
                if(result.size()>=SLOTS)break;
            }
        }
        return result;
    }
    public static void save(Context context,int page,List<String> shortcuts){
        if(page>=pageCount(context))throw new IllegalArgumentException("Page missing");
        StringBuilder content=new StringBuilder();
        Set<String> unique=new HashSet<>();
        for(String item:shortcuts){
            if(item==null||item.isEmpty()||item.length()>=260||!unique.add(item))continue;
            if(unique.size()>SLOTS)break;
            if(content.length()>0)content.append(';');
            content.append(item);
        }
        Config.put(context,key(page),content.toString());
    }
    public static boolean hasAnywhere(Context context,String component){
        for(int page=0;page<pageCount(context);page++)
            if(shortcuts(context,page).contains(component))return true;
        return false;
    }
    public static boolean add(Context context,int page,String component,int maximum){
        if(component==null||component.isEmpty()||page>=pageCount(context))
            return false;
        if(hasAnywhere(context,component))return false;
        List<String> list=shortcuts(context,page);
        if(list.size()>=Math.min(SLOTS,Math.max(0,maximum)))return false;
        list.add(component);
        save(context,page,list);
        return true;
    }
    public static boolean removeEverywhere(Context context,String component){
        boolean changed=false;
        for(int page=0;page<pageCount(context);page++){
            List<String> list=shortcuts(context,page);
            if(list.remove(component)){
                save(context,page,list);
                changed=true;
            }
        }
        return changed;
    }
    public static boolean move(Context context,int fromPage,int fromIndex,int toPage,int toIndex,int toCapacity){
        if(fromPage<0||toPage<0||fromPage>=pageCount(context)||
           toPage>=pageCount(context))return false;
        List<String> source=shortcuts(context,fromPage);
        if(fromIndex<0||fromIndex>=source.size())return false;
        String moving=source.get(fromIndex);
        int safeCapacity=Math.max(0,Math.min(SLOTS,toCapacity));
        if(fromPage==toPage){
            source.remove(fromIndex);
            source.add(Math.min(Math.max(0,toIndex),source.size()),moving);
            save(context,fromPage,source);
            return true;
        }
        List<String> dest=shortcuts(context,toPage);
        if(dest.size()>=safeCapacity||dest.contains(moving))return false;
        // All validation happens before source removal.
        source.remove(fromIndex);
        dest.add(Math.min(Math.max(0,toIndex),dest.size()),moving);
        save(context,fromPage,source);
        save(context,toPage,dest);
        return true;
    }
}
