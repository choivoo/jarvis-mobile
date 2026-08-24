#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKER_DIR="$ROOT/backend/worker"

cd "$WORKER_DIR"

if [ -z "${CLOUDFLARE_API_TOKEN:-}" ]; then
  echo
  echo "[JARVIS V0.7] Cloudflare API Token required"
  echo "Paste your Cloudflare API token below. Input is hidden and only used for this shell process."
  read -rsp "Cloudflare API Token: " CLOUDFLARE_API_TOKEN
  echo
  export CLOUDFLARE_API_TOKEN
fi

if [ -z "${CLOUDFLARE_API_TOKEN:-}" ]; then
  echo "ERROR: Cloudflare API token is empty."
  exit 2
fi

VERIFY_JSON="$(curl -fsS -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" -H "Content-Type: application/json" https://api.cloudflare.com/client/v4/user/tokens/verify)" || {
  echo "ERROR: Cloudflare token verification request failed."
  exit 2
}

VERIFY_OK="$(printf '%s' "$VERIFY_JSON" | python3 -c 'import json,sys; print(str(bool(json.load(sys.stdin).get("success"))).lower())')"
if [ "$VERIFY_OK" != "true" ]; then
  echo "ERROR: Cloudflare API token is invalid or lacks access."
  exit 2
fi

if [ -z "${CLOUDFLARE_ACCOUNT_ID:-}" ]; then
  ACCOUNTS_JSON="$(curl -fsS -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" -H "Content-Type: application/json" 'https://api.cloudflare.com/client/v4/accounts?per_page=50')" || true
  CLOUDFLARE_ACCOUNT_ID="$(printf '%s' "$ACCOUNTS_JSON" | python3 -c '
import json,sys
try:
    data=json.load(sys.stdin)
    results=data.get("result") or []
    print(results[0].get("id", "") if results else "")
except Exception:
    print("")
')"
  if [ -z "$CLOUDFLARE_ACCOUNT_ID" ]; then
    read -rp "Cloudflare Account ID: " CLOUDFLARE_ACCOUNT_ID
  fi
  export CLOUDFLARE_ACCOUNT_ID
fi

echo "[JARVIS V0.7] Deploying Worker with API token authentication..."
npx wrangler deploy

echo
echo "[JARVIS V0.7] Worker deployment complete."
