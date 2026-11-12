package com.auraglass.app;

import android.app.Activity;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/** Supported activation paths only. Neither HOME nor wallpaper can be silently replaced. */
public final class Activation {
    private Activation() {}

    public static boolean isHomeActive(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager roles = (RoleManager) ctx.getSystemService(Context.ROLE_SERVICE);
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    return roles.isRoleHeld(RoleManager.ROLE_HOME);
                }
            }
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo info = ctx.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return info != null && info.activityInfo != null
                    && ctx.getPackageName().equals(info.activityInfo.packageName)
                    && HomeActivity.class.getName().equals(info.activityInfo.name);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isLiveWallpaperActive(Context ctx) {
        try {
            WallpaperInfo current = WallpaperManager.getInstance(ctx).getWallpaperInfo();
            return current != null && ctx.getPackageName().equals(current.getPackageName())
                    && AuraWallpaperService.class.getName().equals(current.getServiceName());
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isWallpaperServiceRegistered(Context ctx) {
        try {
            ComponentName component = new ComponentName(ctx, AuraWallpaperService.class);
            ctx.getPackageManager().getServiceInfo(component, PackageManager.GET_META_DATA);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void requestHome(Activity activity) {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager roles = (RoleManager) activity.getSystemService(Context.ROLE_SERVICE);
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                    Toast.makeText(activity, "AURA уже выбрана домашним экраном", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (launch(activity, roles.createRequestRoleIntent(RoleManager.ROLE_HOME))) {
                    Toast.makeText(activity, "Если окно выбора не появилось, откройте настройки главного экрана", Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
        if (!launch(activity, new Intent(Settings.ACTION_HOME_SETTINGS))) {
            if (!launch(activity, new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)))
                explain(activity, "Откройте Настройки → Приложения → Приложения по умолчанию → Главный экран");
        }
    }

    public static void requestWallpaper(Activity activity) {
        Intent preview = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        preview.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(activity, AuraWallpaperService.class));
        if (launch(activity, preview)) return;
        if (launch(activity, new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))) return;
        if (launch(activity, new Intent(Intent.ACTION_SET_WALLPAPER))) return;
        explain(activity, "Откройте Настройки → Обои и стиль → Сменить обои → Живые обои → AURA");
    }

    public static void chooseSystemWallpaper(Activity activity) {
        if (!launch(activity, new Intent(Intent.ACTION_SET_WALLPAPER))) {
            explain(activity, "Откройте Настройки → Обои и стиль");
        }
    }

    public static void chooseSystemHome(Activity activity) {
        if (!launch(activity, new Intent(Settings.ACTION_HOME_SETTINGS))) {
            explain(activity, "Откройте Настройки → Приложения → Приложения по умолчанию → Главный экран");
        }
    }

    private static boolean launch(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (RuntimeException exception) {
            android.util.Log.w("AuraActivation", "System UI not available: " + intent.getAction(), exception);
            return false;
        }
    }

    private static void explain(Context ctx, String message) {
        Toast.makeText(ctx, message, Toast.LENGTH_LONG).show();
    }
}
