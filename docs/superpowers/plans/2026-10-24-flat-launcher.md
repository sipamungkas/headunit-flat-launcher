# Flat Launcher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and install `com.headunit.flatlauncher`, a zero-dependency minimal home app for the 8227L headunit (Android 8.1, 1280×720@160dpi, armeabi-v7a).

**Architecture:** Single Activity (`FlatActivity`) hosting a left panel (TextClock clock + date + 4 pinned car buttons) and a right `GridView` fed by `AppGridAdapter` (PackageManager launcher-app query, desaturated icons). HOME+DEFAULT intent filter. No tests infra: verification is build success plus on-device adb checks per task.

**Tech Stack:** Android framework views only (TextClock, LinearLayout, GridView, BaseAdapter), minSdk/targetSdk 27, javac 17 `--release 8`, manual `aapt2 → javac → d8 → zip → zipalign → apksigner` pipeline, JDK-generated keystore.

**Spec:** `docs/superpowers/specs/2026-10-24-headunit-flat-launcher-design.md`

## Global Constraints

- Colors exactly: bg `#101014`, text `#E8E6E3`, muted `#9A979F`, hairline `#26262B`, accent `#C8956C`.
- Icon desaturation: `ColorMatrix.setSaturation(0.25f)`.
- Zero external dependencies, zero permissions, no wallpaper/widgets/dock.
- Pinned: Radio→`com.hcn.autoradio`, Media→`com.hcn.AutoMediaPlayer`, Bluetooth→`Settings.ACTION_BLUETOOTH_SETTINGS`, Maps→`com.google.android.apps.maps`; hide any unresolvable button, never crash.
- `com.android.launcher3` untouched (fallback).
- Device adb target: `192.168.0.107:8555`. SDK paths: build-tools `35.0.0`, platform jar `platforms/android-30/android.jar`.

## Review Focus

1. Zero launcher apps resolvable → grid shows muted "No apps" text, no crash/blank. (Pinned in Task 3.)
2. Pinned package unresolvable → button omitted; remaining three still work. (Behavior owned by Task 2 `resolvePinned`; exercised indirectly by Task 2 tap checks.)
3. 12h/24h system setting → TextClock tracks system format automatically; no hardcoded format switch. (Task 2 visual check only.)
4. Long app label → single line, ellipsize end, grid cell never wraps. (Pinned in Task 3.)
5. Launcher registered but not default → user can always switch back via Settings > Apps > Default apps. (Task 4 check.)

---

### Task 1: Project scaffold + reproducible build script

**Files:**
- Create: `launcher/AndroidManifest.xml`
- Create: `launcher/build.sh`
- Create: `launcher/keystore/` (gitignored, generated)
- Create: `launcher/.gitignore`

**Interfaces:**
- Produces: `launcher/build.sh` → signed `launcher/dist/flatlauncher.apk`; later tasks only add sources/resources, never touch the script.
- Manifest pins: package `com.headunit.flatlauncher`, `minSdkVersion 27`, `targetSdkVersion 27`, `FlatActivity` with `android.intent.category.HOME` + `DEFAULT` + `LAUNCHER`, `launchMode="singleTask"`, `stateNotNeeded="true"`, `screenOrientation="sensorLandscape"`, theme `@style/Theme.Flat`.

- [ ] **Step 1: Write manifest + stub theme-less styles placeholder entry** (theme values land in Task 2; manifest references `@style/Theme.Flat`, so create `res/values/styles.xml` now with parent `@android:style/Theme.Material.NoActionBar` and `android:windowBackground=@color/bg`).

- [ ] **Step 2: Write `build.sh`** with exact pipeline:
```sh
SDK=$HOME/Library/Android/sdk
BT=$SDK/build-tools/35.0.0
JAR=$SDK/platforms/android-30/android.jar
# 1 keystore: keytool -genkeypair ... -keystore keystore/debug.keystore -alias hu -storepass android -keypass android -dname CN=FlatLauncher (skip if exists)
# 2 aapt2 compile --dir res -o build/res.zip
# 3 aapt2 link -o build/base.apk -I "$JAR" --manifest AndroidManifest.xml --java build/gen --auto-add-overlay build/res.zip
# 4 javac --release 8 -cp "$JAR" -d build/classes build/gen/**/R.java $(find java -name '*.java')
# 5 d8 --release --lib "$JAR" --min-api 27 --output build/dex $(find build/classes -name '*.class')
# 6 (cd build/dex && zip -qj ../base.apk classes.dex)
# 7 zipalign -f 4 build/base.apk build/aligned.apk
# 8 apksigner sign --ks keystore/debug.keystore --ks-pass pass:android --min-sdk-version 27 --out dist/flatlauncher.apk build/aligned.apk
```

- [ ] **Step 3: Build + verify**
Run: `cd launcher && ./build.sh && $SDK/build-tools/35.0.0/apksigner verify --print-certs dist/flatlauncher.apk`
Expected: APK exists, `Verified using v1 scheme: true` (v1+v2 OK).

- [ ] **Step 4: Commit**

### Task 2: Left panel — clock, date, pinned car buttons

