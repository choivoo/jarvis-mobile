#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../backend/worker"

echo "[JARVIS V2.3] MARK III Brain + Vision deployment"
if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]]; then
  read -rsp "Cloudflare API Token: " CLOUDFLARE_API_TOKEN
  echo
  export CLOUDFLARE_API_TOKEN
fi

if [[ -z "${CLOUDFLARE_ACCOUNT_ID:-}" ]]; then
  ACCOUNT_JSON="$(curl -fsS -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" https://api.cloudflare.com/client/v4/accounts)"
  CLOUDFLARE_ACCOUNT_ID="$(printf '%s' "$ACCOUNT_JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); r=d.get("result",[]); print(r[0]["id"] if r else "")')"
  export CLOUDFLARE_ACCOUNT_ID
fi

[[ -n "${CLOUDFLARE_ACCOUNT_ID:-}" ]] || { echo "Cloudflare account를 찾지 못했습니다." >&2; exit 1; }
echo "[JARVIS V2.3] Deploying Worker..."
npx wrangler deploy

echo "[JARVIS V2.3] Deployment complete."
unset CLOUDFLARE_API_TOKEN
