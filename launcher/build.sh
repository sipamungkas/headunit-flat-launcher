#!/bin/sh
# Flat launcher build: aapt2 -> javac -> d8 -> zip -> zipalign -> apksigner
set -eu
cd "$(dirname "$0")"

SDK=${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-30/android.jar"

rm -rf build dist
mkdir -p build dist

if [ ! -f keystore/debug.keystore ]; then
    mkdir -p keystore
    keytool -genkeypair -keystore keystore/debug.keystore -alias hu \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android -dname CN=FlatLauncher
fi

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/base.apk -I "$JAR" \
    --manifest AndroidManifest.xml --java build/gen --auto-add-overlay build/res.zip

SRCS=$(find build/gen java -name '*.java' 2>/dev/null || true)
if [ -n "$SRCS" ]; then
    mkdir -p build/classes build/dex
    # shellcheck disable=SC2086
    javac --release 8 -cp "$JAR" -d build/classes $SRCS
    # shellcheck disable=SC2046
    "$BT/d8" --release --lib "$JAR" --min-api 27 --output build/dex \
        $(find build/classes -name '*.class')
    (cd build/dex && zip -qj ../base.apk classes.dex)
fi

"$BT/zipalign" -f 4 build/base.apk build/aligned.apk
"$BT/apksigner" sign --ks keystore/debug.keystore --ks-pass pass:android \
    --min-sdk-version 27 --out dist/flatlauncher.apk build/aligned.apk
echo "OK dist/flatlauncher.apk"
