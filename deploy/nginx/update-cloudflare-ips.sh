#!/usr/bin/env sh
# Cloudflare 엣지 IP 대역 목록을 새로 받아 cloudflare-realip.conf 를 다시 만든다.
#
#   sh deploy/nginx/update-cloudflare-ips.sh && \
#     docker compose -f docker-compose.prod.yml --env-file .env.prod exec nginx nginx -s reload
#
# Cloudflare 가 대역을 늘리면(드물지만 있다) 새 대역에서 온 요청의 CF-Connecting-IP 를
# nginx 가 신뢰하지 않아 그 사용자들의 IP 가 전부 "Cloudflare 엣지 주소" 하나로 뭉친다.
# 결과는 레이트리밋 오작동이다. 분기에 한 번 정도 돌려 두면 된다.
set -eu

cd "$(dirname "$0")"
OUT=cloudflare-realip.conf
TMP="$OUT.tmp"

{
    echo "# Cloudflare 엣지에서 온 요청만 CF-Connecting-IP 를 신뢰하도록 하는 목록."
    echo "# 손으로 고치지 말 것 — deploy/nginx/update-cloudflare-ips.sh 가 다시 만든다."
    echo "# 갱신: $(date -u +%Y-%m-%d) (https://www.cloudflare.com/ips-v4, ips-v6)"
    echo
    for url in https://www.cloudflare.com/ips-v4 https://www.cloudflare.com/ips-v6; do
        # `|| [ -n "$cidr" ]` 가 없으면 마지막 줄이 잘린다 — Cloudflare 응답은 끝에
        # 개행이 없어서, read 가 EOF 를 만나 실패로 끝나며 그 줄을 버린다.
        curl -fsS --max-time 20 "$url" | while read -r cidr || [ -n "$cidr" ]; do
            [ -n "$cidr" ] && echo "set_real_ip_from $cidr;"
        done
    done
    echo
    echo "# 위 대역에서 온 요청에 한해 이 헤더의 값을 실제 클라이언트 IP 로 삼는다."
    echo "# 헤더가 없으면(회색 구름 = DNS only) 소켓 주소가 그대로 유지된다."
    echo "real_ip_header CF-Connecting-IP;"
} > "$TMP"

# 목록이 비어 있으면 덮어쓰지 않는다 — 네트워크 실패로 신뢰 목록이 사라지면
# 모든 사용자 IP 가 Cloudflare 주소로 뭉쳐 레이트리밋이 오작동한다.
if ! grep -q '^set_real_ip_from' "$TMP"; then
    rm -f "$TMP"
    echo "!! Cloudflare 대역을 받지 못했다. 기존 $OUT 을 그대로 둔다."
    exit 1
fi

mv "$TMP" "$OUT"
echo "== $OUT 갱신 완료 ($(grep -c '^set_real_ip_from' "$OUT") 개 대역)"
