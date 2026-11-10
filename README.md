# AURA GLASS · Android Launcher & Live Atmosphere

A native Android prototype for a Samsung/One UI-friendly launcher, living wallpaper, glass editor, and weather.

## Current milestone: 0.1.0-alpha

- Fullscreen wallpaper renderer with deterministic rain, snow, frost, mist, stars, aurora, moon/sun and atmospheric gradients
- Smooth time-of-day color changes and 3.4-second weather-effect crossfades; frame rate adapts to power saver and slow render frames
- Android live wallpaper service; installed only after the user's system confirmation
- AURA Home launcher with installed apps, app drawer, search, a custom animated weather panel
- Native in-app preview and preference controls for weather, time, effects, animation speed, opacity and FPS
- Android homescreen widget (static RemoteViews snapshot; ordinary external widgets cannot run a 60 FPS custom Canvas animation)
- Weather via Open-Meteo: select a city or grant **approximate** location permission
- Weather widget / card attempts to open Samsung Weather, falling back to AURA if not available
- Original/system wallpaper remains unchanged **unless the user explicitly confirms AURA live wallpaper**; to restore earlier wallpaper, the user uses Android's wallpaper picker
- AURA Home default launcher is selected through Android's HOME role/settings, and can be reverted to One UI Home there

## Build

- Android Studio: open repository, sync Gradle, run `app`
- GitHub Actions: **Actions → Build AuraGlass APK → Run workflow**, or push on `main`
- Download `AuraGlass-v0.1-alpha-debug` from the workflow run's **Artifacts**
- Android 8.0+ (API 26); pure Java/Dex, no architecture-specific native dependencies, so compatible with ARMv7 and arm64 under supported Android versions

## Limitations / quality gates

This is an **early functional prototype**, not yet the photorealistic visual fidelity of the reference illustrations. The next stages require Android device screenshots, performance measurements, real-device navigation/gesture testing, real wallpaper integration checks and Samsung Weather intent verification. The interface does **not** reskin Samsung Quick Settings, lockscreen or other apps; these are system-managed UI.

Safety and privacy: no root, no overlays above other apps, no accessibility service, no wallpaper replacement in the background, no tracking SDK. Coordinates are only sent to Open-Meteo for an opted-in weather forecast. Network calls occur off the main thread. Live wallpaper rendering pauses when invisible; power saver lowers frame rate.
