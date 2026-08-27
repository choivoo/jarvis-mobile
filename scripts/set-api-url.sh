#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: bash scripts/set-api-url.sh https://jarvis-brain.<subdomain>.workers.dev"
  exit 1
fi

URL="${1%/}"
if [[ ! "$URL" =~ ^https:// ]]; then
  echo "ERROR: URL must start with https://"
  exit 1
fi

command -v gh >/dev/null || { echo "ERROR: GitHub CLI (gh) is required"; exit 1; }
if ! gh auth status >/dev/null 2>&1; then
  gh auth login
fi

printf '%s' "$URL" | gh secret set JARVIS_API_BASE_URL -R choivoo/jarvis-mobile

echo "[JARVIS] GitHub Actions Secret JARVIS_API_BASE_URL updated."
echo "[JARVIS] JarvisConfig.kt was NOT modified and no secret was committed to the repository."
echo "[JARVIS] A new Android build is required for the APK to receive the updated URL."
