#!/usr/bin/env sh
# PostgreSQL 덤프 → 로컬 보관 → Cloudflare R2 업로드. 최근 7일치만 남긴다.
# 단일 VM 구성이라 이 백업이 사용자 데이터를 지키는 유일한 수단이다.
#
# 크론 등록 (매일 03:30 KST):
#   30 3 * * * cd /opt/bandapp && sh deploy/backup/pg-backup.sh >> /var/log/bandapp-backup.log 2>&1
#
# 복구 절차는 docs/DEPLOY.md §5. 복구는 deploy/backup/pg-restore.sh.
set -eu

cd "$(dirname "$0")/../.."
ENV_FILE="${ENV_FILE:-.env.prod}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
KEEP="${BACKUP_KEEP:-7}"

# shellcheck disable=SC1090
[ -f "$ENV_FILE" ] && . "./$ENV_FILE"
: "${DB_NAME:?}" "${DB_USERNAME:?}"

COMPOSE="docker compose -f $COMPOSE_FILE --env-file $ENV_FILE"
mkdir -p "$BACKUP_DIR"

STAMP=$(date -u +%Y%m%dT%H%M%SZ)
FILE="$BACKUP_DIR/bandapp-$STAMP.dump"

echo "== pg_dump → $FILE"
# -Fc(커스텀 포맷): 자체 압축되고, 복구 시 테이블 단위 선택·병렬 복구가 된다.
$COMPOSE exec -T postgres pg_dump -U "$DB_USERNAME" -d "$DB_NAME" -Fc > "$FILE"

# 덤프가 실제로 읽히는지 그 자리에서 확인한다. "백업은 도는데 복구가 안 되는" 사고의 대부분은
# 여기서 걸린다(빈 파일, 에러 메시지가 stdout 에 섞여 들어간 파일).
if ! $COMPOSE exec -T postgres pg_restore --list < "$FILE" > /dev/null 2>&1; then
    echo "!! 덤프가 유효하지 않다: $FILE"
    rm -f "$FILE"
    exit 1
fi
echo "== 검증 통과 ($(du -h "$FILE" | cut -f1))"

if [ -n "${R2_BUCKET:-}" ] && [ -n "${R2_ACCESS_KEY_ID:-}" ]; then
    ENDPOINT="${R2_ENDPOINT:-https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com}"
    aws_r2() {
        docker run --rm \
            -e AWS_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID" \
            -e AWS_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY" \
            -e AWS_DEFAULT_REGION=auto \
            -v "$(cd "$BACKUP_DIR" && pwd):/backup" \
            amazon/aws-cli --endpoint-url "$ENDPOINT" "$@"
    }
    echo "== R2 업로드 s3://$R2_BUCKET/db-backups/"
    aws_r2 s3 cp "/backup/$(basename "$FILE")" "s3://$R2_BUCKET/db-backups/"

    # 원격도 최근 $KEEP 개만 남긴다. 파일명이 UTC 타임스탬프라 사전순 = 시간순이다.
    aws_r2 s3 ls "s3://$R2_BUCKET/db-backups/" | awk '{print $4}' | grep '^bandapp-' | sort \
        | head -n "-$KEEP" | while read -r old; do
            echo "-- 원격 삭제 $old"
            aws_r2 s3 rm "s3://$R2_BUCKET/db-backups/$old"
        done
else
    echo "== R2 미설정 — 로컬에만 보관한다"
fi

# 로컬도 최근 $KEEP 개만.
ls -1 "$BACKUP_DIR"/bandapp-*.dump 2>/dev/null | sort | head -n "-$KEEP" | while read -r old; do
    echo "-- 로컬 삭제 $old"
    rm -f "$old"
done

echo "== 백업 완료"
