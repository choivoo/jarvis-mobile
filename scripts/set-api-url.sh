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

FILE="app/src/main/java/com/choivoo/jarvis/config/JarvisConfig.kt"
cat > "$FILE" <<EOF
package com.choivoo.jarvis.config

object JarvisConfig {
    const val API_BASE_URL = "$URL"

    val cloudEnabled: Boolean
        get() = API_BASE_URL.startsWith("https://")
}
EOF

echo "[JARVIS] API URL updated: $URL"
echo "[JARVIS] Rebuild with: bash scripts/build-debug.sh"
