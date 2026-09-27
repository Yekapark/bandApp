#!/bin/sh
# 운영 서버가 **조용히** 망가지는 것들을 하루 한 번 본다.
#
# 왜 있나 — 배포 직후 헬스체크 말고는 감시가 없었다. 그래서 밴드에 안 붙은 구매 알림이
# 7일 동안 초당 한 번씩 서버를 때리는 동안 아무도 몰랐다
# (docs/TROUBLESHOOTING.md 2026-09-09). 여기 있는 검사는 전부 "터졌는데 아무도 안 보는"
# 종류다 — 요청 폭주, 디스크, 백업 멈춤, 에러 급증.
#
# 서버에서 직접:              sh deploy/prod-check.sh
# 내 PC 에서 서버로 흘려서:   ssh -i ~/.ssh/bandule_deploy root@64.176.231.126 'sh -s' < deploy/prod-check.sh
# 매일 자동:                  .github/workflows/prod-check.yml
#
# 이상이 있으면 exit 1. 임계값은 위쪽 변수로 몰아 뒀다 — 오탐이 잦으면 여기만 만진다.

ERR_MAX=${ERR_MAX:-200}        # 24시간 에러/예외 로그 줄 수
REQ_MAX=${REQ_MAX:-2000}       # 1시간 요청 수 (초당 1회 = 3600)
DISK_MAX=${DISK_MAX:-85}       # 루트 디스크 사용률 %
BACKUP_MAX_H=${BACKUP_MAX_H:-36}   # 마지막 백업이 몇 시간 안이어야 하는지

# QUIET=1 이면 숫자만 찍고 **로그 내용은 안 찍는다.**
# 이 저장소는 공개고 GitHub Actions 로그도 공개다 — 에러 줄·요청 경로에는 이메일·구매
# 토큰 같은 게 섞일 수 있다. 자동 점검은 QUIET=1 로 돌리고, 자세한 건 서버에서 직접 본다.
QUIET=${QUIET:-0}
detail() { [ "$QUIET" = 1 ] && { echo "   (자세한 내용은 서버에서 직접: sh deploy/prod-check.sh)"; return 1; }; return 0; }

cd "${APP_DIR:-/opt/bandapp}" 2>/dev/null || { echo "!! ${APP_DIR:-/opt/bandapp} 이 없다"; exit 1; }
C="docker compose -f docker-compose.prod.yml --env-file .env.prod"
fail=0
bad() { echo "!! $*"; fail=1; }

echo "== 운영 점검 $(date -u +%Y-%m-%dT%H:%MZ) ($(hostname))"

# docker 를 못 쓰면 아래 검사들이 전부 "0줄·0건" 으로 나와 **정상처럼 보인다.**
# 볼 수 없는 상태와 이상 없는 상태를 구분한다.
docker info >/dev/null 2>&1 || { echo "!! docker 에 접근할 수 없다 (데몬? 권한?)"; exit 1; }

# 1. 앱이 살아 있나. 앱 포트는 서버에 안 열려 있어서(80·443 은 Nginx 만) `curl localhost:8080`
#    은 늘 실패한다 — 처음에 그렇게 짰다가 멀쩡한 서버를 매일 "응답없음" 으로 걸었다.
#    deploy.sh 와 같이 compose 헬스체크 결과를 본다. 바깥 응답은 워크플로가 따로 본다.
h=$(docker inspect -f '{{.State.Health.Status}}' $($C ps -q app) 2>/dev/null)
if [ "$h" = healthy ]; then
    echo "health   healthy"
else
    bad "health   앱 컨테이너 상태가 healthy 가 아니다: ${h:-확인불가}"
fi

# 2. 컨테이너가 다 떠 있나
want=$($C config --services 2>/dev/null | wc -l)
up=$($C ps --services --filter status=running 2>/dev/null | wc -l)
if [ "$up" -lt "$want" ]; then
    bad "컨테이너  $up/$want 만 떠 있다"
    detail && $C ps
else
    echo "컨테이너 $up/$want"
fi

# 3. 디스크. 차면 DB 도 백업도 같이 죽는다.
use=$(df -P / | awk 'NR==2 {for (i = 1; i <= NF; i++) if ($i ~ /%$/) { gsub(/%/, "", $i); print $i; exit }}')
if [ "${use:-0}" -ge "$DISK_MAX" ]; then
    bad "디스크   ${use}% (기준 ${DISK_MAX}%) — docker system prune -f 를 먼저 본다"
else
    echo "디스크   ${use}%"
fi

# 4. 백업이 멈추지 않았나. 단일 VM 이라 이 백업이 사용자 데이터를 지키는 유일한 수단이고,
#    크론으로만 돌아서 **멈춰도 아무 알림이 없다.**
last=$(ls -t backups/*.dump 2>/dev/null | head -1)
if [ -z "$last" ]; then
    bad "백업     한 개도 없다"
else
    age_h=$(( ($(date +%s) - $(date -r "$last" +%s)) / 3600 ))
    if [ "$age_h" -gt "$BACKUP_MAX_H" ]; then
        bad "백업     마지막이 ${age_h}시간 전 ($last) — 크론과 /var/log/bandapp-backup.log 를 본다"
    else
        echo "백업     ${age_h}시간 전 ($(basename "$last"))"
    fi
fi

# 5. 에러 급증
errs=$(docker logs --since 24h bandapp-app-1 2>&1 | grep -ciE 'error|exception')
if [ "${errs:-0}" -gt "$ERR_MAX" ]; then
    bad "에러     24시간 ${errs}줄 (기준 ${ERR_MAX}) — 가장 많은 것:"
    detail && docker logs --since 24h bandapp-app-1 2>&1 | grep -iE 'error|exception' \
        | sed 's/^[^ ]* [^ ]* //' | cut -c1-90 | sort | uniq -c | sort -rn | head -5
else
    echo "에러     24시간 ${errs}줄"
fi

# 6. 요청 폭주. RTDN 재전송 폭풍을 찾은 방법 그대로다 — 어떤 경로가 얼마나 맞고 있는지.
reqs=$(docker logs --since 60m bandapp-nginx-1 2>&1 | wc -l)
if [ "${reqs:-0}" -gt "$REQ_MAX" ]; then
    bad "요청     1시간 ${reqs}건 (기준 ${REQ_MAX}) — 가장 많은 경로:"
    detail && docker logs --since 60m bandapp-nginx-1 2>&1 \
        | awk '{print $6, $7}' | sort | uniq -c | sort -rn | head -5
else
    echo "요청     1시간 ${reqs}건"
fi

[ "$fail" = 0 ] && echo "== 이상 없음"
exit $fail
