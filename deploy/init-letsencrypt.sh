#!/usr/bin/env sh
# Let's Encrypt 인증서 최초 발급. VM 에서 딱 한 번 돌린다. 갱신은 compose 의 certbot
# 컨테이너가 12시간마다 알아서 한다(이 스크립트를 다시 부를 필요 없다).
#
#   cd /opt/bandapp && sh deploy/init-letsencrypt.sh
#
# 왜 따로 도는가: nginx 는 기동할 때 인증서 파일을 읽는다. 인증서가 없으면 설정 오류로
# 뜨지 못하고, 그러면 웹루트 챌린지를 서빙할 nginx 가 없어서 발급도 못 한다. 그래서 최초
# 1회만 --standalone(certbot 이 스스로 80 을 연다)으로 끊고 시작한다.
set -eu

cd "$(dirname "$0")/.."
[ -f .env.prod ] || { echo "!! .env.prod 가 없다. .env.prod.example 을 복사해 채운다"; exit 1; }
# shellcheck disable=SC1091
. ./.env.prod

: "${DOMAIN:?.env.prod 에 DOMAIN 을 설정한다}"
: "${LETSENCRYPT_EMAIL:?.env.prod 에 LETSENCRYPT_EMAIL 을 설정한다 (만료 경고 메일 수신)}"

COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env.prod"
PROJECT=$(basename "$PWD")

if $COMPOSE run --rm --entrypoint sh certbot -c "[ -d /etc/letsencrypt/live/$DOMAIN ]" 2>/dev/null; then
    echo "== $DOMAIN 인증서가 이미 있다. 아무것도 하지 않는다."
    exit 0
fi

echo "== 80 포트를 쓰는 컨테이너를 잠시 내린다"
$COMPOSE stop nginx 2>/dev/null || true

echo "== 인증서 발급 (--standalone, 80 포트 직접 사용)"
# STAGING=1 로 두면 레이트리밋 없는 테스트 서버로 발급한다(설정 검증용, 브라우저는 신뢰 안 함).
STAGING_ARG=""
[ "${STAGING:-0}" = "1" ] && STAGING_ARG="--staging"

docker run --rm -p 80:80 \
    -v "${PROJECT}_certbot-conf:/etc/letsencrypt" \
    -v "${PROJECT}_certbot-www:/var/www/certbot" \
    certbot/certbot certonly --standalone \
    $STAGING_ARG \
    -d "$DOMAIN" \
    --email "$LETSENCRYPT_EMAIL" \
    --agree-tos --no-eff-email --non-interactive

echo "== nginx 기동"
$COMPOSE up -d nginx
echo "== 완료. https://$DOMAIN/actuator/health 를 확인한다."
