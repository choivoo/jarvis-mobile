#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPANION="$ROOT/notification-companion"

if [ ! -d "$COMPANION" ]; then
  echo "ERROR: notification-companion project not found."
  exit 2
fi

cd "$COMPANION"

if [ ! -x ./gradlew ]; then
  echo "[JARVIS Companion] Reusing root Gradle wrapper..."
  cp "$ROOT/gradlew" ./gradlew
  chmod +x ./gradlew
  mkdir -p gradle/wrapper
  cp "$ROOT/gradle/wrapper/gradle-wrapper.jar" gradle/wrapper/gradle-wrapper.jar
  cp "$ROOT/gradle/wrapper/gradle-wrapper.properties" gradle/wrapper/gradle-wrapper.properties
fi

echo "[JARVIS Companion] Building debug APK..."
./gradlew :app:assembleDebug --no-daemon

echo
echo "[JARVIS Companion] BUILD SUCCESS"
echo "APK: $COMPANION/app/build/outputs/apk/debug/app-debug.apk"
