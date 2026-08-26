#!/usr/bin/env bash
set -euo pipefail

REPO="choivoo/jarvis-mobile"
KEYSTORE="${HOME}/.jarvis/jarvis-release.jks"
ALIAS="jarvis-release"
mkdir -p "$(dirname "$KEYSTORE")"
chmod 700 "$(dirname "$KEYSTORE")"

command -v keytool >/dev/null || { echo "Java keytool이 필요합니다." >&2; exit 1; }
command -v gh >/dev/null || { echo "GitHub CLI(gh)가 필요합니다." >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "먼저 gh auth login을 완료해 주세요." >&2; exit 1; }

if [[ -f "$KEYSTORE" ]]; then
  echo "기존 영구 JARVIS keystore를 사용합니다: $KEYSTORE"
  read -rsp "기존 keystore 비밀번호: " STORE_PASS; echo
  KEY_PASS="$STORE_PASS"
else
  STORE_PASS="$(openssl rand -base64 36 | tr -d '/+=' | cut -c1-32)"
  KEY_PASS="$STORE_PASS"
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -dname "CN=JARVIS Mobile, OU=Personal Release, O=JARVIS, L=Seoul, C=KR"
  chmod 600 "$KEYSTORE"
fi

CERT_SHA256="$(keytool -list -v -keystore "$KEYSTORE" -storepass "$STORE_PASS" -alias "$ALIAS" 2>/dev/null | awk -F': ' '/SHA256:/{print $2; exit}')"
[[ -n "$CERT_SHA256" ]] || { echo "인증서 SHA-256을 읽지 못했습니다." >&2; exit 1; }

base64 -w0 "$KEYSTORE" | gh secret set JARVIS_RELEASE_KEYSTORE_B64 --repo "$REPO"
printf '%s' "$STORE_PASS" | gh secret set JARVIS_RELEASE_STORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set JARVIS_RELEASE_KEY_ALIAS --repo "$REPO"
printf '%s' "$KEY_PASS" | gh secret set JARVIS_RELEASE_KEY_PASSWORD --repo "$REPO"
printf '%s' "$CERT_SHA256" | gh secret set JARVIS_RELEASE_CERT_SHA256 --repo "$REPO"

BACKUP="${HOME}/JARVIS-RELEASE-KEY-BACKUP.jks"
cp "$KEYSTORE" "$BACKUP"
chmod 600 "$BACKUP"

echo
echo "JARVIS 영구 서명 설정 완료"
echo "Certificate SHA-256: $CERT_SHA256"
echo "백업 파일: $BACKUP"
echo "중요: 이 keystore를 잃어버리면 이후 버전을 기존 앱 위에 업데이트할 수 없습니다. 안전한 개인 저장소에 별도 백업해 주세요."
unset STORE_PASS KEY_PASS
