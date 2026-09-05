#!/usr/bin/env sh
# 접속자 IP 판정이 실제로 맞는지 도커로 검증한다. VM 없이 돌아간다.
#
#   sh deploy/nginx/test-realip.sh
#   (윈도우 Git Bash: MSYS_NO_PATHCONV=1 sh deploy/nginx/test-realip.sh)
#
# 무엇을 지키는 테스트인가: 레이트리밋(로그인 브루트포스·초대코드 대입)의 IP 버킷이
# 위조 가능해지는 순간 그 방어는 전부 무의미해진다. 아래 다섯 케이스가 그 경계다.
# proxy-headers.conf 를 흔한 예제대로 $proxy_add_x_forwarded_for 로 되돌리면 ③⑤가 깨진다.
set -eu

cd "$(dirname "$0")/../.."
NGINX_DIR="$PWD/deploy/nginx"
# 작업 디렉터리를 리포 안에 만든다 — /tmp 는 윈도우 Docker Desktop 이 마운트하지 못한다
# (Git Bash 의 /tmp 는 도커가 보는 파일시스템에 없다). 끝나면 지운다.
WORK="$PWD/.realip-test-$$"
CF_NET=bandapp-realip-cf
EXT_NET=bandapp-realip-ext
PROXY=bandapp-realip-proxy
APP=bandapp-realip-app
fail=0

cleanup() {
    docker rm -f "$PROXY" "$APP" >/dev/null 2>&1 || true
    docker network rm "$CF_NET" "$EXT_NET" >/dev/null 2>&1 || true
    rm -rf "$WORK"
}
trap cleanup EXIT

mkdir -p "$WORK/le/live/api.example.com"
cat > "$WORK/echo.conf" <<'CONF'
server {
    listen 8080;
    location / { default_type text/plain; return 200 "$http_x_forwarded_for"; }
}
CONF

echo "== 자체 서명 인증서 생성 (nginx 가 뜨려면 인증서 파일이 있어야 한다)"
docker run --rm -v "$WORK/le:/out" alpine/openssl req -x509 -newkey rsa:2048 -nodes \
    -keyout /out/live/api.example.com/privkey.pem \
    -out /out/live/api.example.com/fullchain.pem \
    -days 1 -subj "/CN=api.example.com" >/dev/null 2>&1

# 104.16.0.0/13 은 Cloudflare 대역. 203.0.113.0/24 는 문서용(TEST-NET-3)이라 Cloudflare 가 아니다.
docker network create --subnet 104.16.99.0/24 "$CF_NET"  >/dev/null
docker network create --subnet 203.0.113.0/24 "$EXT_NET" >/dev/null

# --network-alias app: 프록시 설정의 proxy_pass 가 http://app:8080 이라, 이 이름으로
# 찾히지 않으면 nginx 가 기동 자체를 못 한다(업스트림 이름을 시작 시점에 해석한다).
docker run -d --name "$APP" --network "$CF_NET" --network-alias app \
    -v "$WORK/echo.conf:/etc/nginx/conf.d/default.conf:ro" nginx:1.27-alpine >/dev/null
docker network connect --alias app "$EXT_NET" "$APP"

docker run -d --name "$PROXY" --network "$CF_NET" \
    -e DOMAIN=api.example.com -e NGINX_ENVSUBST_FILTER='^DOMAIN$' \
    -v "$NGINX_DIR/templates:/etc/nginx/templates:ro" \
    -v "$NGINX_DIR/proxy-headers.conf:/etc/nginx/proxy-headers.conf:ro" \
    -v "$NGINX_DIR/cloudflare-realip.conf:/etc/nginx/cloudflare-realip.conf:ro" \
    -v "$WORK/le:/etc/letsencrypt:ro" \
    nginx:1.27-alpine >/dev/null
docker network connect "$EXT_NET" "$PROXY"

CF_IP=$(docker inspect -f "{{(index .NetworkSettings.Networks \"$CF_NET\").IPAddress}}" "$PROXY")
EXT_IP=$(docker inspect -f "{{(index .NetworkSettings.Networks \"$EXT_NET\").IPAddress}}" "$PROXY")

i=0
until docker run --rm --network "$CF_NET" curlimages/curl:latest -s -k -o /dev/null \
        --resolve "api.example.com:443:$CF_IP" https://api.example.com/ 2>/dev/null; do
    i=$((i + 1))
    [ $i -gt 15 ] && { echo "!! 프록시가 뜨지 않았다"; docker logs "$PROXY" | tail -20; exit 1; }
    sleep 1
done

hit() {
    net=$1; ip=$2; shift 2
    docker run --rm --network "$net" curlimages/curl:latest -s -k \
        --resolve "api.example.com:443:$ip" "$@" https://api.example.com/
}

# 기대값이 고정 문자열이 아닌 케이스(클라이언트 컨테이너 IP)는 대역으로 확인한다.
check() {
    label=$1; expect=$2; got=$3
    case "$got" in
        $expect) echo "  OK   $label  → $got" ;;
        *)       echo "  FAIL $label  → '$got' (기대: $expect)"; fail=1 ;;
    esac
}

echo "== 검증"
check "① CF 대역에서 CF-Connecting-IP 를 보냄 (인정)" \
      "9.9.9.9"      "$(hit "$CF_NET"  "$CF_IP"  -H 'CF-Connecting-IP: 9.9.9.9')"
check "② 외부에서 CF-Connecting-IP 위조 (무시)" \
      "203.0.113.*"  "$(hit "$EXT_NET" "$EXT_IP" -H 'CF-Connecting-IP: 9.9.9.9')"
check "③ 외부에서 X-Forwarded-For 위조 (무시)" \
      "203.0.113.*"  "$(hit "$EXT_NET" "$EXT_IP" -H 'X-Forwarded-For: 1.2.3.4')"
check "④ 헤더 없음 = 회색 구름 (소켓 주소)" \
      "104.16.99.*"  "$(hit "$CF_NET"  "$CF_IP")"
check "⑤ 외부에서 두 헤더 동시 위조 (둘 다 무시)" \
      "203.0.113.*"  "$(hit "$EXT_NET" "$EXT_IP" -H 'CF-Connecting-IP: 9.9.9.9' -H 'X-Forwarded-For: 1.2.3.4')"

[ "$fail" = 0 ] && echo "== 전부 통과" || { echo "== 실패 있음"; exit 1; }
