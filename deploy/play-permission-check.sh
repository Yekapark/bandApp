#!/usr/bin/env bash
# Play Developer API 권한 점검 — 서버에서 돌린다.
#
#   ssh -i ~/.ssh/bandule_deploy root@64.176.231.126 'bash -s' < deploy/play-permission-check.sh
#
# 서비스 계정 키로 토큰을 받아 가짜 purchase token 을 조회해 보고, 돌아온 에러로 판별한다.
# 실제 구매 없이 "서버가 Play 에 물어볼 수 있는 상태인가" 만 본다.
#
#   "insufficient permissions" (401) -> Play Console > 사용자 및 권한에 서비스 계정 초대가 안 됐다
#   "has not been used" / disabled    -> GCP 에서 Android Publisher API 가 꺼져 있다
#   "Invalid" / not found (400·404)   -> 권한 OK. 토큰만 가짜라서 나는 에러다
set -euo pipefail
KEY=${PLAY_SA_KEY:-/opt/bandapp/secrets/play-developer-sa.json}
PKG=${PLAY_PACKAGE:-com.yeka.bandule}
PEM=$(mktemp); trap 'rm -f "$PEM"' EXIT

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

EMAIL=$(python3 -c "import json;print(json.load(open('$KEY'))['client_email'])")
python3 -c "import json;open('$PEM','w').write(json.load(open('$KEY'))['private_key'])"
echo "service account: $EMAIL"

NOW=$(date +%s)
HEADER=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
CLAIM=$(printf '{"iss":"%s","scope":"https://www.googleapis.com/auth/androidpublisher","aud":"https://oauth2.googleapis.com/token","exp":%s,"iat":%s}' \
        "$EMAIL" "$((NOW+3600))" "$NOW" | b64url)
SIG=$(printf '%s.%s' "$HEADER" "$CLAIM" | openssl dgst -sha256 -sign "$PEM" | b64url)

RESP=$(curl -s -X POST https://oauth2.googleapis.com/token \
  -d grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer -d "assertion=$HEADER.$CLAIM.$SIG")
AT=$(python3 -c "import json,sys;print(json.load(sys.stdin).get('access_token',''))" <<< "$RESP")
[ -n "$AT" ] || { echo "토큰 발급 실패: $RESP"; exit 1; }
echo "access_token OK"

echo "--- subscriptionsv2 조회 (일부러 가짜 토큰) ---"
curl -s -w "\nHTTP %{http_code}\n" -H "Authorization: Bearer $AT" \
  "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PKG/purchases/subscriptionsv2/tokens/BOGUS-TOKEN-FOR-PERMISSION-PROBE"
