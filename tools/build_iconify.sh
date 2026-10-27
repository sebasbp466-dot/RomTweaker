#!/usr/bin/env bash
# RomTweaker: compila los overlays de Iconify (GPL-3.0, https://github.com/Mahmud0808/Iconify)
cd "$(dirname "$0")/.." || exit 0
OUT=app/src/main/assets/iconify
rm -rf "$OUT"; mkdir -p "$OUT"
W=$(mktemp -d); mkdir -p "$W/res" "$W/b"
git clone -q --depth 1 -b beta https://github.com/Mahmud0808/Iconify.git "$W/ic" || { echo "iconify: no se pudo clonar"; exit 0; }
A="$W/ic/app/src/main/assets"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
BT=$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1); BT=${BT%/}
AJ="$SDK/platforms/android-34/android.jar"; [ -f "$AJ" ] || AJ=$(ls "$SDK"/platforms/*/android.jar 2>/dev/null | sort -V | tail -1)
[ -x "$BT/aapt2" ] && [ -f "$AJ" ] || { echo "iconify: falta aapt2/android.jar"; exit 0; }
KS="$W/k.jks"
keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias k -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=RomTweaker" >/dev/null 2>&1

build_one() { # id target srcdir
  local id="$1" tgt="$2" src="$3" lid pkg b
  [ -d "$src/res" ] || return 1
  lid=$(echo "$id" | tr 'A-Z' 'a-z' | tr -cd 'a-z0-9')
  pkg="dev.romtweaker.ico.$lid"
  b="$W/b/$id"; rm -rf "$b"; mkdir -p "$b"; cp -r "$src/res" "$b/res"
  cat > "$b/AndroidManifest.xml" <<EOM
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$pkg">
    <uses-sdk android:minSdkVersion="29" android:targetSdkVersion="34" />
    <overlay android:targetPackage="$tgt" />
    <application android:hasCode="false" />
</manifest>
EOM
  "$BT/aapt2" compile --dir "$b/res" -o "$b/c.zip" >/dev/null 2>&1 || return 1
  "$BT/aapt2" link -o "$b/u.apk" -I "$AJ" --manifest "$b/AndroidManifest.xml" --min-sdk-version 29 --target-sdk-version 34 "$b/c.zip" >/dev/null 2>&1 || return 1
  "$BT/zipalign" -f 4 "$b/u.apk" "$b/a.apk" >/dev/null 2>&1 || return 1
  "$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out "$OUT/$id.apk" "$b/a.apk" >/dev/null 2>&1 || return 1
  echo "$id:$pkg:$tgt"
}
export -f build_one; export W BT AJ KS OUT

SY=com.android.systemui; AN=android; ST=com.android.settings
J="$W/jobs.txt"; : > "$J"
job() { echo "$1 $2 $3" >> "$J"; }
for n in $(seq 1 14); do job IPAS$n $AN "$A/Overlays/android/IPAS$n"; job IPSUI$n $SY "$A/Overlays/$SY/IPSUI$n"; done
for n in $(seq 1 36); do job SGIC$n $SY "$A/CompileOnDemand/$SY/SGIC$n"; done
for n in $(seq 1 16); do job WIFI$n $SY "$A/CompileOnDemand/$SY/WIFI$n"; done
for n in $(seq 1 20); do job NFN$n $SY "$A/CompileOnDemand/$SY/NFN$n"; done
for n in $(seq 1 12); do job TSTFRM$n $AN "$A/CompileOnDemand/android/TSTFRM$n"; done
for n in $(seq 1 18); do job SIS$n $AN "$A/CompileOnDemand/android/SIS$n"; done
for n in $(seq 1 6); do job SIP$n $ST "$A/CompileOnDemand/$ST/SIP$n"; done
job CR1 $AN "$A/Overlays/android/CR1"; job CR2 $SY "$A/Overlays/$SY/CR2"
job PCBG $SY "$A/Overlays/$SY/PCBG"; job QSC $SY "$A/Overlays/$SY/QSC"
job SGICB $SY "$A/Overlays/$SY/SGIC"; job WIFIB $SY "$A/Overlays/$SY/WIFI"
xargs -P 4 -L 1 bash -c 'build_one "$0" "$1" "$2" > "$W/res/$0.ok" 2>/dev/null || rm -f "$W/res/$0.ok"' < "$J"

IDX="$OUT/index.txt"; : > "$IDX"
res() { cat "$W/res/$1.ok" 2>/dev/null; }
one() { local r; r=$(res "$3"); [ -n "$r" ] && echo "$1|$2|$r" >> "$IDX"; }
for n in $(seq 1 14); do p=""; for i in IPAS$n IPSUI$n; do r=$(res $i); [ -n "$r" ] && p="${p:+$p,}$r"; done; [ -n "$p" ] && echo "Paquetes de iconos|Pack $n|$p" >> "$IDX"; done
one "Iconos de señal" "Iconify (base)" SGICB
for n in $(seq 1 36); do one "Iconos de señal" "Estilo $n" SGIC$n; done
one "Iconos de Wi-Fi" "Iconify (base)" WIFIB
for n in $(seq 1 16); do one "Iconos de Wi-Fi" "Estilo $n" WIFI$n; done
for n in $(seq 1 20); do one "Fondo de notificaciones" "Estilo $n" NFN$n; done
for n in $(seq 1 12); do one "Marco de toasts" "Estilo $n" TSTFRM$n; done
for n in $(seq 1 18); do one "Forma de iconos (Iconify)" "Forma $n" SIS$n; done
for n in $(seq 1 6); do one "Iconos de Ajustes" "Estilo $n" SIP$n; done
p=""; for i in CR1 CR2; do r=$(res $i); [ -n "$r" ] && p="${p:+$p,}$r"; done; [ -n "$p" ] && echo "Esquinas de pantalla|Iconify|$p" >> "$IDX"
one "Chip de privacidad" "Iconify" PCBG
one "Botones y diálogos" "Iconify" QSC
cp "$W/ic/LICENSE" "$OUT/LICENSE-Iconify.txt" 2>/dev/null
echo "iconify: $(wc -l < "$IDX") estilos, $(ls "$OUT"/*.apk 2>/dev/null | wc -l) apks"
exit 0
