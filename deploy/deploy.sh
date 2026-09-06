#!/usr/bin/env sh
# VM 에서 새 이미지를 받아 무중단에 가깝게 갈아 끼운다. GitHub Actions 가 SSH 로 이걸 부른다.
#
#   sh deploy/deploy.sh sha-abc1234      # 태그 지정
#   sh deploy/deploy.sh                  # latest
set -eu

cd "$(dirname "$0")/.."
COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env.prod"

# 배포 끝에 https://$DOMAIN 을 확인하므로 값이 필요하다. .env.prod 는 compose 에만 넘기고
# 이 셸에는 안 들어오기 때문에 직접 뽑는다(전체를 source 하면 비밀값이 이 셸에 다 풀린다).
DOMAIN=$(sed -n 's/^DOMAIN=//p' .env.prod | head -1 | sed 's/#.*//' | tr -d '[:space:]')
[ -n "$DOMAIN" ] || { echo "!! .env.prod 에 DOMAIN 이 없다"; exit 1; }

TAG="${1:-latest}"
# .env.prod 의 IMAGE_TAG 줄을 이번 태그로 갈아 끼운다(없으면 추가). 롤백할 때 이 파일만 보면 된다.
if grep -q '^IMAGE_TAG=' .env.prod; then
    sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=$TAG|" .env.prod
else
    printf '\nIMAGE_TAG=%s\n' "$TAG" >> .env.prod
fi

echo "== 이미지 받기 ($TAG)"
$COMPOSE pull app

echo "== 배포 전 백업 (되돌릴 곳을 만들어 두고 시작한다)"
sh deploy/backup/pg-backup.sh || echo "!! 백업 실패 — 배포는 계속하지만 확인할 것"

echo "== 교체"
$COMPOSE up -d --remove-orphans

echo "== 헬스체크 대기 (앱 컨테이너 내부)"
i=0
healthy=0
while [ $i -lt 60 ]; do
    if [ "$($COMPOSE ps -q app | xargs docker inspect -f '{{.State.Health.Status}}')" = "healthy" ]; then
        healthy=1
        break
    fi
    sleep 5
    i=$((i + 1))
done

if [ "$healthy" != 1 ]; then
    echo "!! 5분 안에 healthy 가 되지 않았다. 로그:"
    $COMPOSE logs --tail 100 app
    exit 1
fi

# 앱이 healthy 라는 건 "컨테이너 안에서" 응답한다는 뜻일 뿐이다. 실제 사용자는 Nginx 를
# 거쳐 들어오므로 바깥 주소로도 확인한다. 실제로 nginx 설정이 통째로 생성되지 않아
# 기본 페이지만 뜨는데 앱은 healthy 였던 적이 있다(deploy/nginx/docker-entrypoint.d 주석 참조).
echo "== 바깥 주소 확인 https://$DOMAIN/actuator/health"
i=0
while [ $i -lt 12 ]; do
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "https://$DOMAIN/actuator/health" || echo 000)
    if [ "$code" = "200" ]; then
        echo "== 배포 완료: $TAG"
        docker image prune -f >/dev/null 2>&1 || true
        exit 0
    fi
    sleep 5
    i=$((i + 1))
done

echo "!! 앱은 떴는데 https://$DOMAIN 으로는 200 이 아니다 (마지막 응답 $code)."
echo "   Nginx 설정과 인증서를 확인한다:"
$COMPOSE exec -T nginx ls -l /etc/nginx/conf.d/ 2>&1 || true
$COMPOSE logs --tail 30 nginx
exit 1
