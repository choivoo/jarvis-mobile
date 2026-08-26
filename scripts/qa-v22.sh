#!/usr/bin/env bash
set -euo pipefail

fail() { echo "[QA V2.2.1] FAIL: $*" >&2; exit 1; }
pass() { echo "[QA V2.2.1] PASS: $*"; }

VOICE=app/src/main/java/com/choivoo/jarvis/voice/VoiceController.kt
ENGINE=app/src/main/java/com/choivoo/jarvis/core/JarvisAssistantEngine.kt
MANIFEST=app/src/main/AndroidManifest.xml
GRADLE=app/build.gradle.kts

[[ -f "$GRADLE" ]] || fail "app/build.gradle.kts missing"
[[ -f "$MANIFEST" ]] || fail "AndroidManifest.xml missing"
[[ -f app/src/main/java/com/choivoo/jarvis/diagnostics/CrashBlackBox.kt ]] || fail "Crash Black Box missing"
[[ -f app/src/main/java/com/choivoo/jarvis/JarvisApplication.kt ]] || fail "JarvisApplication missing"
[[ -f "$VOICE" ]] || fail "VoiceController missing"

grep -q 'applicationId = "com.choivoo.jarvis"' "$GRADLE" || fail "stable applicationId changed"
grep -q 'versionCode = 24' "$GRADLE" || fail "versionCode is not 24"
grep -q 'versionName = "2.2.1"' "$GRADLE" || fail "versionName is not 2.2.1"
grep -q 'create("jarvisPermanent")' "$GRADLE" || fail "permanent signing config missing"
grep -q 'JARVIS_RELEASE_STORE_FILE' "$GRADLE" || fail "release keystore env binding missing"
grep -q 'enableV3Signing = true' "$GRADLE" || fail "APK v3 signing not enabled"
grep -q 'android:name=".JarvisApplication"' "$MANIFEST" || fail "Crash Black Box Application is not registered"

if grep -q 'NotificationListenerService' "$MANIFEST"; then
  fail "NotificationListenerService must stay out of the standalone APK"
fi
if grep -q 'android.permission.CAMERA' "$MANIFEST"; then
  fail "Unused CAMERA permission must not ship before V2.3 vision permission flow is ready"
fi

grep -q 'basic-auto-safe' "$VOICE" || fail "AUTO safe basic fallback missing"
grep -q 'JarvisConfig.cloudEnabled' "$VOICE" || fail "AUTO cloud preference missing"
if grep -q 'neural-auto' "$VOICE"; then
  fail "AUTO must not enter native Neural TTS automatically"
fi
grep -q 'AtomicLong' "$VOICE" || fail "Voice generation guard missing"
grep -q 'speechGeneration' "$VOICE" || fail "Voice generation state missing"

grep -q 'JarvisSubtitleService.show' "$ENGINE" || fail "Korean overlay subtitle publishing missing"
grep -q 'CrashBlackBox.note' "$ENGINE" || fail "Assistant crash phase capture missing"

if grep -RIE --exclude-dir=.git --exclude='qa-v22.sh' '(sk-[A-Za-z0-9_-]{20,}|CLOUDFLARE_API_TOKEN[[:space:]]*=[[:space:]]*[^$[:space:]]+)' .; then
  fail "possible secret material detected"
fi

[[ -f scripts/prepare-standalone-neural.sh ]] || fail "neural preparation script missing"
[[ -f scripts/setup-permanent-signing.sh ]] || fail "permanent signing setup script missing"

pass "stable package id"
pass "permanent signing configuration"
pass "manifest permissions"
pass "voice AUTO routing"
pass "stale voice callback guard"
pass "crash diagnostics"
pass "subtitle bridge"
pass "secret scan"
pass "V2.2.1 release metadata"
echo "[QA V2.2.1] ALL GATES PASSED"
