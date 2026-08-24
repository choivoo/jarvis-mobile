#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/backend/worker"

if [ -z "${CLOUDFLARE_API_TOKEN:-}" ]; then
  echo
  echo "[JARVIS V1.0] Cloudflare API Token required"
  read -rsp "Cloudflare API Token: " CLOUDFLARE_API_TOKEN
  echo
  export CLOUDFLARE_API_TOKEN
fi

if [ -z "${CLOUDFLARE_API_TOKEN:-}" ]; then
  echo "ERROR: Cloudflare API token is empty."
  exit 2
fi

VERIFY_JSON="$(curl -fsS -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" -H "Content-Type: application/json" https://api.cloudflare.com/client/v4/user/tokens/verify)" || {
  echo "ERROR: Cloudflare token verification failed."
  exit 2
}
VERIFY_OK="$(printf '%s' "$VERIFY_JSON" | python3 -c 'import json,sys; print(str(bool(json.load(sys.stdin).get("success"))).lower())')"
[ "$VERIFY_OK" = "true" ] || { echo "ERROR: invalid Cloudflare API token."; exit 2; }

if [ -z "${CLOUDFLARE_ACCOUNT_ID:-}" ]; then
  ACCOUNTS_JSON="$(curl -fsS -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" -H "Content-Type: application/json" 'https://api.cloudflare.com/client/v4/accounts?per_page=50')" || true
  CLOUDFLARE_ACCOUNT_ID="$(printf '%s' "$ACCOUNTS_JSON" | python3 -c 'import json,sys
try:
 d=json.load(sys.stdin); r=d.get("result") or []; print(r[0].get("id", "") if r else "")
except Exception: print("")')"
  if [ -z "$CLOUDFLARE_ACCOUNT_ID" ]; then read -rp "Cloudflare Account ID: " CLOUDFLARE_ACCOUNT_ID; fi
  export CLOUDFLARE_ACCOUNT_ID
fi

echo "[JARVIS V1.0] Deploying Personal Operations Brain..."
npx wrangler deploy

echo
echo "[JARVIS V1.0] Worker deployment complete."
