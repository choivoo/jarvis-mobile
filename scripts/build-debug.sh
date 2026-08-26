#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_VERSION="9.5.0"
TOOLS_DIR="$HOME/.jarvis-tools"
GRADLE_DIR="$TOOLS_DIR/gradle-$GRADLE_VERSION"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: Java was not found. JDK 17 is required."
  exit 1
fi
if ! command -v sdkmanager >/dev/null 2>&1; then
  echo "ERROR: sdkmanager was not found. Check ANDROID_HOME=$ANDROID_HOME"
  exit 1
fi

mkdir -p "$TOOLS_DIR"
if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  echo "[JARVIS] Downloading Gradle $GRADLE_VERSION..."
  curl -L --fail --retry 3 "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$TOOLS_DIR/gradle-$GRADLE_VERSION-bin.zip"
  rm -rf "$GRADLE_DIR"
  unzip -q "$TOOLS_DIR/gradle-$GRADLE_VERSION-bin.zip" -d "$TOOLS_DIR"
fi

echo "[JARVIS] Checking Android SDK packages..."
yes | sdkmanager --licenses >/dev/null || true
sdkmanager "platform-tools" "platforms;android-37" "build-tools;36.0.0"

echo "[JARVIS] Preparing standalone Neural Local voice..."
bash "$PROJECT_DIR/scripts/prepare-standalone-neural.sh"

echo "[JARVIS] Building Standalone Cinema V2.1 debug APK..."
cd "$PROJECT_DIR"
"$GRADLE_DIR/bin/gradle" --no-daemon :app:assembleDebug

APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
  echo "ERROR: Build finished but APK was not found."
  exit 1
fi

echo
printf '[JARVIS] BUILD SUCCESS\nVERSION: 2.1.0 Standalone Cinema\nAPK: %s\n' "$APK"
