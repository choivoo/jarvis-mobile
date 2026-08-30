#!/usr/bin/env bash
set -euo pipefail
fail() { echo "[QA V2.4] FAIL: $*" >&2; exit 1; }
pass() { echo "[QA V2.4] PASS: $*"; }
VOICE=app/src/main/java/com/choivoo/jarvis/voice/VoiceController.kt
GRADLE=app/build.gradle.kts
WAKE=app/src/main/java/com/choivoo/jarvis/wake/WakeRecognizer.kt
WAKE_SERVICE=app/src/main/java/com/choivoo/jarvis/wake/WakeWordService.kt
MANIFEST=app/src/main/AndroidManifest.xml
ACTION_CORE=app/src/main/java/com/choivoo/jarvis/tools/ActionCore.kt
for f in "$VOICE" "$GRADLE" "$WAKE" "$WAKE_SERVICE" "$MANIFEST" "$ACTION_CORE"; do [[ -f "$f" ]] || fail "missing $f"; done
grep -q 'applicationId = "com.choivoo.jarvis"' "$GRADLE" || fail "stable applicationId changed"
grep -q 'versionCode = 29' "$GRADLE" || fail "versionCode is not 29"
grep -q 'versionName = "2.4.0"' "$GRADLE" || fail "versionName is not 2.4.0"
grep -q 'create("jarvisPermanent")' "$GRADLE" || fail "permanent signing config missing"
grep -q 'ACCESS_NETWORK_STATE' "$MANIFEST" || fail "network permission missing"
grep -q 'noMatchStreak' "$WAKE" || fail "no-match streak recovery missing"
grep -q 'ensureActive' "$WAKE" || fail "wake watchdog hook missing"
grep -q 'recreateAndRestart(700L)' "$WAKE" || fail "code 7 recognizer recreation missing"
grep -q 'PARTIAL_WAKE_GRACE_MS = 450L' "$WAKE_SERVICE" || fail "partial wake grace missing"
grep -q 'WATCHDOG_MS = 8_000L' "$WAKE_SERVICE" || fail "background watchdog missing"
grep -q 'recognizer.ensureActive()' "$WAKE_SERVICE" || fail "background watchdog not wired"
grep -q 'handlePartialText' "$WAKE_SERVICE" || fail "partial wake handler missing"
grep -q 'POST_TTS_REARM_DELAY_MS = 900L' "$WAKE_SERVICE" || fail "post-TTS safety delay missing"
grep -q 'manualListening' "$VOICE" || fail "manual recognizer state guard missing"
grep -q 'SpeechRecognizer.ERROR_NO_MATCH' "$VOICE" || fail "foreground code 7 handling missing"
grep -q 'basic-auto-safe' "$VOICE" || fail "safe AUTO voice route missing"
grep -q 'google.navigation:q=' "$ACTION_CORE" || fail "navigation action missing"
grep -q 'ACTION_SET_ALARM' "$ACTION_CORE" || fail "alarm action missing"
grep -q 'Intent.createChooser' "$ACTION_CORE" || fail "share confirmation chooser missing"
grep -q 'ACTION_BLUETOOTH_SETTINGS' "$ACTION_CORE" || fail "Bluetooth settings action missing"
grep -q 'FOLLOW_UP_WINDOW_MS = 8_000L' "$WAKE_SERVICE" || fail "voice follow-up window missing"
grep -q 'follow-up-window' "$WAKE_SERVICE" || fail "voice follow-up state not wired"
grep -q 'previousObservation' app/src/main/java/com/choivoo/jarvis/vision/VisionActivity.kt || fail "Vision session memory missing"
grep -q 'SCAN AGAIN' app/src/main/java/com/choivoo/jarvis/vision/VisionActivity.kt || fail "Vision rescan missing"
if grep -RIE --exclude-dir=.git --exclude='qa-v22.sh' '(sk-[A-Za-z0-9_-]{20,}|CLOUDFLARE_API_TOKEN[[:space:]]*=[[:space:]]*[^$[:space:]]+)' .; then fail "possible secret material detected"; fi
pass "stable update identity"
pass "foreground code 7 suppression"
pass "partial wake rescue"
pass "repeated no-match recognizer recreation"
pass "background wake watchdog"
pass "post-TTS safety rearm"
pass "allow-listed V2.4 Action Core"
pass "eight-second Voice follow-up window"
pass "continuous Vision session context"
echo "[QA V2.4] ALL GATES PASSED"
