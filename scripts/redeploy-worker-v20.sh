#!/usr/bin/env bash
set -euo pipefail

cd /workspaces/jarvis-mobile

if [ -z "${CLOUDFLARE_API_TOKEN:-}" ]; then
  printf "Cloudflare API Token: "
  read -rs CLOUDFLARE_API_TOKEN
  printf "\n"
  export CLOUDFLARE_API_TOKEN
fi

cd backend/worker
printf "[JARVIS V2.0] Deploying MARK II cloud brain...\n"
npx wrangler deploy
printf "[JARVIS V2.0] Worker deployment complete.\n"
