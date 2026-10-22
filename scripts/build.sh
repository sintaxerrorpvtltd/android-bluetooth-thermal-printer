#!/usr/bin/env bash
set -euo pipefail
umask 077
root="$(cd "$(dirname "$0")/.." && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 21 installation}"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
: "${sdk:?Set ANDROID_SDK_ROOT to your Android SDK}"
export PATH="$JAVA_HOME/bin:$PATH"
tools="$sdk/build-tools/36.1.0"
platform="$sdk/platforms/android-36/android.jar"
base="${THERMAL_BUILD_DIR:-$root/build}"
mkdir -p "$base" "$root/dist"
base="$(cd "$base" && pwd)"
build="$(mktemp -d "$base/run.XXXXXX")"
mkdir -p "$build/classes" "$build/generated" "$build/dex"
"$tools/aapt2" compile --dir "$root/app/res" -o "$build/resources.zip"
"$tools/aapt2" link -I "$platform" --manifest "$root/app/AndroidManifest.xml" --java "$build/generated" -o "$build/unsigned.apk" "$build/resources.zip"
sources=()
while IFS= read -r -d '' source; do sources+=("$source"); done < <(find "$root/app/src" "$build/generated" -name '*.java' -print0)
javac -source 8 -target 8 -Xlint:-options -bootclasspath "$platform:$tools/core-lambda-stubs.jar" -d "$build/classes" "${sources[@]}"
java -cp "$build/classes" local.sintax.thermal58.RasterCodec
jar cf "$build/classes.jar" -C "$build/classes" .
"$tools/d8" --lib "$platform" --min-api 31 --output "$build/dex" "$build/classes.jar"
(cd "$build/dex" && zip -q -j "$build/unsigned.apk" classes.dex)
"$tools/zipalign" -f 4 "$build/unsigned.apk" "$build/aligned.apk"
if [ -n "${THERMAL_KEYSTORE:-}" ]; then
 key="$THERMAL_KEYSTORE"
 : "${THERMAL_KEYSTORE_PASSWORD_FILE:?Supply the private keystore password file}"
 password="$THERMAL_KEYSTORE_PASSWORD_FILE"
else
 key="$base/development.keystore"
 password="$base/development-password.txt"
 if [ ! -f "$key" ]; then
  openssl rand -hex 24 > "$password"
  keytool -genkeypair -keystore "$key" -storepass:file "$password" -keypass:file "$password" -alias thermal -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Thermal Printer Local Development' >/dev/null 2>&1
  chmod 600 "$key" "$password"
 fi
fi
"$tools/apksigner" sign --ks "$key" --ks-pass "file:$password" --out "$root/dist/58mm-receipt-printer.apk" "$build/aligned.apk"
"$tools/apksigner" verify "$root/dist/58mm-receipt-printer.apk"
printf '%s\n' "$build" > "$base/latest-build-dir.txt"
echo 'Build, signature verification and raster self-checks passed. APK: dist/58mm-receipt-printer.apk'
