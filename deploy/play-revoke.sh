#!/usr/bin/env bash
# 활성 구독의 사용 권한을 취소한다 (Play Developer API) — 서버에서 돌린다.
#
#   ssh -i ~/.ssh/bandule_deploy root@64.176.231.126 'bash -s 4' < deploy/play-revoke.sh
#
# Play Console 에서 환불할 때 "사용 권한 취소" 를 안 눌렀으면 구독이 그대로 살아 있어
# RTDN type=12(REVOKED) 가 오지 않는다. 이 스크립트가 그 취소를 대신 쏜다.
# 취소가 성공하면 Play 가 REVOKED 알림을 보내고, 서버 로그에 "RTDN 처리 완료 type=12" 가 찍힌다.
#
# 구매 토큰은 band_plans 에서 읽는다 — 밴드가 PREMIUM 이고 store=GOOGLE_PLAY 여야 한다.
set -euo pipefail
BAND=${1:?사용법: play-revoke.sh <밴드ID>}
REFUND=${REFUND_TYPE:-fullRefund}   # fullRefund | proratedRefund
KEY=${PLAY_SA_KEY:-/opt/bandapp/secrets/play-developer-sa.json}
PKG=${PLAY_PACKAGE:-com.yeka.bandule}
COMPOSE_DIR=${COMPOSE_DIR:-/opt/bandapp}

# .env.prod 는 셸 스크립트가 아니다 — source 하면 값에 든 특수문자에서 깨진다. 필요한 값만 뽑는다.
envget() { grep -m1 "^$1=" "$COMPOSE_DIR/.env.prod" | cut -d= -f2- | sed "s/[[:space:]]*#.*$//; s/[[:space:]]*$//"; }
DB_USERNAME=$(envget DB_USERNAME); DB_NAME=$(envget DB_NAME)
TOKEN=$(docker exec bandapp-postgres-1 psql -U "$DB_USERNAME" -d "$DB_NAME" -tAc \
  "select purchase_token from band_plans where band_id = $BAND and store = 'GOOGLE_PLAY'")
[ -n "$TOKEN" ] || { echo "밴드 $BAND 에 붙은 Google Play 구매 토큰이 없다"; exit 1; }
echo "band=$BAND token=${TOKEN:0:12}…(${#TOKEN}자)"

PEM=$(mktemp); trap 'rm -f "$PEM"' EXIT
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
EMAIL=$(python3 -c "import json;print(json.load(open('$KEY'))['client_email'])")
python3 -c "import json;open('$PEM','w').write(json.load(open('$KEY'))['private_key'])"

NOW=$(date +%s)
HEADER=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
CLAIM=$(printf '{"iss":"%s","scope":"https://www.googleapis.com/auth/androidpublisher","aud":"https://oauth2.googleapis.com/token","exp":%s,"iat":%s}' \
        "$EMAIL" "$((NOW+3600))" "$NOW" | b64url)
SIG=$(printf '%s.%s' "$HEADER" "$CLAIM" | openssl dgst -sha256 -sign "$PEM" | b64url)
AT=$(curl -s -X POST https://oauth2.googleapis.com/token \
      -d grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer -d "assertion=$HEADER.$CLAIM.$SIG" \
     | python3 -c "import json,sys;print(json.load(sys.stdin).get('access_token',''))")
[ -n "$AT" ] || { echo "토큰 발급 실패"; exit 1; }

echo "--- 현재 구독 상태 ---"
curl -s -H "Authorization: Bearer $AT" \
  "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PKG/purchases/subscriptionsv2/tokens/$TOKEN" \
  | python3 -c "import json,sys;d=json.load(sys.stdin);print(json.dumps({k:d.get(k) for k in ('subscriptionState','latestOrderId','acknowledgementState')},indent=2,ensure_ascii=False))" \
  || echo "(조회 실패 — 아래 revoke 응답을 본다)"

echo "--- 사용 권한 취소 ($REFUND) ---"
curl -s -w "\nHTTP %{http_code}\n" -X POST \
  -H "Authorization: Bearer $AT" -H "Content-Type: application/json" \
  -d "{\"revocationContext\":{\"$REFUND\":{}}}" \
  "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PKG/purchases/subscriptionsv2/tokens/$TOKEN:revoke"

echo
echo "HTTP 200 이면 몇 분 안에 RTDN type=12 가 온다. 이미 환불된 주문이라 fullRefund 가 거부되면:"
echo "  REFUND_TYPE=proratedRefund 로 다시 실행"
