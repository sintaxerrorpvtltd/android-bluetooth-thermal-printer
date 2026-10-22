#!/usr/bin/env bash
set -euo pipefail
umask 077
root="$(cd "$(dirname "$0")/.." && pwd)"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 21}"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
: "${sdk:?Set ANDROID_SDK_ROOT}"
serial="${1:?Usage: ./scripts/test-device.sh DEVICE_SERIAL}"
export PATH="$JAVA_HOME/bin:$PATH"
"$root/scripts/build.sh"
base="${THERMAL_BUILD_DIR:-$root/build}"
build="$(cat "$base/latest-build-dir.txt")"
testbuild="$build/device-checks"
tools="$sdk/build-tools/36.1.0"
platform="$sdk/platforms/android-36/android.jar"
adb="$sdk/platform-tools/adb"
mkdir -p "$testbuild/classes" "$testbuild/dex"
"$tools/aapt2" link -I "$platform" --manifest "$root/tests/AndroidManifest.xml" -o "$testbuild/unsigned.apk"
javac -source 8 -target 8 -Xlint:-options -bootclasspath "$platform:$tools/core-lambda-stubs.jar" -classpath "$build/classes" -d "$testbuild/classes" "$root/tests/SelfCheck.java"
jar cf "$testbuild/checks.jar" -C "$testbuild/classes" .
"$tools/d8" --lib "$platform" --min-api 31 --output "$testbuild/dex" "$testbuild/checks.jar" "$build/classes.jar"
(cd "$testbuild/dex" && zip -q -j "$testbuild/unsigned.apk" classes.dex)
"$tools/zipalign" -f 4 "$testbuild/unsigned.apk" "$testbuild/aligned.apk"
openssl rand -hex 24 > "$testbuild/password.txt"
keytool -genkeypair -keystore "$testbuild/test.keystore" -storepass:file "$testbuild/password.txt" -keypass:file "$testbuild/password.txt" -alias checks -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Local Raster Checks' >/dev/null 2>&1
"$tools/apksigner" sign --ks "$testbuild/test.keystore" --ks-pass "file:$testbuild/password.txt" --out "$testbuild/checks.apk" "$testbuild/aligned.apk"
installed=0
cleanup() { if [ "$installed" = 1 ]; then "$adb" -s "$serial" uninstall local.sintax.thermal58.tests >/dev/null || true; fi; }
trap cleanup EXIT
"$adb" -s "$serial" install "$testbuild/checks.apk"
installed=1
result="$("$adb" -s "$serial" shell am instrument -w local.sintax.thermal58.tests/local.sintax.thermal58.SelfCheck)"
printf '%s\n' "$result"
case "$result" in *'result=PASS:'*'INSTRUMENTATION_CODE: -1'*) ;; *) echo 'Device checks failed.' >&2; exit 1 ;; esac