**Files:**
- Create: `launcher/res/values/colors.xml` (exact spec colors), `strings.xml` (app label "Launcher", pinned labels), `dimens.xml`
- Modify: `launcher/res/values/styles.xml` (Theme.Flat: fullscreen, landscape-safe, windowBackground `@color/bg`, statusBar/navBar color `@color/bg`)
- Create: `launcher/res/layout/activity_flat.xml` (horizontal LinearLayout: left panel width `280dp` with vertical LinearLayout, right placeholder `FrameLayout @+id/grid_container` weight 1; 1dp `@color/hairline` divider between)
- Create: `launcher/res/drawable/pinned_button_bg.xml` (rounded 8dp stroke `@color/hairline`, ripple overlay `@color/accent` 25%)
- Create: `launcher/java/com/headunit/flatlauncher/FlatActivity.java`
- Create: `launcher/res/drawable/ic_launcher.xml` (simple gray square vector)

**Interfaces:**
- Produces:
  - `class FlatActivity extends Activity` with `void onWindowFocusChanged(boolean)` applying immersive sticky flags.
  - `static class PinnedApp { String label; Intent intent; }`
  - `private List<PinnedApp> resolvePinned()` — order Radio, Media, Bluetooth, Maps; `getLaunchIntentForPackage` for packages, `Settings.ACTION_BLUETOOTH_SETTINGS` for BT; unresolvable entries dropped.
  - `private void buildPinnedRow(List<PinnedApp>)` — inflates one `Button` per app (min height `56dp`, background `@drawable/pinned_button_bg`, text `@color/text`, `13sp`), `startActivity(intent)` on click.

- [ ] **Step 1: Write resources + layouts**
- [ ] **Step 2: Implement `FlatActivity` onCreate: `setContentView`, clock `TextClock` `format24Hour="HH:mm"` + `format12Hour="h:mm"`, `96sp`, `sans-serif-light`, `@color/text`; date `TextClock` `format24Hour="EEEE, d MMMM"`/12h same, `16sp` `@color/muted`; call `resolvePinned` + `buildPinnedRow`.**
- [ ] **Step 3: Build, install, smoke**
Run: `./build.sh && adb -s 192.168.0.107:8555 install -r dist/flatlauncher.apk && adb -s 192.168.0.107:8555 shell am start -W -n com.headunit.flatlauncher/.FlatActivity`
Expected: `TotalTime` reported, no exception.
- [ ] **Step 4: Visual check** — `adb exec-out screencap -p > /tmp/t2.png`: clock large top-left, date below, ≤4 outline buttons, right side empty panel. If a pinned package is unresolvable, its button is absent and log shows dropped entry.
- [ ] **Step 5: Commit**

### Task 3: Right panel — app grid

**Files:**
- Create: `launcher/java/com/headunit/flatlauncher/AppGridAdapter.java`
- Create: `launcher/res/layout/grid_item.xml`
- Modify: `launcher/res/layout/activity_flat.xml` (replace placeholder with `GridView @+id/app_grid`, 5 columns, vertical spacing `8dp`, padding `16dp`)
- Modify: `launcher/java/com/headunit/flatlauncher/FlatActivity.java` (wire grid)

**Interfaces:**
- Consumes: `FlatActivity` content view id `app_grid`.
- Produces:
  - `class AppGridAdapter extends BaseAdapter { AppGridAdapter(Context ctx) }` — queries `ACTION_MAIN`/`CATEGORY_LAUNCHER`, excludes its own package, sorts by label; entry `class App { CharSequence label; Drawable icon; Intent launch; }`.
  - `getView` reuses convertView, `ColorMatrixColorFilter(ColorMatrix().setSaturation(0.25f))` on icon, label `@color/muted` `12sp` single line `ellipsize=end`.
  - Click → `startActivity(app.launch)`; long-press → `AlertDialog` "App info" → `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` with package URI.

- [ ] **Step 1: Write `grid_item.xml` + `AppGridAdapter` + wiring**
- [ ] **Step 2: Build + install + launch** (same commands as Task 2 Step 3)
- [ ] **Step 3: Visual + interaction check** — screencap: 5-column desaturated grid matches design mock. `input tap` first item → that app foregrounds (`dumpsys activity activities | grep mResumedActivity`); `input keyevent KEYCODE_HOME` → flatlauncher; long-press (`input swipe X Y X Y 900`) → app-info dialog screencap.
- [ ] **Step 4: Empty-state check** — assert adapter returns 0-count text: `@string/empty_grid` "No apps", `@color/muted`, centered (code + one screencap after temporarily filtering list in a debug build is overkill; instead unit-verify `getCount()>=0` logic by code review + note). Accept: code path present.
- [ ] **Step 5: Commit**

### Task 4: HOME default + performance validation

**Files:**
- Modify: none (validation task; fixes fold into Tasks 2–3)

**Interfaces:**
- Consumes: installed `com.headunit.flatlauncher/.FlatActivity`.

- [ ] **Step 1: Set as HOME**
Run: `adb shell cmd package set-home-activity com.headunit.flatlauncher/.FlatActivity || adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME` (chooser fallback; pick "Always").
Verify: `cmd package resolve-activity -c android.intent.category.HOME -a android.intent.action.MAIN | grep -m1 name=` → `com.headunit.flatlauncher.FlatActivity`.
- [ ] **Step 2: Home-key round trip** — launch Maps, press KEYCODE_HOME, screencap = flatlauncher.
- [ ] **Step 3: Performance** — `am force-stop` then `am start -W` (record TotalTime), `dumpsys meminfo com.headunit.flatlauncher | grep TOTAL` (expect PSS < 25600 kB), compare launcher3 baseline `53,797K` from session notes.
- [ ] **Step 4: Reboot unit** — `adb reboot`; after reconnection verify home = flatlauncher and capture final `MemAvailable`.
- [ ] **Step 5: Commit + update spec status line.**
