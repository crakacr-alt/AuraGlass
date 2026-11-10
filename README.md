# AURA GLASS · Android Launcher & Live Atmosphere

Native Android prototype for an adaptive Samsung-friendly launcher, animated wallpapers, glass editor and weather.

## v0.2.2 alpha — installation and activation

The **app icon opens a dedicated setup screen** with separate controls and status indicators for the launcher and live wallpaper. No root or system modifications are involved.

1. Install the APK from [v0.2.2-alpha Releases](https://github.com/crakacr-alt/AuraGlass/releases/tag/v0.2.2-alpha).
2. Open AURA GLASS from your application list.
3. Choose **Сделать AURA главным экраном** and confirm Android's home app dialog.
4. Independently choose **Установить живые обои AURA**, and select AURA Live Atmosphere in Android's system picker.
5. Leave your wallpaper unchanged if you want. Enter **Редактор** to adjust weather, time-of-day, opacity, effect strength and rendering rate.
6. Choose **Вернуть One UI Home** to open Android's Home default settings; choose the original Samsung launcher.
7. To return to previous wallpaper, use Samsung **Обои и стиль**; the wallpaper is not silently backed up or restored.

> The v0.1 alpha was merely a working compile and lacked a proper first-run activation experience. Please **uninstall that test version** before trying v0.2: GitHub runners generate different debug-signing keys.

## AURA Island — development implementation

The development branch now includes the first **real media/event integration**:

- Compact glass capsule on **AURA Home**; tap or swipe to expand/collapse, long-press for configuration
- Real media title, artist, source app, artwork, playback state, seek bar, previous/pause/next where supported by the source MediaSession
- Optional FFT audio-reactive spectrum through Android's `Visualizer(0)` when the user explicitly grants `RECORD_AUDIO`. Without capture there is a calm stationary waveform — not simulated bass
- Call notifications where the phone app exposes them: caller label and optional details (hidden by default), notification-provided actions only; opening the system phone call UI
- Actual five-channel AudioManager system volume mixer; **not** a universal per-app sound mixer. Samsung SoundAssistant can be opened when available
- Local `NotificationListenerService` consent; no notification content or FFT bytes sent to servers
- Currently limited to AURA Home. Other-app overlay, independent per-app audio and true direct call mute/hang-up require separate OEM/system support or a default-dialer implementation and are **not promised or enabled**

The GitHub Actions pipeline builds and smoke-tests the development APK **without publishing a public prerelease**. A stable release requires a persistent private signing key and real testing on Samsung before publication. See [RELEASE_PLAN.md](RELEASE_PLAN.md), [SIGNING.md](SIGNING.md), and [AURA_ISLAND_SPEC.md](AURA_ISLAND_SPEC.md).

## Prototype capabilities

- Native home screen with app drawer, installed apps and animated weather card
- Live-wallpaper service with gradients, sun/moon, stars, rain, snow, frost, mist, aurora
- Gradual daylight changes and weather crossfades
- Manual weather mode or Open-Meteo city weather, approximate geolocation permission when opted in
- Android weather widget with static RemoteViews snapshots; frame-by-frame animation is only in AURA Home
- Samsung Weather opening where a compatible activity is available
- AURA setup state diagnostics and easy system-based reversal
- No native dependencies: Android 8.0+; ARMv7 and ARM64 supported if device supports API 26+

## Quality and limitations

This is **not yet a visually complete recreation of the concept renders**. Visual fidelity, blur/refraction, weather realism, smooth gestures and real Samsung compatibility need substantial development and device verification. The launcher currently has a basic application grid rather than a full One UI replacement (folders, drag/drop and editable desktop not yet implemented).

CI builds the APK, validates its HOME and live-wallpaper manifest entries, runs Setup/Home/Editor, opens live wallpaper preview, checks HOME role selection on an Android emulator, and saves screenshots as GitHub Actions artifacts. Emulator success does not guarantee Samsung behavior. No invasive overlay permissions or system modifications are used. Render loops only run while visible; battery saver reduces updates.

## Build

Open in Android Studio using JDK 17 / Gradle 8.10.2, or use GitHub Actions. Source project is Java 17, Android Gradle Plugin 8.7.3, compileSdk 35.
