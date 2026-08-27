#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
WORKER_DIR="$ROOT/backend/worker"
cd "$WORKER_DIR"

command -v npm >/dev/null || { echo "ERROR: npm is required"; exit 1; }
command -v gh >/dev/null || { echo "ERROR: GitHub CLI (gh) is required"; exit 1; }
command -v openssl >/dev/null || { echo "ERROR: openssl is required"; exit 1; }

echo "[JARVIS] Installing Worker dependencies..."
npm install

echo "[JARVIS] Checking Cloudflare authentication..."
if ! npx wrangler whoami >/dev/null 2>&1; then
  echo "[JARVIS] Cloudflare login is required. Complete the browser approval when it opens."
  npx wrangler login
fi

echo "[JARVIS] Checking GitHub authentication..."
if ! gh auth status >/dev/null 2>&1; then
  echo "[JARVIS] GitHub login is required."
  gh auth login
fi

echo
printf '%s\n' "[JARVIS] OpenAI API key setup"
printf '%s\n' "Enter the key only into the Wrangler prompt. Do NOT paste it into chat or commit it to GitHub."
npx wrangler secret put OPENAI_API_KEY

APP_TOKEN="$(openssl rand -hex 32)"
printf '%s' "$APP_TOKEN" | npx wrangler secret put JARVIS_APP_TOKEN >/dev/null

echo "[JARVIS] Deploying jarvis-brain Worker..."
DEPLOY_OUTPUT="$(npx wrangler deploy 2>&1 | tee /dev/stderr)"
WORKER_URL="$(printf '%s\n' "$DEPLOY_OUTPUT" | grep -Eo 'https://[^[:space:]]+\.workers\.dev' | tail -n 1 || true)"

if [ -z "$WORKER_URL" ]; then
  echo
  echo "ERROR: Worker deployment finished, but the workers.dev URL could not be detected."
  echo "Run 'npx wrangler deployments list' or copy the workers.dev URL from the deployment output, then run:"
  echo "  bash scripts/set-api-url.sh https://YOUR-WORKER.workers.dev"
  exit 2
fi

WORKER_URL="${WORKER_URL%/}"

echo "[JARVIS] Health check: $WORKER_URL/health"
curl -fsS "$WORKER_URL/health" >/dev/null

echo "[JARVIS] Registering Android build configuration in GitHub Actions Secrets..."
printf '%s' "$WORKER_URL" | gh secret set JARVIS_API_BASE_URL -R choivoo/jarvis-mobile
printf '%s' "$APP_TOKEN" | gh secret set JARVIS_APP_TOKEN -R choivoo/jarvis-mobile

unset APP_TOKEN

echo
echo "JARVIS CLOUD SETUP COMPLETE"
echo "Worker URL: $WORKER_URL"
echo "GitHub Actions Secrets: JARVIS_API_BASE_URL + JARVIS_APP_TOKEN registered"
echo "OpenAI key: stored only as Cloudflare Worker secret"
echo
echo "IMPORTANT: the current public APK was built before these values existed."
echo "A new Android build is required before Cloud Brain can work in the app."
