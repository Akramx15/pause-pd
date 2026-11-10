#!/usr/bin/env bash
# Build the pause-pd debug APK and stage it at dist/pause-pd-debug.apk
set -euo pipefail

cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
  SDK="C:/Users/A/AppData/Local/Android/Sdk"
fi
if [ ! -f "$SDK/platforms/android-36/android.jar" ]; then
  echo "ERROR: Android SDK not found (looked at '$SDK')." >&2
  echo "Install platform android-36, or export ANDROID_HOME." >&2
  exit 1
fi
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"

./gradlew assembleDebug

mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/pause-pd-debug.apk
echo "APK -> dist/pause-pd-debug.apk"
