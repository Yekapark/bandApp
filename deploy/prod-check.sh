#!/bin/sh
# 운영 서버가 **조용히** 망가지는 것들을 하루 한 번 본다.
#
# 왜 있나 — 배포 직후 헬스체크 말고는 감시가 없었다. 그래서 밴드에 안 붙은 구매 알림이
# 7일 동안 초당 한 번씩 서버를 때리는 동안 아무도 몰랐다
# (docs/TROUBLESHOOTING.md 2026-09-09). 여기 있는 검사는 전부 "터졌는데 아무도 안 보는"
# 종류다 — 요청 폭주, 디스크, 메모리, 백업 멈춤, 에러 급증, HTTPS 인증서 만료, 강제 종료.
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
CERT_MIN_DAYS=${CERT_MIN_DAYS:-21} # HTTPS 인증서가 최소 며칠 남아 있어야 하는지 (certbot 은 30일 전부터 갱신한다)
MEM_MIN_FREE=${MEM_MIN_FREE:-10}   # 서버 메모리 중 새로 쓸 수 있는 몫(available)이 최소 몇 % 남아야 하는지
REDIS_MAX_MB=${REDIS_MAX_MB:-256}  # Redis 가 쓰는 메모리 상한(MB). 키가 전부 유효기간이 있어 평소엔 몇 MB 다
REBOOT_MAX_D=${REBOOT_MAX_D:-7}    # 보안 업데이트가 재부팅을 며칠 넘게 기다리면 알릴지

# QUIET=1 이면 숫자만 찍고 **로그 내용은 안 찍는다.**
# 이 저장소는 공개고 GitHub Actions 로그도 공개다 — 에러 줄·요청 경로에는 이메일·구매
# 토큰 같은 게 섞일 수 있다. 자동 점검은 QUIET=1 로 돌리고, 자세한 건 서버에서 직접 본다.
QUIET=${QUIET:-0}
detail() { [ "$QUIET" = 1 ] && { echo "   (자세한 내용은 서버에서 직접: sh deploy/prod-check.sh)"; return 1; }; return 0; }

cd "${APP_DIR:-/opt/bandapp}" 2>/dev/null || { echo "!! ${APP_DIR:-/opt/bandapp} 이 없다"; exit 1; }
C="docker compose -f docker-compose.prod.yml --env-file .env.prod"
fail=0
bad() { echo "!! $*"; fail=1; }
# 걸린 검사 바로 아래 처리 명령. 매일 점검 워크플로가 `→` 줄을 메일 본문에 그대로 옮긴다 — 내 PC 저장소 폴더(Git Bash)에서
# 그대로 치면 되게 쓴다. 토큰·이메일 같은 값은 넣지 않는다(공개 로그).
SSH="ssh -i ~/.ssh/bandule_deploy root@64.176.231.126"
fix() { echo "   → $*"; }

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
    fix "원인 보기: $SSH 'bandule errors'"
    fix "앱 다시 띄우기(30~60초 끊김): $SSH 'cd /opt/bandapp && bandule restart'"
fi

# 2. 컨테이너가 다 떠 있나
want=$($C config --services 2>/dev/null | wc -l)
up=$($C ps --services --filter status=running 2>/dev/null | wc -l)
if [ "$up" -lt "$want" ]; then
    bad "컨테이너  $up/$want 만 떠 있다"
    fix "어느 게 꺼졌나: $SSH 'cd /opt/bandapp && docker compose -f docker-compose.prod.yml --env-file .env.prod ps -a'"
    fix "다 다시 띄우기: $SSH 'cd /opt/bandapp && docker compose -f docker-compose.prod.yml --env-file .env.prod up -d'"
    detail && $C ps
else
    echo "컨테이너 $up/$want"
fi

