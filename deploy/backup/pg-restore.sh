#!/usr/bin/env sh
# 덤프 파일에서 DB 를 복구한다. 기존 스키마를 통째로 버리고 덤프 상태로 되돌린다.
#
#   sh deploy/backup/pg-restore.sh backups/bandapp-20260906T033000Z.dump
#
# 백업이 살아 있는지만 확인하는 훈련(운영 DB 를 건드리지 않는다) — 빈 DB 를 하나 만들어 거기로:
#   RESTORE_DB=restore_drill sh deploy/backup/pg-restore.sh <덤프파일>
#
# R2 에 있는 것을 쓰려면 먼저 내려받는다 (docs/DEPLOY.md §5).
set -eu

cd "$(dirname "$0")/../.."
ENV_FILE="${ENV_FILE:-.env.prod}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"

DUMP="${1:?사용법: sh deploy/backup/pg-restore.sh <덤프파일>}"
[ -f "$DUMP" ] || { echo "!! 파일이 없다: $DUMP"; exit 1; }

# shellcheck disable=SC1090
[ -f "$ENV_FILE" ] && . "./$ENV_FILE"
: "${DB_NAME:?}" "${DB_USERNAME:?}"

COMPOSE="docker compose -f $COMPOSE_FILE --env-file $ENV_FILE"

# RESTORE_DB 를 주면 그 DB 로 복구한다(없으면 만든다). 운영 DB 는 그대로 두고 덤프만 검증하는 용도.
TARGET_DB="${RESTORE_DB:-$DB_NAME}"
IS_LIVE=$([ "$TARGET_DB" = "$DB_NAME" ] && echo 1 || echo 0)

if [ "${ASSUME_YES:-0}" != "1" ]; then
    printf "%s 의 데이터를 버리고 %s 로 덮어쓴다. 계속하려면 'yes' 입력: " "$TARGET_DB" "$DUMP"
    read -r answer
    [ "$answer" = "yes" ] || { echo "취소했다"; exit 1; }
fi

if [ "$IS_LIVE" = 1 ]; then
    # 커넥션이 붙어 있으면 스키마를 드롭할 수 없다.
    echo "== 앱 정지 (DB 커넥션을 끊는다)"
    $COMPOSE stop app 2>/dev/null || true
else
    echo "== 훈련 모드 — $TARGET_DB 로 복구한다 (운영 DB $DB_NAME 은 건드리지 않는다)"
    $COMPOSE exec -T postgres psql -U "$DB_USERNAME" -d postgres -tAc         "SELECT 1 FROM pg_database WHERE datname='$TARGET_DB'" | grep -q 1         || $COMPOSE exec -T postgres createdb -U "$DB_USERNAME" "$TARGET_DB"
fi

echo "== 스키마 초기화 ($TARGET_DB)"
$COMPOSE exec -T postgres psql -U "$DB_USERNAME" -d "$TARGET_DB" -v ON_ERROR_STOP=1     -c 'DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;'

echo "== 복구"
# --no-owner: 덤프를 뜬 서버와 롤 이름이 달라도 복구되게 한다(다른 VM 으로 옮길 때).
$COMPOSE exec -T postgres pg_restore -U "$DB_USERNAME" -d "$TARGET_DB" --no-owner --exit-on-error < "$DUMP"

echo "== 확인: 마이그레이션 버전과 주요 테이블 행 수"
$COMPOSE exec -T postgres psql -U "$DB_USERNAME" -d "$TARGET_DB" -tAc     "SELECT 'flyway=' || version || ' ' || description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
     SELECT 'users=' || count(*) FROM users;
     SELECT 'bands=' || count(*) FROM bands;
     SELECT 'reservations=' || count(*) FROM reservations;
     SELECT 'board_posts=' || count(*) FROM board_posts;"

if [ "$IS_LIVE" = 1 ]; then
    echo "== 앱 기동"
    $COMPOSE up -d app
    echo "== 복구 완료. 앱이 healthy 가 되는지 확인한다: $COMPOSE ps"
else
    echo "== 훈련 완료. 위 행 수가 백업 시점과 같으면 성공이다."
    echo "   정리: $COMPOSE exec -T postgres dropdb -U $DB_USERNAME $TARGET_DB"
fi
