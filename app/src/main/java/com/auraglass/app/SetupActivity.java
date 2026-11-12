package com.auraglass.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Switch;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** The app icon opens onboarding, not the editor, so installation is obvious. */
public final class SetupActivity extends Activity {
    private TextView launcherStatus;
    private TextView wallpaperStatus;
    private TextView subtitle;
    private AtmospherePreviewView preview;
    private TextView updateStatus,updateHeader,updateDescription;
    private Button downloadUpdate,installUpdate;
    private LinearLayout updateBanner;
    private Switch notificationUpdates;
    private UpdateCenter.Release pendingRelease;
    private boolean visible=false,checkedEntry=false;
    private final Handler updateHandler=new Handler(Looper.getMainLooper());
    private final Runnable updateProgress=new Runnable(){
        @Override public void run(){
            if(!visible)return;
            UpdateCenter.downloadStatus(SetupActivity.this,(message,ready)->{
                if(updateStatus!=null&&message.length()>0)updateStatus.setText(message);
                if(installUpdate!=null)installUpdate.setVisibility(ready?View.VISIBLE:View.GONE);
            });
            updateHandler.postDelayed(this,1800);
        }
    };

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + .5f);
    }
    private GradientDrawable surface(int color, int radius) {
        GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{color, 0x885b6087});
        background.setCornerRadius(dp(radius));
        background.setStroke(dp(1), 0x58ffffff);
        return background;
    }
    private TextView title(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }
    private LinearLayout column() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }
    private void gap(LinearLayout layout, int height) {
        layout.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }
    private Button action(String value, Runnable click) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setBackground(surface(0x9a8d81bf, 18));
        button.setOnClickListener(v -> click.run());
        return button;
    }
    private void addButton(LinearLayout parent, String value, Runnable click) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.topMargin = dp(9);
        parent.addView(action(value, click), params);
    }
    private LinearLayout section(LinearLayout main, String header, String details) {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(15), dp(16), dp(17));
        card.setBackground(surface(0xa03c466b, 24));
        TextView heading = title(header, 20, true);
        card.addView(heading);
        TextView description = title(details, 12, false);
        description.setTextColor(0xffc8c6df);
        LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(-1, -2);
        d.topMargin = dp(4);
        card.addView(description, d);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(13);
        main.addView(card, p);
        return card;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff151b32);
        getWindow().setNavigationBarColor(0xff17172b);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff10182c, 0xff3a3554, 0xff191b32}));
        LinearLayout main = column();
        main.setPadding(dp(18), dp(25), dp(18), dp(32));
        scroll.addView(main);
        setContentView(scroll);

        LinearLayout titleRow=new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = title("AURA GLASS", 32, true);
        brand.setLetterSpacing(.09f);
        titleRow.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button quickTest=action("Тест",()->
            startActivity(new Intent(this,DiagnosticsActivity.class)));
        quickTest.setContentDescription("Диагностика AURA");
        titleRow.addView(quickTest,new LinearLayout.LayoutParams(dp(95),dp(44)));
        main.addView(titleRow,new LinearLayout.LayoutParams(-1,-2));
        TextView tag = title("ЖИВОЕ СТЕКЛО ДЛЯ ANDROID", 11, false);
        tag.setTextColor(0xffe6caea);
        main.addView(tag);
        gap(main, 14);
        updateBanner=column();
        updateBanner.setPadding(dp(15),dp(14),dp(15),dp(15));
        updateBanner.setBackground(surface(0xb0536596,22));
        updateBanner.setVisibility(View.GONE);
        updateHeader=title("Доступно обновление AURA",19,true);
        updateBanner.addView(updateHeader);
        updateDescription=title("Новый стабильный релиз на GitHub",12,false);
        updateDescription.setTextColor(0xffdae2fa);
        updateBanner.addView(updateDescription);
        downloadUpdate=action("Скачать новую версию",()->downloadUpdate());
        addUpdateAction(updateBanner,downloadUpdate);
        installUpdate=action("Проверить APK и установить",()->
            UpdateCenter.installDownloaded(this,(message,ready)->{
                updateStatus.setText(message);
                installUpdate.setVisibility(ready?View.VISIBLE:View.GONE);
            }));
        installUpdate.setVisibility(View.GONE);
        addUpdateAction(updateBanner,installUpdate);
        main.addView(updateBanner,new LinearLayout.LayoutParams(-1,-2));
        preview = new AtmospherePreviewView(this);
        main.addView(preview, new LinearLayout.LayoutParams(-1, dp(225)));
        gap(main, 14);
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        Button quickHome = action("Включить лаунчер", () -> Activation.requestHome(this));
        Button quickWallpaper = action("Живые обои", () -> Activation.requestWallpaper(this));
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(56), 1);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(56), 1);
        right.leftMargin = dp(9);
        quick.addView(quickHome, left);
        quick.addView(quickWallpaper, right);
        main.addView(quick, new LinearLayout.LayoutParams(-1, dp(56)));
        gap(main, 14);
        subtitle = title("Выберите, что применять. Ваши системные обои сохранятся до подтверждения замены.", 13, false);
        subtitle.setTextColor(0xffe4deef);
        main.addView(subtitle);

        LinearLayout launcher = section(main, "01 · Рабочий стол AURA",
                "Заменяет только главный экран. Возврат к Samsung One UI Home через настройки Android.");
        launcherStatus = title("Проверяем…", 13, true);
        launcher.addView(launcherStatus);
        addButton(launcher, "Сделать AURA главным экраном", () -> Activation.requestHome(this));
        // One UI may dismiss or suppress the role picker. Keep an explicit
        // OEM-independent route visible instead of relying on a transient toast.
        addButton(launcher, "Открыть системный выбор лаунчера", () ->
                Activation.chooseSystemHome(this));
        addButton(launcher, "Посмотреть рабочий стол", () ->
                startActivity(new Intent(this, HomeActivity.class)));
        addButton(launcher, "Вернуть One UI Home", () -> Activation.chooseSystemHome(this));

        LinearLayout wallpaper = section(main, "02 · Живые обои",
                "Живой дождь, снег, лёд и сияние. Можно оставить текущие обои.");
        wallpaperStatus = title("Проверяем…", 13, true);
        wallpaper.addView(wallpaperStatus);
        addButton(wallpaper, "Установить живые обои AURA", () -> Activation.requestWallpaper(this));
        addButton(wallpaper, "Оставить / выбрать системные обои", () ->
                Activation.chooseSystemWallpaper(this));

        LinearLayout personalize = section(main, "03 · Настройка атмосферы",
                "Ползунки, погода и время суток с предпросмотром в реальном времени.");
        addButton(personalize, "Открыть редактор темы", () ->
                startActivity(new Intent(this, MainActivity.class)));
        addButton(personalize, "Перепроверить состояние", this::updateStatus);
        LinearLayout islandSection = section(main,"04 · AURA Island",
            "Музыка, звонки, уведомления, аудиоволна и микшер на рабочем столе.");
        android.widget.Switch islandToggle=new android.widget.Switch(this);
        islandToggle.setText("Показывать AURA Island");
        islandToggle.setTextColor(Color.WHITE);
        islandToggle.setChecked(Config.bool(this,"island_enabled",true));
        islandToggle.setOnCheckedChangeListener((button,checked)->
            Config.put(this,"island_enabled",checked));
        islandSection.addView(islandToggle,new LinearLayout.LayoutParams(-1,dp(49)));
        android.widget.Switch callerToggle=new android.widget.Switch(this);
        callerToggle.setText("Показывать имя и номер звонящего");
        callerToggle.setTextColor(Color.WHITE);
        callerToggle.setTextSize(13);
        callerToggle.setChecked(Config.bool(this,"island_show_caller",false));
        callerToggle.setOnCheckedChangeListener((button,checked)->
            Config.put(this,"island_show_caller",checked));
        islandSection.addView(callerToggle,new LinearLayout.LayoutParams(-1,dp(49)));
        android.widget.Switch noticeToggle=new android.widget.Switch(this);
        noticeToggle.setText("Показывать текст уведомлений");
        noticeToggle.setTextColor(Color.WHITE);
        noticeToggle.setTextSize(13);
        noticeToggle.setChecked(Config.bool(this,"island_show_notification_text",false));
        noticeToggle.setOnCheckedChangeListener((button,checked)->
            Config.put(this,"island_show_notification_text",checked));
        islandSection.addView(noticeToggle,new LinearLayout.LayoutParams(-1,dp(49)));
        TextView restricted=title("На Android 13+ доступ к уведомлениям для APK из GitHub может быть ограничен самой системой. Не меняйте защитные настройки, пока не проверите источник и подпись APK.",11,false);
        restricted.setSingleLine(false);
        restricted.setTextColor(0xffc2cedc);
        islandSection.addView(restricted,new LinearLayout.LayoutParams(-1,-2));
        addButton(islandSection,"Разрешить доступ к уведомлениям",()->{
            try{startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}
            catch(RuntimeException ignored){}
        });
        addButton(islandSection,"Открыть микшер громкости",()->AuraAudioMixer.show(this));

        LinearLayout updates=section(main,"05 · Обновления AURA",
            "Проверка только при открытии приложения. Никаких фоновых проверок и автоматической установки.");
        updateStatus=title("Проверка ещё не выполнялась",12,false);
        updateStatus.setTextColor(0xffd4ddf4);
        updates.addView(updateStatus,new LinearLayout.LayoutParams(-1,-2));

        Switch onOpen=new Switch(this);
        onOpen.setText("Проверять при запуске AURA");
        onOpen.setTextColor(Color.WHITE);
        onOpen.setTextSize(13);
        onOpen.setChecked(Config.bool(this,"updates_on_open",true));
        onOpen.setOnCheckedChangeListener((button,checked)->
            Config.put(this,"updates_on_open",checked));
        updates.addView(onOpen,new LinearLayout.LayoutParams(-1,dp(50)));

        notificationUpdates=new Switch(this);
        notificationUpdates.setText("Показывать обновления в шторке");
        notificationUpdates.setTextSize(13);
        notificationUpdates.setTextColor(Color.WHITE);
        notificationUpdates.setChecked(Config.bool(this,"updates_notification",false));
        notificationUpdates.setOnCheckedChangeListener((button,checked)->{
            Config.put(this,"updates_notification",checked);
            if(checked&&Build.VERSION.SDK_INT>=33&&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
                PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},763);
        });
        updates.addView(notificationUpdates,new LinearLayout.LayoutParams(-1,dp(50)));
        addButton(updates,"Проверить сейчас",this::checkUpdate);
        addButton(updates,"Открыть GitHub Releases",()->UpdateCenter.openReleases(this));
        addButton(updates,"Установить скачанный APK",()->
            UpdateCenter.installDownloaded(this,(message,ready)->{
                updateStatus.setText(message);
                if(installUpdate!=null)installUpdate.setVisibility(ready?View.VISIBLE:View.GONE);
            }));

        LinearLayout backups=section(main,"06 · Резервная копия",
            "Экспорт и восстановление темы, настроек и ярлыков через файлы Android. "+
            "Приватные данные уведомлений и установочные файлы не копируются.");
        addButton(backups,"Сохранить настройки в JSON",()->SettingsBackup.export(this));
        addButton(backups,"Восстановить настройки из JSON",()->
            SettingsBackup.importFromFile(this));

        LinearLayout diagnostics=section(main,"07 · Диагностика устройства",
            "Проверка Home, обоев, разрешений, дисплея и времени кадров без отправки данных.");
        addButton(diagnostics,"Открыть диагностику AURA",()->
            startActivity(new Intent(this,DiagnosticsActivity.class)));

        gap(main, 20);
        TextView note = title("Тестовая версия. Системная шторка и экран блокировки остаются под управлением One UI.", 11, false);
        note.setTextColor(0xffc7bfd6);
        main.addView(note);
        updateStatus();
        if(Config.bool(this,"updates_on_open",true))checkUpdate();
    }
    private void addUpdateAction(LinearLayout parent,Button button){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));
        p.topMargin=dp(9);
        parent.addView(button,p);
    }
    private void checkUpdate(){
        if(updateStatus!=null)updateStatus.setText("Проверяю официальный стабильный релиз GitHub…");
        UpdateCenter.checkOnEntry(this,(release,explanation)->{
            if(release==null){
                if(updateStatus!=null)updateStatus.setText(
                    explanation==null?"Новых стабильных версий нет":explanation);
                return;
            }
            pendingRelease=release;
            updateBanner.setVisibility(View.VISIBLE);
            updateHeader.setText("Новая версия AURA GLASS "+release.tag);
            updateDescription.setText("Официальный стабильный APK. Установка — только с подтверждением Android.");
            updateStatus.setText("Доступно обновление "+release.tag);
            UpdateCenter.downloadStatus(this,(message,ready)->{
                if(ready)installUpdate.setVisibility(View.VISIBLE);
            });
        });
    }
    private void downloadUpdate(){
        if(pendingRelease==null)return;
        if(UpdateCenter.beginDownload(this,pendingRelease)){
            updateStatus.setText("Загрузка "+pendingRelease.tag+" запущена через Android");
            downloadUpdate.setText("Загрузить заново");
            if(installUpdate!=null)installUpdate.setVisibility(View.GONE);
        }
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==SettingsBackup.EXPORT_REQUEST||
           requestCode==SettingsBackup.IMPORT_REQUEST)
            SettingsBackup.onResult(this,requestCode,resultCode,data);
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==763&&Build.VERSION.SDK_INT>=33&&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=
            PackageManager.PERMISSION_GRANTED){
            Config.put(this,"updates_notification",false);
            if(notificationUpdates!=null)notificationUpdates.setChecked(false);
        }
    }

    private void updateStatus() {
        if (launcherStatus == null) return;
        boolean home = Activation.isHomeActive(this);
        boolean wallpaper = Activation.isLiveWallpaperActive(this);
        launcherStatus.setText(home ? "✓ Активен как домашний экран" : "○ Не выбран домашним экраном");
        launcherStatus.setTextColor(home ? 0xffb4f2d6 : 0xffffe0c2);
        wallpaperStatus.setText(wallpaper ? "✓ Живые обои AURA установлены" :
                Activation.isWallpaperServiceRegistered(this) ?
                "○ Не установлены · требуется подтверждение Android" : "⚠ Сервис обоев не зарегистрирован");
        wallpaperStatus.setTextColor(wallpaper ? 0xffb4f2d6 : 0xffffe0c2);
        subtitle.setText(home || wallpaper ?
                "Настройка применена. Параметры эффектов можно менять без повторной установки." :
                "Сначала активируйте нужный компонент. Ничего не заменяется автоматически.");
        if (preview != null) preview.reload();
    }
    @Override protected void onResume() {
        super.onResume();
        RefreshRateSupport.applyToActivity(this);
        visible=true;
        updateStatus();
        updateHandler.removeCallbacks(updateProgress);
        updateHandler.post(updateProgress);
    }
    @Override protected void onPause(){
        visible=false;
        updateHandler.removeCallbacks(updateProgress);
        super.onPause();
    }
}