# 3. 디스크. 차면 DB 도 백업도 같이 죽는다.
use=$(df -P / | awk 'NR==2 {for (i = 1; i <= NF; i++) if ($i ~ /%$/) { gsub(/%/, "", $i); print $i; exit }}')
if [ "${use:-0}" -ge "$DISK_MAX" ]; then
    bad "디스크   ${use}% (기준 ${DISK_MAX}%) — 차면 DB·백업이 멈춘다"
    fix "무엇이 차지하나: $SSH 'cd /opt/bandapp && bandule disk'"
    fix "안 쓰는 도커 이미지 정리(데이터는 안 지움): $SSH 'docker system prune -f'"
else
    echo "디스크   ${use}%"
fi

# 4. 백업이 멈추지 않았나. 단일 VM 이라 이 백업이 사용자 데이터를 지키는 유일한 수단이고,
#    크론으로만 돌아서 **멈춰도 아무 알림이 없다.**
last=$(ls -t backups/*.dump 2>/dev/null | head -1)
if [ -z "$last" ]; then
    bad "백업     한 개도 없다"
    fix "지금 한 번 백업: $SSH 'cd /opt/bandapp && bandule backup'"
else
    age_h=$(( ($(date +%s) - $(date -r "$last" +%s)) / 3600 ))
    if [ "$age_h" -gt "$BACKUP_MAX_H" ]; then
        bad "백업     마지막이 ${age_h}시간 전 ($last) — 매일 백업이 멈췄다"
        fix "왜 멈췄나: $SSH 'tail -30 /var/log/bandapp-backup.log; crontab -l | grep -i backup'"
        fix "지금 한 번 백업: $SSH 'cd /opt/bandapp && bandule backup'"
    else
        echo "백업     ${age_h}시간 전 ($(basename "$last"))"
    fi
fi

# 5. 에러 급증
errs=$(docker logs --since 24h bandapp-app-1 2>&1 | grep -ciE 'error|exception')
if [ "${errs:-0}" -gt "$ERR_MAX" ]; then
    bad "에러     24시간 ${errs}줄 (기준 ${ERR_MAX}) — 무언가 반복해서 실패 중"
    fix "어떤 에러가 많은지: $SSH 'sh -s' < deploy/prod-check.sh"
    detail && docker logs --since 24h bandapp-app-1 2>&1 | grep -iE 'error|exception' \
        | sed 's/^[^ ]* [^ ]* //' | cut -c1-90 | sort | uniq -c | sort -rn | head -5
else
    echo "에러     24시간 ${errs}줄"
fi

# 6. 요청 폭주. RTDN 재전송 폭풍을 찾은 방법 그대로다 — 어떤 경로가 얼마나 맞고 있는지.
reqs=$(docker logs --since 60m bandapp-nginx-1 2>&1 | wc -l)
if [ "${reqs:-0}" -gt "$REQ_MAX" ]; then
    bad "요청     1시간 ${reqs}건 (기준 ${REQ_MAX}) — 공격이나 재전송 폭주일 수 있다"
    fix "어느 경로가 맞고 있나: $SSH 'sh -s' < deploy/prod-check.sh"
    detail && docker logs --since 60m bandapp-nginx-1 2>&1 \
        | awk '{print $6, $7}' | sort | uniq -c | sort -rn | head -5
else
    echo "요청     1시간 ${reqs}건"
fi

# 7. HTTPS 인증서 만료. certbot 컨테이너가 12시간마다 갱신하고 nginx 가 6시간마다 다시 읽지만, 둘 중 하나가
#    조용히 멈추면 90일째에 앱 접속이 전부 끊긴다. **실제로 내보내는 인증서**를 재야 갱신과 reload 를 한 번에 본다.
#    certbot 은 30일 남았을 때부터 갱신하므로 21일 아래로 내려왔다면 갱신이 실패하고 있는 것이다(2026-09-30 추가).
domain=$(grep -m1 '^DOMAIN=' .env.prod 2>/dev/null | cut -d= -f2- | tr -d '"' | tr -cd 'A-Za-z0-9.-')
end=$(echo | openssl s_client -connect 127.0.0.1:443 -servername "${domain:-localhost}" 2>/dev/null \
      | openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2)
if [ -z "$end" ]; then
    bad "인증서   읽지 못했다 (DOMAIN=${domain:-없음}) — nginx 가 443 에 떠 있는지 본다"
    fix "nginx 상태: $SSH 'cd /opt/bandapp && docker compose -f docker-compose.prod.yml --env-file .env.prod logs --tail 30 nginx'"
else
    days=$(( ($(date -d "$end" +%s) - $(date +%s)) / 86400 ))
    if [ "$days" -lt "$CERT_MIN_DAYS" ]; then
        bad "인증서   ${days}일 남음 (기준 ${CERT_MIN_DAYS}일) — 자동 갱신이 멈췄다. 만료일에 앱 접속이 끊긴다"
        fix "갱신 기록: $SSH 'cd /opt/bandapp && docker compose -f docker-compose.prod.yml --env-file .env.prod logs --tail 50 certbot'"
    else
        echo "인증서   ${days}일 남음"
    fi
fi

# 8. 탈퇴한 결제자의 구독 해지 실패(LAUNCH_REVIEW B17). 한 건이라도 있으면 실패다 — 에러 급증(5번)은 200줄을 넘어야
#    걸려서 해지 실패 한 건은 묻혔고, 묻히면 탈퇴한 사람에게 다음 해에도 청구된다. 서버가 매시 다시 시도하고 그때마다
#    이 표시를 남기므로, 계속 걸리면 bandId 를 보고 Play Console › 주문 관리에서 직접 해지한다. 해지되면 다음 재시도가
#    확인하고 표시를 그만 남긴다(점검은 24시간을 보므로 하루 뒤 풀린다).
cancel_fail=$(docker logs --since 24h bandapp-app-1 2>&1 | grep -c 'SUBSCRIPTION_CANCEL_FAILED')
if [ "${cancel_fail:-0}" -gt 0 ]; then
    bad "구독해지 24시간 ${cancel_fail}건 실패 — 탈퇴한 결제자의 구독이 아직 청구될 수 있다(서버가 매시 다시 시도)"
    fix "어느 밴드인지: $SSH 'sh -s' < deploy/prod-check.sh   (구독해지 아래 bandId)"
    fix "계속 걸리면 그 밴드 구독을 Play Console › 주문 관리에서 직접 해지 — 다음 재시도가 확인하고 멈춘다"
    detail && docker logs --since 24h bandapp-app-1 2>&1 | grep 'SUBSCRIPTION_CANCEL_FAILED' \
        | grep -o 'userId=[0-9]* bandId=[0-9]* cause=.*— ' | sort | uniq -c | tail -5
else
    echo "구독해지 실패 0건"
fi

# 9. 멤버 0명 밴드 삭제 실패(LAUNCH_REVIEW L7). 마지막 멤버가 탈퇴한 밴드는 서버가 지우고, 실패하면 10분마다 다시 하며 이 표시를
#    남긴다. 계속 걸리면 cause 를 본다 — R2 장애면 기다리고, 구독 해지 실패면 Play Console › 주문 관리에서 직접 해지한다.
purge_fail=$(docker logs --since 24h bandapp-app-1 2>&1 | grep -c 'MEMBERLESS_BAND_PURGE_FAILED')
if [ "${purge_fail:-0}" -gt 0 ]; then
    bad "빈밴드삭제 24시간 ${purge_fail}건 실패 — 탈퇴한 사람의 글·사진이 아직 남아 있다(서버가 10분마다 다시 시도)"
    fix "왜 실패하나: $SSH 'sh -s' < deploy/prod-check.sh   (cause 가 R2 면 기다리고, 구독 해지 실패면 Play Console 에서 직접 해지)"
    detail && docker logs --since 24h bandapp-app-1 2>&1 | grep 'MEMBERLESS_BAND_PURGE_FAILED'         | grep -o 'bandId=[0-9]* cause=.*— ' | sort | uniq -c | tail -5
else
    echo "빈밴드삭제 실패 0건"
fi

# 11. 메모리. 차면 리눅스가 아무 프로세스나 강제로 끝낸다(OOM) — 보통 제일 큰 앱이나 DB 가 죽는다. 디스크처럼
#     서서히 차는 것을 미리 본다. available = 캐시를 비우면 새로 쓸 수 있는 몫(free 보다 정확하다).
mem=$(awk '/^MemTotal:/ {t=$2} /^MemAvailable:/ {a=$2} END {if (t) printf "%d %d %d", a*100/t, (t-a)/1024, t/1024}' /proc/meminfo)
set -- $mem
if [ -z "$1" ]; then
    bad "메모리   읽지 못했다 (/proc/meminfo)"
elif [ "$1" -lt "$MEM_MIN_FREE" ]; then
    bad "메모리   남은 몫 $1% (사용 $2/$3MB, 기준 ${MEM_MIN_FREE}% 이상) — 더 차면 앱이나 DB 가 강제 종료된다"
    fix "누가 쓰나: $SSH 'docker stats --no-stream --format \"{{.Name}} {{.MemUsage}}\"'"
    fix "앱만 다시 띄워 비우기(30~60초 끊김): $SSH 'cd /opt/bandapp && bandule restart'"
else
    echo "메모리   남은 몫 $1% (사용 $2/$3MB)"
fi

# 12. Redis 메모리. 넣는 키가 전부 유효기간이 있어 평소엔 몇 MB 다 — 커지면 유효기간 없는 키가 새로 생긴 것이다.
redis_mb=$(docker exec bandapp-redis-1 sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli INFO memory' 2>/dev/null            | awk -F: '/^used_memory:/ {printf "%d", $2/1048576}')
redis_keys=$(docker exec bandapp-redis-1 sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli DBSIZE' 2>/dev/null | tr -cd '0-9')
if [ -z "$redis_mb" ]; then
    bad "Redis    읽지 못했다 — 컨테이너가 꺼졌거나 비밀번호가 안 맞는다"
    fix "상태: $SSH 'docker logs --tail 30 bandapp-redis-1'"
elif [ "$redis_mb" -gt "$REDIS_MAX_MB" ]; then
    bad "Redis    ${redis_mb}MB · 키 ${redis_keys:-?}개 (기준 ${REDIS_MAX_MB}MB) — 유효기간 없는 키가 쌓이는 중일 수 있다"
    fix "어떤 키가 많나: $SSH 'docker exec bandapp-redis-1 sh -c \"REDISCLI_AUTH=\\\$REDIS_PASSWORD redis-cli --scan | cut -d: -f1-2 | sort | uniq -c | sort -rn | head\"'"
    fix "모르겠으면 이 메일을 Claude 에게 — 유효기간 없는 키를 넣는 코드를 찾아 고친다"
else
    echo "Redis    ${redis_mb}MB · 키 ${redis_keys:-?}개"
fi

# 13. 강제 종료·재시작. 메모리가 모자라 커널이 죽였거나(OOMKilled) 앱이 스스로 죽어 docker 가 다시 띄운 횟수.
#     "지금은 떠 있음" 이라 health·컨테이너 검사로는 안 보인다. 배포하면 컨테이너가 새로 생겨 0 부터 센다.
crash=$(for c in $(docker ps -q --filter name=bandapp-); do
          docker inspect -f '{{.Name}} {{.RestartCount}} {{.State.OOMKilled}}' "$c"
        done 2>/dev/null | awk '$2 > 0 || $3 == "true" {sub("^/", "", $1); printf "%s(재시작 %s, OOM %s) ", $1, $2, $3}')
if [ -n "$crash" ]; then
    bad "재시작   ${crash}— 죽었다가 다시 뜬 컨테이너가 있다"
    fix "왜 죽었나: $SSH 'docker logs --tail 50 bandapp-app-1; dmesg | grep -i \"killed process\" | tail -5'"
else
    echo "재시작   0회"
fi

# 14. 서버 밖 백업(R2). 4번은 서버 안 파일만 본다 — 서버 디스크가 통째로 망가지면 그 백업도 같이 사라진다. 매일 백업이
#     암호화해 R2 db-backups/ 에 올린 것이 기준 시간 안에 있는지 본다(파일명이 UTC 시각). 업로드만 실패하면(R2 키 교체 등)
#     4번은 통과하고 여기서만 걸린다. 백업 스크립트와 같은 방법(amazon/aws-cli 컨테이너)으로 목록만 읽는다.
r2get() { grep -m1 "^$1=" .env.prod 2>/dev/null | cut -d= -f2- | sed "s/[[:space:]]*#.*$//; s/[[:space:]]*$//"; }
R2B=$(r2get R2_BUCKET); R2K=$(r2get R2_ACCESS_KEY_ID); R2S=$(r2get R2_SECRET_ACCESS_KEY)
R2E=$(r2get R2_ENDPOINT); [ -n "$R2E" ] || R2E="https://$(r2get R2_ACCOUNT_ID).r2.cloudflarestorage.com"
if [ -z "$R2B" ] || [ -z "$R2K" ]; then
    bad "R2백업   R2 설정이 없다 — 백업이 서버 안에만 있다"
    fix "백업 스크립트 설명: deploy/backup/pg-backup.sh · docs/DEPLOY.md §5"
else
    newest=$(docker run --rm -e AWS_ACCESS_KEY_ID="$R2K" -e AWS_SECRET_ACCESS_KEY="$R2S" -e AWS_DEFAULT_REGION=auto \
               amazon/aws-cli --endpoint-url "$R2E" s3 ls "s3://$R2B/db-backups/" 2>/dev/null \
             | awk '{print $4}' | grep '^bandapp-' | sort | tail -1)
    stamp=$(echo "$newest" | sed -n 's/^bandapp-\([0-9]\{8\}\)T\([0-9]\{2\}\)\([0-9]\{2\}\)\([0-9]\{2\}\)Z.*/\1 \2:\3:\4/p')
    if [ -z "$newest" ]; then
        bad "R2백업   목록을 못 읽었거나 하나도 없다 — R2 키가 바뀌었거나 업로드가 계속 실패 중"
        fix "백업 기록: $SSH 'tail -30 /var/log/bandapp-backup.log'"
        fix "지금 한 번 백업(R2 업로드 포함): $SSH 'cd /opt/bandapp && bandule backup'"
    elif [ -z "$stamp" ]; then
        bad "R2백업   마지막 파일 이름을 못 읽었다 ($newest)"
    else
        r2_age=$(( ($(date +%s) - $(date -u -d "$stamp" +%s)) / 3600 ))
        if [ "$r2_age" -gt "$BACKUP_MAX_H" ]; then
            bad "R2백업   마지막이 ${r2_age}시간 전 ($newest) — 서버 밖으로 백업이 안 나가고 있다"
            fix "백업 기록(업로드 실패 줄): $SSH 'tail -30 /var/log/bandapp-backup.log'"
            fix "지금 한 번 백업(R2 업로드 포함): $SSH 'cd /opt/bandapp && bandule backup'"
        else
            echo "R2백업   ${r2_age}시간 전 ($newest)"
        fi
    fi
fi

# 15. 서버 OS 재부팅 대기. Ubuntu 는 커널 같은 보안 업데이트를 받으면 재부팅해야 적용되고, 그때 이 표시를 남긴다.
#     받은 직후엔 알리지 않고 REBOOT_MAX_D 일 넘게 미뤄졌을 때만 건다 — 매일 울리면 아무도 안 본다. 컨테이너는
#     restart: unless-stopped 라 재부팅 뒤 저절로 다시 뜬다(1~2분 끊김).
if [ -f /var/run/reboot-required ]; then
    rb_days=$(( ($(date +%s) - $(date -r /var/run/reboot-required +%s)) / 86400 ))
    rb_pkgs=$(wc -l < /var/run/reboot-required.pkgs 2>/dev/null || echo "?")
    if [ "$rb_days" -ge "$REBOOT_MAX_D" ]; then
        bad "재부팅   보안 업데이트(${rb_pkgs}개)가 ${rb_days}일째 재부팅을 기다린다 — 재부팅해야 적용된다"
        fix "사용자가 적은 시간(새벽)에: $SSH 'reboot'   — 1~2분 뒤 앱·DB·Redis 가 저절로 다시 뜬다"
        fix "다시 떴는지: $SSH 'cd /opt/bandapp && bandule health'"
    else
        echo "재부팅   필요 (${rb_days}일째, ${REBOOT_MAX_D}일 넘으면 알림)"
    fi
else
    echo "재부팅   필요 없음"
fi

# 10. 돈은 들어왔는데 어느 밴드에도 못 붙인 구독(QA BILL-31, V24 unmatched_store_purchases). 만료 뒤 Play 스토어
#     「다시 구독」은 밴드 표시 없이 이전 구독 토큰만 따라오고, 서버가 그 토큰으로 원래 밴드를 찾는다. 못 찾으면 아무
#     밴드도 올리지 않고 이 테이블에 남긴다. **해결될 때까지 매일 걸린다** — 예전처럼 로그를 24시간만 세면 배포로
#     컨테이너가 바뀌거나 하루가 지나면 경보가 저절로 꺼졌다. 해결은 대부분 서버 배치(10분마다)가 한다: 밴드에 붙었거나,
#     Google 이 환불·만료했으면(확인 처리를 못 받은 구매는 3일 뒤 자동 환불) 닫는다. 운영자 명령은 tools/purchases.py.
#     `→` 로 시작하는 줄은 매일 점검 워크플로가 이슈(=메일) 본문에 그대로 옮긴다 — 번호·건수만 있고 토큰은 없다.
DB_USER=${DB_USERNAME:-$(grep -m1 '^DB_USERNAME=' .env.prod 2>/dev/null | cut -d= -f2- | tr -cd 'A-Za-z0-9_')}
DB_NAME_=${DB_NAME:-$(grep -m1 '^DB_NAME=' .env.prod 2>/dev/null | cut -d= -f2- | tr -cd 'A-Za-z0-9_')}
unmatched=$(docker exec bandapp-postgres-1 psql -U "${DB_USER:-bandapp}" -d "${DB_NAME_:-bandapp}" -tAc     "select count(*) || ' ' || coalesce(string_agg(id::text, ',' order by id), '') || ' ' ||
            coalesce(extract(day from now() - min(received_at))::int, 0)
     from unmatched_store_purchases where resolved_at is null" 2>/dev/null)
if [ -z "$unmatched" ]; then
    bad "밴드없는결제 확인 실패 — DB 를 읽지 못했다 (postgres 컨테이너·V24 마이그레이션을 본다)"
    fix "DB 컨테이너·마이그레이션: $SSH 'cd /opt/bandapp && docker compose -f docker-compose.prod.yml --env-file .env.prod ps postgres; docker logs bandapp-app-1 2>&1 | grep -iE \"flyway|migrat\" | tail -5'"
else
    set -- $unmatched
    if [ "$1" -gt 0 ]; then
        bad "밴드없는결제 미해결 $1건 (번호 $2, 가장 오래된 것 $3일 전) — 돈은 들어왔는데 PREMIUM 을 줄 밴드를 못 찾았다"
        echo "   → 내 PC 저장소 폴더에서 보기: python tools/purchases.py list"
        echo "   → 결제한 밴드를 알면 그 밴드에 반영(10분 안): python tools/purchases.py link <번호> <밴드ID>"
        echo "   → 모르면 그대로 둔다: 결제 3일 뒤 Google 이 자동 환불하고 이 경보는 저절로 꺼진다"
    else
        echo "밴드없는결제 0건"
    fi
fi

[ "$fail" = 0 ] && echo "== 이상 없음"
[ "$fail" = 0 ] || fix "잘 모르겠으면 이 메일 내용을 그대로 Claude 에게 붙여넣는다"
exit $fail
