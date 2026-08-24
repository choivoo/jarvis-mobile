#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKER_DIR="$ROOT/backend/worker"
CONFIG_FILE="$ROOT/jarvis.local.properties"

cd "$WORKER_DIR"

echo "[JARVIS V0.6] Installing Cloudflare Worker dependencies..."
npm install

# Codespaces can be treated as a non-interactive environment by Wrangler.
# Use an API token instead of `wrangler login` so deployment is reliable.
if [ -z "${CLOUDFLARE_API_TOKEN:-}" ]; then
  echo
  echo "[JARVIS V0.6] Cloudflare API Token required"
  echo "Create a Cloudflare API token with the 'Edit Cloudflare Workers' template,"
  echo "then paste it below. The value is hidden and is NOT written to this repository."
  echo
  if [ ! -t 0 ]; then
    echo "ERROR: No interactive terminal is available."
    echo "Run this in the Codespaces terminal after setting CLOUDFLARE_API_TOKEN."
    exit 2
  fi
  read -rsp "Cloudflare API Token: " CLOUDFLARE_API_TOKEN
  echo
  export CLOUDFLARE_API_TOKEN
fi

if [ -z "${CLOUDFLARE_API_TOKEN:-}" ]; then
  echo "ERROR: Cloudflare API token is empty."
  exit 2
fi

echo "[JARVIS V0.6] Verifying Cloudflare token..."
VERIFY_JSON="$(curl -fsS \
  -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
  -H "Content-Type: application/json" \
  https://api.cloudflare.com/client/v4/user/tokens/verify)" || {
    echo "ERROR: Cloudflare token verification request failed."
    exit 2
  }

VERIFY_OK="$(printf '%s' "$VERIFY_JSON" | python3 -c 'import json,sys; print(str(bool(json.load(sys.stdin).get("success"))).lower())')"
if [ "$VERIFY_OK" != "true" ]; then
  echo "ERROR: Cloudflare API token is invalid or lacks access."
  exit 2
fi

# Resolve the account automatically when possible.
if [ -z "${CLOUDFLARE_ACCOUNT_ID:-}" ]; then
  ACCOUNTS_JSON="$(curl -fsS \
    -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
    -H "Content-Type: application/json" \
    'https://api.cloudflare.com/client/v4/accounts?per_page=50')" || true

  CLOUDFLARE_ACCOUNT_ID="$(printf '%s' "$ACCOUNTS_JSON" | python3 -c '
import json,sys
try:
    data=json.load(sys.stdin)
    results=data.get("result") or []
    print(results[0].get("id", "") if results else "")
except Exception:
    print("")
')"

  if [ -n "$CLOUDFLARE_ACCOUNT_ID" ]; then
    export CLOUDFLARE_ACCOUNT_ID
    echo "[JARVIS V0.6] Cloudflare account detected automatically."
  else
    echo "[JARVIS V0.6] Account ID could not be auto-detected."
    read -rp "Cloudflare Account ID: " CLOUDFLARE_ACCOUNT_ID
    export CLOUDFLARE_ACCOUNT_ID
  fi
fi

if [ -z "${CLOUDFLARE_ACCOUNT_ID:-}" ]; then
  echo "ERROR: Cloudflare Account ID is empty."
  exit 2
fi

echo "[JARVIS V0.6] Cloudflare authentication ready."
npx wrangler whoami

echo "[JARVIS V0.6] Initial Worker deployment..."
DEPLOY_OUTPUT="$(npx wrangler deploy 2>&1 | tee /dev/stderr)"
WORKER_URL="$(printf '%s\n' "$DEPLOY_OUTPUT" | grep -Eo 'https://[^ ]+\.workers\.dev' | tail -n 1 || true)"

if [ -z "$WORKER_URL" ]; then
  echo "ERROR: Worker URL could not be detected."
  echo "Copy the workers.dev URL from the output and run:"
  echo "bash scripts/configure-cloud-v06.sh https://YOUR-WORKER.workers.dev"
  exit 1
fi

echo
echo "[JARVIS V0.6] OpenAI API key setup"
echo "The key is entered into Cloudflare's hidden Secret prompt and is never written to GitHub or the APK."
npx wrangler secret put OPENAI_API_KEY

APP_TOKEN="$(python3 - <<'PY'
import secrets
print(secrets.token_urlsafe(32))
PY
)"
printf '%s' "$APP_TOKEN" | npx wrangler secret put JARVIS_APP_TOKEN >/dev/null

echo "[JARVIS V0.6] App authentication token created."

echo "[JARVIS V0.6] Final Worker deployment..."
npx wrangler deploy >/dev/null

cat > "$CONFIG_FILE" <<EOF
JARVIS_API_BASE_URL=$WORKER_URL
JARVIS_APP_TOKEN=$APP_TOKEN
EOF
chmod 600 "$CONFIG_FILE" || true

echo "[JARVIS V0.6] Testing Worker health..."
curl -fsS "$WORKER_URL/health"
echo

echo "[JARVIS V0.6] Cloud connection configured locally."
echo "Worker: $WORKER_URL"
echo "Private config: $CONFIG_FILE"
echo

echo "[JARVIS V0.6] Building Android APK..."
cd "$ROOT"
bash scripts/build-debug.sh

echo
echo "[JARVIS V0.6] COMPLETE"
echo "APK: $ROOT/app/build/outputs/apk/debug/app-debug.apk"
