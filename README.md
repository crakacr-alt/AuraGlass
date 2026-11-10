# AURA GLASS — Android launcher, live glass & weather

**Status: active development, not a stable release.** This repository contains the Android application source and reproducible development builds. The `v0.x-alpha` prereleases are obsolete; their GitHub Actions debug certificates changed between builds, so updating them on top of each other may fail with "App not installed". A stable, permanently signed release has **not** been published.

## Core components

| Component | Current implementation | What is not finished |
|---|---|---|
| AURA Home | Launcher HOME role, weather card, app list/search, favorites, editable dock, persistent folders, integrated access to a widget board | True multi-page drag-and-drop home grid, full backup/restore of live widget bindings, Samsung visual acceptance |
| Widgets | Android `AppWidgetHost` workspace with system binding, provider configuration, widget height/order and removal | Native resizable widget placement directly on multiple launcher pages |
| ICE GLASS | Live wallpaper, thermal frost trails, condensation/refreezing, snow, aurora, stars, galaxy, comets, moon phase, full-screen interactive preview | GPU optical refraction, final crystalline detail, measured performance and visual match on S24 Ultra |
| AURA Island | Launcher capsule, MediaSession track/artist/transport/seek, opt-in FFT, notification cards, call-notification actions, system audio streams | Across-app overlay, advanced OEM call support, real per-app volume without supported Samsung API |
| Updates | GitHub stable-release check **on opening Setup only**, opt-in notification, explicit download and package/hash/signature/version checks, Android installation confirmation | End-to-end stable→stable upgrade test; release key and stable assets |
| Diagnostics | On-device system status and user-initiated window FrameMetrics test, local shareable report | Long-run thermal/battery tests and full-screen wallpaper 120 Hz profiling |
| Backup | User-controlled JSON export/import of theme, desktop shortcuts, dock and folders | Widget instance IDs are not portable and must be rebound after reinstall |

## Android setup

Installing an existing development artifact is for testing, not for use as a production launcher. Android 8.0+ (`minSdk=26`) is the current compatibility target; ARMv7 and arm64 code is built without native ABI-specific libraries, but physical compatibility is not yet verified.

After install, open AURA GLASS from the app list. The setup screen lets you **independently** select AURA as the default Home application (Android Home role) and apply AURA live wallpaper (Android system preview). Neither action occurs silently. You can always switch the Home app back to Samsung One UI Home under **Settings → Apps → Default apps → Home app**, and separately restore a wallpaper via Samsung Wallpaper and style.

Use the editor to choose ICE GLASS, weather, time, effects and refresh rate. Select **System / 60 FPS / 120 FPS**; 120 Hz is a request only if supported by the display, not a claim of sustained 120 FPS. Energy saver/thermal throttling may lower frame rates.

From AURA Home, tap the widget icon to open the Android widget board. Long-press app icons to manage favorites, dock and folders. From Setup, use **Save settings to JSON** before uninstalling a test build; import them after reinstalling. Widgets supplied by other apps still require Android's binding/permissions.

## Updates and security

- Stable releases are discovered from `https://api.github.com/repos/crakacr-alt/AuraGlass/releases/latest` **only on SetupActivity entry or when you press Check now**.
- If a newer stable APK is available, AURA offers a download button. Optionally an update notice appears in the notification shade once per release. There is no scheduled or silent update.
- Download verification checks the GitHub asset digest, APK size, package ID, signing certificate, and increasing version code. Android still displays its own installation consent.
- Until one persistent private signing key exists, previous debug builds **cannot be guaranteed to upgrade without uninstalling**.
- Android notification listener, audio spectrum, location and install-from-source permissions are requested only for their corresponding opt-in features.

## Tests and release gate

GitHub Actions compiles, validates manifest declarations and APK signatures, installs the development APK in an API 35 emulator, exercises onboarding, Home, widget board, diagnostics, editor, ICE GLASS and the wallpaper picker, and uploads screenshots/logs. A passing emulator test does **not** prove compatibility with physical Samsung One UI, true 120 FPS, smooth touch rendering or stable upgrade signing.

A stable `v1.0` release must meet **every mandatory acceptance item** in:
- [100% engineering roadmap](RELEASE_100_ROADMAP.md)
- [Release checklist](RELEASE_PLAN.md)
- [ICE GLASS rendering spec](ICE_GLASS_SPEC.md) and [visual QA](ICE_VISUAL_QA.md)
- [AURA Island spec](AURA_ISLAND_SPEC.md)
- [GitHub updater spec](GITHUB_UPDATES.md)
- [Signing and migration notes](SIGNING.md)

No automatic GitHub Releases are created from development commits. The existing old prereleases are not promoted to stable releases.

## Build

JDK 17 / Gradle 8.10.2 / Android Gradle Plugin 8.7.3 / compileSdk 35. Run `gradle --no-daemon :app:assembleDebug`, or inspect the latest GitHub Actions workflow and its artifacts. Development APK signatures may vary between Actions runners.

© AURA GLASS — development prototype.
