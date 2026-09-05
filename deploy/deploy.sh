#!/usr/bin/env sh
# VM 에서 새 이미지를 받아 무중단에 가깝게 갈아 끼운다. GitHub Actions 가 SSH 로 이걸 부른다.
#
#   sh deploy/deploy.sh sha-abc1234      # 태그 지정
#   sh deploy/deploy.sh                  # latest
set -eu

cd "$(dirname "$0")/.."
COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env.prod"

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

echo "== 헬스체크 대기"
i=0
while [ $i -lt 60 ]; do
    if [ "$($COMPOSE ps -q app | xargs docker inspect -f '{{.State.Health.Status}}')" = "healthy" ]; then
        echo "== 배포 완료: $TAG"
        docker image prune -f >/dev/null 2>&1 || true
        exit 0
    fi
    sleep 5
    i=$((i + 1))
done

echo "!! 5분 안에 healthy 가 되지 않았다. 로그:"
$COMPOSE logs --tail 100 app
exit 1
