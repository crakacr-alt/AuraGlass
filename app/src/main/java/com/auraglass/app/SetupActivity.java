package com.auraglass.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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

        TextView brand = title("AURA GLASS", 32, true);
        brand.setLetterSpacing(.14f);
        main.addView(brand);
        TextView tag = title("ЖИВОЕ СТЕКЛО ДЛЯ ANDROID", 11, false);
        tag.setTextColor(0xffe6caea);
        main.addView(tag);
        gap(main, 14);
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
        addButton(islandSection,"Разрешить доступ к уведомлениям",()->{
            try{startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}
            catch(RuntimeException ignored){}
        });
        addButton(islandSection,"Открыть микшер громкости",()->AuraAudioMixer.show(this));
        gap(main, 20);
        TextView note = title("Тестовая версия. Системная шторка и экран блокировки остаются под управлением One UI.", 11, false);
        note.setTextColor(0xffc7bfd6);
        main.addView(note);
        updateStatus();
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
        updateStatus();
    }
}
