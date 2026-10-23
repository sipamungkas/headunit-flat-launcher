# Flat Launcher for 8227L headunit — design

Date: 2026-10-24
Status: approved in chat, pending spec review

## Goal

Replace the stock `com.android.launcher3` (53 MB PSS, colorful vendor theming) with a minimal, calm, fast home app on the 8227L headunit (Android 8.1, 1280×720 @ 160dpi, armeabi-v7a, 4 GB RAM).

Success criteria:
- Home screen shows: big clock (left), date below it, 4 pinned car buttons, scrollable app grid (right).
- Monochrome dark palette; single muted accent for press/selection only.
- PSS < 25 MB; cold start noticeably faster than launcher3; zero network permissions.
- `com.android.launcher3` remains installed as fallback.

## Layout (1280×720 landscape, single screen, no pages)

- Left panel (~28% width):
  - Clock, ~110sp thin weight, tabular figures.
  - Date below, muted gray, "Friday, 24 October" format.
  - Below date: 4 pinned buttons, full-width outline rows, ≥56dp tall: Radio, Media, Bluetooth, Maps.
- Right panel (~72%): scrollable 5-column grid of launcher apps; icon (48dp) over small muted label, single line.

## Colors

| Role | Value |
|---|---|
| Background | `#101014` (solid, no wallpaper) |
| Text primary | `#E8E6E3` |
| Text muted | `#9A979F` |
| Hairline/outline | `#26262B` |
| Accent (press/selected only) | `#C8956C` |

App icons desaturated to ~25% saturation via `ColorMatrixColorFilter`; labels soft white.

## Behavior

- Solid dark background; no wallpaper, widgets, dock, Google feed, page indicators.
- Status bar hidden (immersive).
- Long-press grid item → app info dialog.
- Pinned targets: Radio → `com.hcn.autoradio`, Media → `com.hcn.AutoMediaPlayer`, Bluetooth → `com.android.settings` BT settings entry, Maps → `com.google.android.apps.maps`. Missing apps are hidden at load.
- Grid lists all launcher activities; disabled/hidden packages excluded automatically by PackageManager query.
- HOME category registered; launcher3 keeps HOME intent so default can be switched back in Settings.

## Non-goals

- No widget support, no folders, no wallpaper picker, no settings UI, no gestures beyond tap/long-press, no icon packs, no network features.

## Tech

- Single Activity + `GridView`/`BaseAdapter`; framework views only; zero external dependencies.
- `minSdk 27`, `targetSdk 27`, `compileSdk` against available `android.jar` (30+); package `com.headunit.flatlauncher`, label "Launcher".
- Build: manual pipeline `aapt2 → javac → d8 → zip → zipalign → apksigner` (debug keystore generated with `keytool`). No Gradle/AGP downloads needed.
- Files: `AndroidManifest.xml`, `FlatActivity.java`, `AppGridAdapter.java`, `res/` (layout, colors, dimens, strings), `ic_launcher` simple PNG.

## Verification

- `adb install`, `cmd package` set preferred HOME or choose via `am start` chooser.
- `adb exec-out screencap -p` → visual check against design.
- `dumpsys meminfo com.headunit.flatlauncher` → PSS < 25 MB.
- `am start -W` cold-start time; compare with launcher3 baseline.
- Tap a grid app + pinned buttons; confirm launches and return-to-home.

## Rollback

- `pm uninstall -k --user 0 com.headunit.flatlauncher` (or disable) restores launcher3 as HOME.
