#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKER_DIR="$ROOT/backend/worker"
CONFIG_FILE="$ROOT/jarvis.local.properties"

cd "$WORKER_DIR"

echo "[JARVIS V0.6] Installing Cloudflare Worker dependencies..."
npm install

echo "[JARVIS V0.6] Checking Cloudflare login..."
if ! npx wrangler whoami >/dev/null 2>&1; then
  npx wrangler login
fi

echo "[JARVIS V0.6] Initial Worker deployment..."
DEPLOY_OUTPUT="$(npx wrangler deploy 2>&1 | tee /dev/stderr)"
WORKER_URL="$(printf '%s\n' "$DEPLOY_OUTPUT" | grep -Eo 'https://[^ ]+\.workers\.dev' | tail -n 1 || true)"

if [ -z "$WORKER_URL" ]; then
  echo "ERROR: Worker URL could not be detected. Copy the workers.dev URL from the output and run:"
  echo "bash scripts/configure-cloud-v06.sh https://YOUR-WORKER.workers.dev"
  exit 1
fi

echo
echo "[JARVIS V0.6] OpenAI API key setup"
echo "The key is entered into Cloudflare's hidden Secret prompt and is never written to this repository or APK."
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
