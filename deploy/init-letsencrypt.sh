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
ENV_FILE=.env.prod
# .env.prod 는 **docker compose 가 읽는 형식이지 셸 스크립트가 아니다.** `.`(source)은 각 줄을
# 셸 코드로 **실행**하므로 값에 #·<·>·따옴표가 섞이면 문법 에러로 죽는다. 비밀값을
# `openssl rand -base64` 로 만들면 특수문자가 섞이므로 **교체할 때마다 터질 수 있다.**
# 실제로 2026-09-08 유출 사고로 값을 전부 교체한 뒤 백업이 8일 동안 멈춰 있었고,
# 크론으로만 도는 스크립트라 아무도 몰랐다. 필요한 값만 뽑는다 (deploy/play-revoke.sh 와 같은 방식).
#
# 한계: 값 안에 공백 없이 붙은 `#` 는 주석으로 잘린다. compose 도 같게 동작하고,
# 여기서 읽는 값들(DB 이름·사용자·R2 키·도메인)에는 `#` 가 들어가지 않는다.
envget() { grep -m1 "^$1=" "$ENV_FILE" | cut -d= -f2- | sed "s/[[:space:]]*#.*$//; s/[[:space:]]*$//"; }
DOMAIN=$(envget DOMAIN); LETSENCRYPT_EMAIL=$(envget LETSENCRYPT_EMAIL)

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
