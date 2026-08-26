#!/usr/bin/env bash
set -euo pipefail

REPO="choivoo/jarvis-mobile"
KEY_DIR="${HOME}/.jarvis"
KEYSTORE="${KEY_DIR}/jarvis-release.jks"
CREDENTIALS="${KEY_DIR}/jarvis-release.credentials"
ALIAS="jarvis-release"
BACKUP="${HOME}/JARVIS-RELEASE-KEY-BACKUP.jks"

mkdir -p "$KEY_DIR"
chmod 700 "$KEY_DIR"

command -v keytool >/dev/null || { echo "Java keytool이 필요합니다." >&2; exit 1; }
command -v gh >/dev/null || { echo "GitHub CLI(gh)가 필요합니다." >&2; exit 1; }
command -v openssl >/dev/null || { echo "openssl이 필요합니다." >&2; exit 1; }

ensure_secret_access() {
  if gh api "repos/${REPO}/actions/secrets/public-key" >/dev/null 2>&1; then
    return 0
  fi

  echo
  echo "Codespaces 기본 integration token에는 Actions Secrets 수정 권한이 없습니다."
  echo "GitHub 사용자 인증으로 전환합니다. 브라우저/기기 인증 화면이 나오면 승인해 주세요."
  echo

  unset GH_TOKEN GITHUB_TOKEN || true

  if ! gh auth status -h github.com >/dev/null 2>&1; then
    gh auth login -h github.com -p https -w
  else
    # Existing stored login may still lack repo scope. Refresh it interactively.
    gh auth refresh -h github.com -s repo,workflow || gh auth login -h github.com -p https -w
  fi

  gh api "repos/${REPO}/actions/secrets/public-key" >/dev/null 2>&1 || {
    echo
    echo "GitHub 사용자 인증은 되었지만 Actions Secrets 권한이 아직 없습니다."
    echo "다시 실행해 주세요:"
    echo "  unset GH_TOKEN GITHUB_TOKEN"
    echo "  gh auth refresh -h github.com -s repo,workflow"
    echo "  bash scripts/setup-permanent-signing.sh"
    exit 1
  }
}

load_or_create_key() {
  if [[ -f "$KEYSTORE" && -f "$CREDENTIALS" ]]; then
    # shellcheck disable=SC1090
    source "$CREDENTIALS"
    if [[ -z "${STORE_PASS:-}" || -z "${KEY_PASS:-}" ]]; then
      echo "로컬 서명 자격정보가 손상되었습니다. 새 키를 생성합니다."
      rm -f "$KEYSTORE" "$CREDENTIALS"
    else
      echo "기존 영구 JARVIS keystore를 사용합니다: $KEYSTORE"
      return 0
    fi
  fi

  if [[ -f "$KEYSTORE" && ! -f "$CREDENTIALS" ]]; then
    echo "이전 실패 실행에서 생성된 미등록 keystore를 발견했습니다."
    echo "비밀번호가 보존되지 않았으므로 이 키는 사용하지 않고 새 영구 키를 생성합니다."
    rm -f "$KEYSTORE"
  fi

  STORE_PASS="$(openssl rand -base64 48 | tr -d '/+=' | cut -c1-36)"
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

  umask 077
  cat > "$CREDENTIALS" <<EOF
STORE_PASS='$STORE_PASS'
KEY_PASS='$KEY_PASS'
EOF
  chmod 600 "$CREDENTIALS"
}

ensure_secret_access
load_or_create_key

CERT_SHA256="$(keytool -list -v -keystore "$KEYSTORE" -storepass "$STORE_PASS" -alias "$ALIAS" 2>/dev/null | awk -F': ' '/SHA256:/{print $2; exit}')"
[[ -n "$CERT_SHA256" ]] || { echo "인증서 SHA-256을 읽지 못했습니다." >&2; exit 1; }

base64 -w0 "$KEYSTORE" | gh secret set JARVIS_RELEASE_KEYSTORE_B64 --repo "$REPO"
printf '%s' "$STORE_PASS" | gh secret set JARVIS_RELEASE_STORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set JARVIS_RELEASE_KEY_ALIAS --repo "$REPO"
printf '%s' "$KEY_PASS" | gh secret set JARVIS_RELEASE_KEY_PASSWORD --repo "$REPO"
printf '%s' "$CERT_SHA256" | gh secret set JARVIS_RELEASE_CERT_SHA256 --repo "$REPO"

cp "$KEYSTORE" "$BACKUP"
chmod 600 "$BACKUP"

echo
echo "JARVIS 영구 서명 설정 완료"
echo "Certificate SHA-256: $CERT_SHA256"
echo "백업 파일: $BACKUP"
echo "중요: 이 keystore를 잃어버리면 이후 영구서명 버전을 기존 앱 위에 업데이트할 수 없습니다."

# Trigger the signed V2.2.1 release automatically. The workflow itself verifies
# that the produced APK certificate matches JARVIS_RELEASE_CERT_SHA256.
gh workflow run release-standalone.yml --repo "$REPO"
echo "V2.2.1 영구서명 Release workflow를 시작했습니다."

unset STORE_PASS KEY_PASS
