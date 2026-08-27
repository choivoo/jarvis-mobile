#!/usr/bin/env bash
set -euo pipefail
fail() { echo "[QA V2.3.3] FAIL: $*" >&2; exit 1; }
pass() { echo "[QA V2.3.3] PASS: $*"; }
VOICE=app/src/main/java/com/choivoo/jarvis/voice/VoiceController.kt
GRADLE=app/build.gradle.kts
WAKE=app/src/main/java/com/choivoo/jarvis/wake/WakeRecognizer.kt
WAKE_SERVICE=app/src/main/java/com/choivoo/jarvis/wake/WakeWordService.kt
MANIFEST=app/src/main/AndroidManifest.xml
for f in "$VOICE" "$GRADLE" "$WAKE" "$WAKE_SERVICE" "$MANIFEST"; do [[ -f "$f" ]] || fail "missing $f"; done
grep -q 'applicationId = "com.choivoo.jarvis"' "$GRADLE" || fail "stable applicationId changed"
grep -q 'versionCode = 28' "$GRADLE" || fail "versionCode is not 28"
grep -q 'versionName = "2.3.3"' "$GRADLE" || fail "versionName is not 2.3.3"
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
if grep -RIE --exclude-dir=.git --exclude='qa-v22.sh' '(sk-[A-Za-z0-9_-]{20,}|CLOUDFLARE_API_TOKEN[[:space:]]*=[[:space:]]*[^$[:space:]]+)' .; then fail "possible secret material detected"; fi
pass "stable update identity"
pass "foreground code 7 suppression"
pass "partial wake rescue"
pass "repeated no-match recognizer recreation"
pass "background wake watchdog"
pass "post-TTS safety rearm"
echo "[QA V2.3.3] ALL GATES PASSED"
