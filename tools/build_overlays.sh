#!/usr/bin/env bash
set -euo pipefail
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -n 1)"
JAR="$(ls -d "$SDK"/platforms/android-*/android.jar | sort -V | tail -n 1)"
OUT="app/src/main/assets/overlays"
W="$(mktemp -d)"
mkdir -p "$OUT"
rm -f "$OUT"/*.apk
KS="${RT_KEYSTORE:-}"
if [ -z "$KS" ]; then
  keytool -genkeypair -keystore "$W/tmp.p12" -storetype pkcs12 -storepass android -keypass android \
    -alias key -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=RomTweaker" >/dev/null 2>&1
  KS="$W/tmp.p12"
fi
while IFS='|' read -r id label path; do
  case "$id" in ""|\#*) continue ;; esac
  D="$W/$id"
  mkdir -p "$D/res/values"
  PKG="dev.romtweaker.shape.$id"
  cat > "$D/AndroidManifest.xml" <<MAN
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$PKG">
    <overlay android:targetPackage="android" android:category="android.theme.customization.adaptive_icon_shape" android:priority="1" />
    <application android:hasCode="false" />
</manifest>
MAN
  cat > "$D/res/values/config.xml" <<RES
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="config_icon_mask" translatable="false">$path</string>
</resources>
RES
  "$BT/aapt2" compile --dir "$D/res" -o "$D/compiled.zip" </dev/null
  "$BT/aapt2" link -o "$D/unaligned.apk" -I "$JAR" --manifest "$D/AndroidManifest.xml" \
    --min-sdk-version 29 --target-sdk-version 34 "$D/compiled.zip" </dev/null
  "$BT/zipalign" -f 4 "$D/unaligned.apk" "$D/aligned.apk" </dev/null
  "$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --ks-key-alias key --key-pass pass:android \
    --out "$OUT/$id.apk" "$D/aligned.apk" </dev/null
done < app/src/main/assets/shapes.txt
echo "Overlays generados: $(ls "$OUT" | wc -l)"
bash "$(dirname "${BASH_SOURCE[0]}")/build_iconify.sh" || true
