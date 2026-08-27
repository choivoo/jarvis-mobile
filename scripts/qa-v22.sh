#!/usr/bin/env bash
set -euo pipefail

fail() { echo "[QA V2.3.1] FAIL: $*" >&2; exit 1; }
pass() { echo "[QA V2.3.1] PASS: $*"; }

VOICE=app/src/main/java/com/choivoo/jarvis/voice/VoiceController.kt
ENGINE=app/src/main/java/com/choivoo/jarvis/core/JarvisAssistantEngine.kt
MANIFEST=app/src/main/AndroidManifest.xml
GRADLE=app/build.gradle.kts
VISION=app/src/main/java/com/choivoo/jarvis/vision/VisionActivity.kt
VISION_CLIENT=app/src/main/java/com/choivoo/jarvis/vision/VisionClient.kt
SHARE_VISION=app/src/main/java/com/choivoo/jarvis/vision/ShareVisionActivity.kt
MARK3=app/src/main/java/com/choivoo/jarvis/MarkIIIActivity.kt
TELEMETRY=app/src/main/java/com/choivoo/jarvis/telemetry/SystemTelemetry.kt
WORKER=backend/worker/src/index.ts

for f in "$GRADLE" "$MANIFEST" "$VOICE" "$ENGINE" "$VISION" "$VISION_CLIENT" "$SHARE_VISION" "$MARK3" "$TELEMETRY" "$WORKER"; do
  [[ -f "$f" ]] || fail "missing $f"
done
[[ -f app/src/main/java/com/choivoo/jarvis/diagnostics/CrashBlackBox.kt ]] || fail "Crash Black Box missing"
[[ -f app/src/main/java/com/choivoo/jarvis/JarvisApplication.kt ]] || fail "JarvisApplication missing"

grep -q 'applicationId = "com.choivoo.jarvis"' "$GRADLE" || fail "stable applicationId changed"
grep -q 'versionCode = 26' "$GRADLE" || fail "versionCode is not 26"
grep -q 'versionName = "2.3.1"' "$GRADLE" || fail "versionName is not 2.3.1"
grep -q 'create("jarvisPermanent")' "$GRADLE" || fail "permanent signing config missing"
grep -q 'JARVIS_RELEASE_STORE_FILE' "$GRADLE" || fail "release keystore env binding missing"
grep -q 'enableV3Signing = true' "$GRADLE" || fail "APK v3 signing not enabled"
grep -q 'android:name=".JarvisApplication"' "$MANIFEST" || fail "Crash Black Box Application is not registered"
grep -q 'android:name=".vision.VisionActivity"' "$MANIFEST" || fail "VisionActivity is not registered"
grep -q 'android:name=".vision.ShareVisionActivity"' "$MANIFEST" || fail "Screen Context share Activity is not registered"
grep -q 'android:name=".MarkIIIActivity"' "$MANIFEST" || fail "MARK III launcher missing"
grep -q 'android.intent.action.MAIN' "$MANIFEST" || fail "launcher MAIN intent missing"
grep -q 'android.intent.action.SEND' "$MANIFEST" || fail "screen share SEND intent missing"
grep -q 'android:mimeType="image/\*"' "$MANIFEST" || fail "screen share image MIME missing"
grep -q 'android.permission.ACCESS_NETWORK_STATE' "$MANIFEST" || fail "network telemetry permission missing"

if grep -q 'NotificationListenerService' "$MANIFEST"; then
  fail "NotificationListenerService must stay out of standalone APK"
fi
if grep -q 'android.permission.CAMERA' "$MANIFEST"; then
  fail "direct CAMERA permission is unnecessary for the safe system-camera Vision flow"
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
grep -q 'VisionActivity' "$ENGINE" || fail "voice command Vision launcher missing"
grep -q 'SystemTelemetry' "$ENGINE" || fail "telemetry context bridge missing"
grep -q 'TakePicturePreview' "$VISION" || fail "safe system-camera capture flow missing"
grep -q '/v1/vision' "$VISION_CLIENT" || fail "Vision client endpoint missing"
grep -q 'ACTION_SEND' "$SHARE_VISION" || fail "Screen Context ACTION_SEND handling missing"
grep -q 'lifecycleScope' "$SHARE_VISION" || fail "Screen Context lifecycle coroutine guard missing"
grep -q 'LIVE SYSTEM TELEMETRY' "$MARK3" || fail "MARK III live telemetry HUD missing"
grep -q 'VISION SCAN' "$MARK3" || fail "MARK III Vision launcher missing"
grep -q 'runCatching' "$TELEMETRY" || fail "telemetry fail-safe guard missing"
grep -q 'url.pathname === "/v1/vision"' "$WORKER" || fail "Worker Vision endpoint missing"
grep -q 'input_image' "$WORKER" || fail "Worker image input missing"

if grep -RIE --exclude-dir=.git --exclude='qa-v22.sh' '(sk-[A-Za-z0-9_-]{20,}|CLOUDFLARE_API_TOKEN[[:space:]]*=[[:space:]]*[^$[:space:]]+)' .; then
  fail "possible secret material detected"
fi

[[ -f scripts/prepare-standalone-neural.sh ]] || fail "neural preparation script missing"
[[ -f scripts/setup-permanent-signing.sh ]] || fail "permanent signing setup script missing"

pass "stable package id + permanent signing"
pass "MARK III launcher"
pass "Vision capture + analysis bridge"
pass "screenshot-share Screen Context"
pass "system telemetry crash safety"
pass "manifest permission minimisation"
pass "voice AUTO routing"
pass "stale voice callback guard"
pass "crash diagnostics"
pass "Korean subtitle bridge"
pass "secret scan"
pass "V2.3.1 release metadata"
echo "[QA V2.3.1] ALL GATES PASSED"
