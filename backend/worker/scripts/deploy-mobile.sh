#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "[JARVIS] Installing Worker dependencies..."
npm install

echo "[JARVIS] Cloudflare login check..."
npx wrangler whoami || npx wrangler login

echo
printf '%s\n' "[JARVIS] IMPORTANT: Next you will enter your OpenAI API key securely." 
printf '%s\n' "The key is stored as a Cloudflare Worker secret, not in GitHub or the APK."

npx wrangler secret put OPENAI_API_KEY

echo "[JARVIS] Deploying jarvis-brain Worker..."
DEPLOY_OUTPUT="$(npx wrangler deploy 2>&1 | tee /dev/stderr)"

WORKER_URL="$(printf '%s\n' "$DEPLOY_OUTPUT" | grep -Eo 'https://[^ ]+\.workers\.dev' | tail -n 1 || true)"

if [ -z "$WORKER_URL" ]; then
  echo
  echo "[JARVIS] Worker deployed, but the workers.dev URL could not be detected automatically."
  echo "Copy the URL shown above and use scripts/set-api-url.sh from the repository root."
  exit 0
fi

echo
printf '[JARVIS] WORKER URL: %s\n' "$WORKER_URL"
echo "[JARVIS] Health check..."
curl -fsS "$WORKER_URL/health" || true

echo
printf '%s\n' "Next, from the repository root run:"
printf 'bash scripts/set-api-url.sh %s\n' "$WORKER_URL"
