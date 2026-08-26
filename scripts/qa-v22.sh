#!/usr/bin/env bash
set -euo pipefail

fail() { echo "[QA V2.2] FAIL: $*" >&2; exit 1; }
pass() { echo "[QA V2.2] PASS: $*"; }

[[ -f app/build.gradle.kts ]] || fail "app/build.gradle.kts missing"
[[ -f app/src/main/AndroidManifest.xml ]] || fail "AndroidManifest.xml missing"
[[ -f app/src/main/java/com/choivoo/jarvis/diagnostics/CrashBlackBox.kt ]] || fail "Crash Black Box missing"
[[ -f app/src/main/java/com/choivoo/jarvis/JarvisApplication.kt ]] || fail "JarvisApplication missing"
[[ -f app/src/main/java/com/choivoo/jarvis/voice/VoiceController.kt ]] || fail "VoiceController missing"

grep -q 'versionCode = 23' app/build.gradle.kts || fail "versionCode is not 23"
grep -q 'versionName = "2.2.0"' app/build.gradle.kts || fail "versionName is not 2.2.0"
grep -q 'android:name=".JarvisApplication"' app/src/main/AndroidManifest.xml || fail "Crash Black Box Application is not registered"

if grep -q 'NotificationListenerService' app/src/main/AndroidManifest.xml; then
  fail "NotificationListenerService must stay out of the standalone APK"
fi
if grep -q 'android.permission.CAMERA' app/src/main/AndroidManifest.xml; then
  fail "Unused CAMERA permission must not ship in V2.2"
fi

grep -q 'Crash-safe AUTO' app/src/main/java/com/choivoo/jarvis/voice/VoiceController.kt || fail "AUTO safe routing missing"
grep -q 'AtomicLong' app/src/main/java/com/choivoo/jarvis/voice/VoiceController.kt || fail "Voice generation guard missing"

grep -q 'JarvisSubtitleService.show' app/src/main/java/com/choivoo/jarvis/core/JarvisAssistantEngine.kt || fail "Korean overlay subtitle publishing missing"

# Public repository secret sanity checks. These are intentionally narrow to avoid false positives.
if grep -RIE --exclude-dir=.git --exclude='qa-v22.sh' '(sk-[A-Za-z0-9_-]{20,}|CLOUDFLARE_API_TOKEN[[:space:]]*=[[:space:]]*[^$[:space:]]+)' .; then
  fail "possible secret material detected"
fi

# Neural assets are optional at source checkout and are prepared by CI, but the preparation script must exist.
[[ -f scripts/prepare-standalone-neural.sh ]] || fail "neural preparation script missing"

pass "manifest permissions"
pass "voice routing and stale-callback guard"
pass "crash diagnostics"
pass "subtitle bridge"
pass "secret scan"
pass "V2.2 release metadata"
echo "[QA V2.2] ALL GATES PASSED"
