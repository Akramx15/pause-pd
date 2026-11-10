#!/usr/bin/env bash
# Build the signed release APK and stage it at dist/pause-pd-release.apk
set -euo pipefail

cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] && [ -n "${LOCALAPPDATA:-}" ]; then
  SDK="$LOCALAPPDATA/Android/Sdk"
fi
if [ -z "$SDK" ]; then
  SDK="$HOME/Android/Sdk"
fi
if [ ! -f "$SDK/platforms/android-36/android.jar" ]; then
  echo "ERROR: Android SDK not found (looked at '$SDK')." >&2
  exit 1
fi
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"

if [ ! -f keystore.properties ]; then
  echo "ERROR: keystore.properties is missing (see README, release signing)." >&2
  exit 1
fi

./gradlew assembleRelease

mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/pause-pd-release.apk
echo "APK -> dist/pause-pd-release.apk"
