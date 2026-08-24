#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/backend/worker"

if [ -z "${CLOUDFLARE_API_TOKEN:-}" ]; then
  echo
  echo "[JARVIS V0.9] Cloudflare API Token required"
  read -rsp "Cloudflare API Token: " CLOUDFLARE_API_TOKEN
  echo
  export CLOUDFLARE_API_TOKEN
fi

VERIFY_JSON="$(curl -fsS -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" -H "Content-Type: application/json" https://api.cloudflare.com/client/v4/user/tokens/verify)" || {
  echo "ERROR: Cloudflare token verification failed."
  exit 2
}
VERIFY_OK="$(printf '%s' "$VERIFY_JSON" | python3 -c 'import json,sys; print(str(bool(json.load(sys.stdin).get("success"))).lower())')"
[ "$VERIFY_OK" = "true" ] || { echo "ERROR: Invalid Cloudflare API token."; exit 2; }

if [ -z "${CLOUDFLARE_ACCOUNT_ID:-}" ]; then
  ACCOUNTS_JSON="$(curl -fsS -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" -H "Content-Type: application/json" 'https://api.cloudflare.com/client/v4/accounts?per_page=50')" || true
  CLOUDFLARE_ACCOUNT_ID="$(printf '%s' "$ACCOUNTS_JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); r=d.get("result") or []; print(r[0].get("id","") if r else "")')"
  export CLOUDFLARE_ACCOUNT_ID
fi

[ -n "${CLOUDFLARE_ACCOUNT_ID:-}" ] || { echo "ERROR: Cloudflare Account ID not found."; exit 2; }

echo "[JARVIS V0.9] Deploying context-aware Personal Operations Brain..."
npx wrangler deploy

echo
 echo "[JARVIS V0.9] Worker deployment complete."
