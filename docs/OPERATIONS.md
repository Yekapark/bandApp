# 운영 안내서 — DB 접속과 자주 쓰는 쿼리

> 운영자 화면이 아직 없어서 통계·쿠폰 발급·신고 확인은 DB 를 직접 본다.
> 신고 조치(글 숨김·계정 이용 정지)는 SQL 을 손으로 치지 않고 **`tools/moderate.py`** 로 한다 — [5장](#5-신고-확인과-조치--글-숨김이용-정지).
> 여기 있는 것을 복사해 붙이면 된다. 스키마 뜻은 **DB 도구가 컬럼 옆에 보여주는 설명**을
> 보면 되고(V16 마이그레이션이 채웠다), 겪은 문제는 [TROUBLESHOOTING.md](TROUBLESHOOTING.md) 에 있다.

---

## 1. DB 접속

### 1-A. 개발 DB (내 PC)

`docker compose up` 으로 띄운 것. 포트가 그대로 열려 있어 바로 붙는다.

| | |
|---|---|
| 호스트 | `127.0.0.1` |
| 포트 | `5432` |
| 사용자 | `bandapp` (`.env` 의 `DB_USERNAME`) |
| 비밀번호 | `.env` 의 `DB_PASSWORD` |
| DB 이름 | `bandapp` |

**HeidiSQL** — `세션 관리자 > 신규` 에서

1. **네트워크 유형**: `PostgreSQL (TCP/IP)`
2. 호스트 `127.0.0.1`, 사용자 `bandapp`, 비밀번호는 `.env` 값, 포트 `5432`
3. **데이터베이스** 칸에 `bandapp` 을 적는다 — 비워 두면 HeidiSQL 이 모든 DB 를 훑다가
   권한 오류로 멈추는 일이 있다
4. `열기`

터미널로 하려면:

```bash
docker compose exec postgres psql -U bandapp -d bandapp
```

### 1-B. 운영 DB (VM) — SSH 터널로만

운영 Postgres 는 **밖으로 열려 있지 않다.** VM 의 루프백(`127.0.0.1:5432`)에만 붙어 있어서
SSH 로 들어간 사람만 닿는다. 방화벽도 80·443·SSH 만 연다.

> ⚠️ 이 루프백 바인딩은 `docker-compose.prod.yml` 에 있다. **다음 배포 때 적용된다**
> (`docker compose up -d` 가 postgres 컨테이너를 다시 만든다 — 이때 DB 가 몇 초 끊긴다).
> 그 전까지는 아래 1-C 방법을 쓴다.

**HeidiSQL** — `세션 관리자 > 신규` 에서

1. **네트워크 유형**: `PostgreSQL (SSH 터널)`
2. **설정** 탭 — 호스트 `127.0.0.1`, 포트 `5432`, 사용자 `bandapp`,
   비밀번호는 VM 의 `/opt/bandapp/.env.prod` 에 있는 `DB_PASSWORD`, 데이터베이스 `bandapp`
   - 여기의 호스트는 **터널 반대편에서 본 주소**라 `127.0.0.1` 이 맞다
3. **SSH 터널** 탭 — `plink.exe` 경로(HeidiSQL 이 함께 깔아 준다), SSH 호스트에 VM 주소,
   SSH 사용자, 개인키 파일(`.ppk` 로 변환된 것), 로컬 포트는 비워 두면 알아서 잡는다
   - 개인키가 OpenSSH 형식이면 PuTTYgen 으로 `.ppk` 로 바꿔야 plink 가 읽는다
4. `열기`

**터미널이 편하면** 터널을 직접 열어도 된다. 이러면 HeidiSQL 은 1-A 와 똑같이 설정하고
포트만 `15432` 로 하면 된다.

```bash
ssh -L 15432:127.0.0.1:5432 <SSH사용자>@<VM주소>
```

### 1-C. 배포 전이라 포트가 아직 안 열렸을 때

VM 에 SSH 로 들어가 컨테이너 안에서 바로 쓴다.

```bash
cd /opt/bandapp && docker compose -f docker-compose.prod.yml --env-file .env.prod exec postgres psql -U bandapp -d bandapp
```

> **운영 DB 에서 `UPDATE`·`DELETE` 를 할 때는 먼저 `SELECT` 로 몇 행이 걸리는지 본다.**
> `WHERE` 를 빠뜨린 `UPDATE` 는 되돌릴 수 없다. 백업은 하루 한 번이라 그 사이 것은 사라진다.

---

## 2. 통계

### 한눈에 보기

```sql
SELECT
  (SELECT count(*) FROM users WHERE deleted_at IS NULL)                    AS 사용자,
  (SELECT count(*) FROM bands)                                             AS 밴드,
  (SELECT count(*) FROM band_members WHERE left_at IS NULL)                AS 소속수,
  (SELECT count(*) FROM reservations)                                      AS 일정,
  (SELECT count(*) FROM board_posts WHERE deleted_at IS NULL)              AS 게시글,
  (SELECT count(*) FROM media_attachments WHERE status = 'READY')          AS 첨부,
  (SELECT count(*) FROM band_plans WHERE tier = 'PREMIUM')                 AS 프리미엄밴드,
  (SELECT count(*) FROM reports WHERE status = 'OPEN')                     AS 미처리신고;
```

### 밴드별 현황 (인원·일정·게시글·첨부·용량)

**용량은 볼 수 있다** — `media_attachments.size_bytes` 에 크기가 들어 있고, 업로드를 마칠 때
R2 의 실제 크기와 대조해 다르면 거부하므로 저장된 값이 곧 실제 값이다.

세는 대상마다 `LEFT JOIN LATERAL` 로 따로 센다. 여러 테이블을 한 번에 조인하면 행이 곱해져
(멤버 4명 × 일정 4건 = 16행) 합계가 부풀어 오른다.

```sql
SELECT
  b.id,
  b.name                                                          AS 밴드,
  COALESCE(p.tier, '-')                                           AS 요금제,
  (SELECT count(*) FROM band_members m
     WHERE m.band_id = b.id AND m.left_at IS NULL)                AS 인원,
  (SELECT count(*) FROM reservations r WHERE r.band_id = b.id)    AS 일정,
  (SELECT count(*) FROM board_posts po
     WHERE po.band_id = b.id AND po.deleted_at IS NULL)           AS 게시글,
  me.images                                                       AS 사진,
  me.videos                                                       AS 영상,
  pg_size_pretty(me.bytes)                                        AS 용량
FROM bands b
  LEFT JOIN band_plans p ON p.band_id = b.id
  LEFT JOIN LATERAL (
    SELECT count(*) FILTER (WHERE m.type = 'IMAGE')  AS images,
           count(*) FILTER (WHERE m.type = 'VIDEO')  AS videos,
           COALESCE(sum(m.size_bytes), 0)            AS bytes
    FROM media_attachments m
      JOIN board_posts po2 ON po2.id = m.board_post_id
    WHERE po2.band_id = b.id AND m.status = 'READY'
  ) me ON true
ORDER BY b.id;
```

### 정확한 용량 (밴드별)

```sql
SELECT b.id, b.name AS 밴드,
       count(*) FILTER (WHERE me.type = 'IMAGE')                AS 사진,
       count(*) FILTER (WHERE me.type = 'VIDEO')                AS 영상,
       pg_size_pretty(sum(me.size_bytes))                       AS 용량,
       pg_size_pretty(COALESCE(sum(me.size_bytes) FILTER (WHERE me.type = 'VIDEO'), 0)) AS 영상용량
FROM media_attachments me
  JOIN board_posts po ON po.id = me.board_post_id
  JOIN bands b        ON b.id = po.band_id
WHERE me.status = 'READY'
GROUP BY b.id, b.name
ORDER BY sum(me.size_bytes) DESC;
```

### 전체 저장 용량과 상태별 분포

```sql
SELECT status, type, count(*) AS 개수, pg_size_pretty(sum(size_bytes)) AS 용량
FROM media_attachments
GROUP BY ROLLUP (status, type)
ORDER BY status NULLS LAST, type NULLS LAST;
```

`PENDING` 이 오래 쌓여 있으면 업로드하다 만 것이다(청소 배치가 지운다).
`EXPIRED` 는 보관기한이 지나 못 보는 것이고, R2 객체는 별도 배치가 지운다.

### 최근 활동 (요일별 가입·일정)

```sql
SELECT date_trunc('day', created_at AT TIME ZONE 'Asia/Seoul')::date AS 날짜,
       count(*) AS 가입
FROM users WHERE created_at > now() - interval '30 days'
GROUP BY 1 ORDER BY 1 DESC;
```

```sql
SELECT date_trunc('week', start_at AT TIME ZONE 'Asia/Seoul')::date AS 주,
       count(*) AS 합주수, count(DISTINCT band_id) AS 활동밴드
FROM reservations
WHERE start_at > now() - interval '90 days'
GROUP BY 1 ORDER BY 1 DESC;
```

### 안 쓰는 밴드 찾기

```sql
SELECT b.id, b.name, b.created_at::date AS 만든날,
       max(r.created_at)::date AS 마지막일정등록
FROM bands b LEFT JOIN reservations r ON r.band_id = b.id
GROUP BY b.id, b.name, b.created_at
HAVING max(r.created_at) IS NULL OR max(r.created_at) < now() - interval '60 days'
ORDER BY b.created_at;
```

---

## 3. 쿠폰 (프리미엄 발급)

발급 화면이 없다. 코드를 직접 넣는다. 사용자는 앱의 `설정 > 요금제 > 쿠폰 코드 입력` 에서 쓴다.

### 만들기

```sql
-- 1년(365일)짜리, 1회용, 30일 뒤 만료
INSERT INTO plan_coupons (code, grant_days, max_uses, expires_at, revoked, created_at)
VALUES ('BAND2026', 365, 1, now() + interval '30 days', false, now());
```

| 칸 | 뜻 |
|---|---|
| `code` | 사용자가 입력할 8자 이내 코드. **대문자로 넣는다** (서버가 대문자로 맞춰 찾는다) |
| `grant_days` | 프리미엄을 며칠 줄지. 이미 프리미엄이면 그만큼 **연장**된다 |
| `max_uses` | 몇 밴드까지 쓸 수 있는지. `NULL` 이면 무제한 |
| `expires_at` | 이 시각 뒤로는 못 쓴다. `NULL` 이면 기한 없음 |
| `revoked` | `true` 로 바꾸면 즉시 못 쓰게 된다 |

여러 개를 한 번에:

```sql
INSERT INTO plan_coupons (code, grant_days, max_uses, expires_at, revoked, created_at)
VALUES ('TESTA001', 365, 1, now() + interval '90 days', false, now()),
       ('TESTA002', 365, 1, now() + interval '90 days', false, now()),
       ('TESTA003', 365, 1, now() + interval '90 days', false, now());
```

### 현황

```sql
SELECT c.id, c.code, c.grant_days AS 일수, c.used_count || '/' || COALESCE(c.max_uses::text, '무제한') AS 사용,
       c.expires_at::date AS 만료, c.revoked AS 끊김,
       string_agg(b.name, ', ') AS 쓴밴드
FROM plan_coupons c
  LEFT JOIN plan_coupon_redemptions r ON r.coupon_id = c.id
  LEFT JOIN bands b ON b.id = r.band_id
GROUP BY c.id ORDER BY c.id DESC;
```

### 끊기

```sql
UPDATE plan_coupons SET revoked = true WHERE code = 'BAND2026';
```

---

## 4. 요금제 직접 바꾸기

> **되도록 쿠폰이나 앱 화면을 쓴다.** SQL 로 티어만 바꾸면 **첨부 만료일이 재계산되지 않는다**
> — API 는 프리미엄으로 올릴 때 기존 사진·영상의 만료일을 지우고, 무료로 내릴 때 30일 유예를
> 주는데 그 처리가 빠진다.

`band_plans` 에는 불변식 세 개가 걸려 있어서 티어만 바꾸면 제약 위반이 난다.

```sql
-- 프리미엄으로 (1년)
UPDATE band_plans
SET tier = 'PREMIUM', media_retention_days = NULL, subscription_ref = 'manual-' || band_id,
    started_at = now(), expires_at = now() + interval '1 year', updated_at = now()
WHERE band_id = 4;

-- 무료로
UPDATE band_plans
SET tier = 'FREE', media_retention_days = 30, subscription_ref = NULL,
    started_at = now(), expires_at = NULL, updated_at = now()
WHERE band_id = 4;
```

| 제약 | 뜻 |
|---|---|
| `ck_band_plans_tier` | `FREE` 또는 `PREMIUM` 만 |
| `ck_band_plans_retention` | FREE 면 `media_retention_days > 0`, PREMIUM 이면 `NULL` |
| `ck_band_plans_free_no_expiry` | FREE 면 `expires_at` 이 `NULL` 이어야 한다 |

### 요금제 현황

```sql
SELECT b.id, b.name AS 밴드, p.tier AS 요금제,
       p.expires_at::date AS 만료일,
       (p.tier = 'PREMIUM' AND p.subscription_ref IS NULL) AS 해지예약됨,
       p.subscription_ref AS 구독식별자
FROM bands b JOIN band_plans p ON p.band_id = b.id
ORDER BY p.tier DESC, b.id;
```

`해지예약됨` 이 `true` 면 **해지를 눌렀지만 결제 기간이 남은 상태**다. 만료일이 지나면
야간 배치가 무료로 내린다.

---

## 5. 신고 확인과 조치 — 글 숨김·이용 정지

접수되면 `REPORT_NOTIFY_EMAILS` 주소로 **메일**이 가고, `REPORT_NOTIFY_USER_IDS` 계정으로
**푸시**가 간다. 메일 본문에 이 신고에 맞는 조회 쿼리가 함께 들어 있다.

```sql
-- 미처리 신고
SELECT r.id, r.target_type AS 종류, r.target_id AS 대상, u.name AS 신고자,
       r.reason AS 사유, r.created_at
FROM reports r JOIN users u ON u.id = r.reporter_id
WHERE r.status = 'OPEN' ORDER BY r.created_at;

-- 처리 완료로
UPDATE reports SET status = 'RESOLVED' WHERE id = 1;
```

### 조치 명령 — `tools/moderate.py`

내 PC(저장소 폴더)에서 돌린다. 배포 키 `~/.ssh/bandule_deploy` 로 서버에 들어가 DB·Redis 에 쓴다.
**모든 명령은 바뀔 대상을 먼저 보여 주고 `yes` 를 쳐야 실행한다.** 무엇을 할지 고르는 기준·본인 고지 메일
견본은 [MODERATION.md](MODERATION.md).

```bash
# 보기 (바꾸지 않는다)
python tools/moderate.py reports                 # 처리 안 된 신고 목록
python tools/moderate.py post 123                # 글 123 내용과 첨부

# 글 숨김 — 앱의 글 삭제와 같다. 첨부는 04:15 배치가 저장소에서 지운다. 관련 신고는 처리 완료로.
python tools/moderate.py hide-post 123
python tools/moderate.py hide-media 77           # 첨부 77 이 달린 글을 통째로 숨김

# 이용 정지 — 로그인 차단 + 쓰던 로그인 즉시 끊김 + 푸시 끊김. 기간이 지나면 저절로 풀린다.
python tools/moderate.py suspend 45 --days 7 --reason "비방 게시(신고 12)"
python tools/moderate.py suspend 45 --forever --reason "불법촬영물(신고 13)" --hide-posts   # 글까지 전부 숨김

# 정지 해제 (이의 인정 등) — 사유 기록은 남는다
python tools/moderate.py unsuspend 45

# 조치 없이 신고만 닫기
python tools/moderate.py resolve 12
```

> **약관 제14조 제5~8항(정지 근거)은 2026-10-06 시행이다. 그 전에는 `suspend` 를 쓰지 않는다.**
> 정지한 뒤에는 스크립트가 마지막에 보여 주는 가입 이메일로 **사유·기간·이의 방법을 보낸다**(약관 제14조 제7항).
> `users.deleted_at` 을 손으로 채워 "정지" 하지 않는다 — 탈퇴로 처리돼 90일 뒤 개인정보가 파기된다.

```sql
-- 지금 정지 중인 계정
SELECT id, name, email, suspended_until, suspension_reason
FROM users WHERE suspended_until > now() ORDER BY suspended_until;

-- 정지 이력(풀린 것 포함)
SELECT id, name, suspended_until, suspension_reason
FROM users WHERE suspension_reason IS NOT NULL ORDER BY suspended_until DESC;
```

**정지된 사람 앱에 보이는 것** — 로그인 화면에 "이용이 정지된 계정이에요 (M월 D일까지). 이의가 있으면
notice@bandule.com 으로 알려 주세요." 사유는 보이지 않는다(`suspension_reason` 은 운영 기록).

**`ssh` 가 실패할 때** — 배포 키 경로가 다르면 `BANDULE_SSH_KEY=경로 python tools/moderate.py ...`.
키가 없는 PC 면 [NEW_PC_SETUP.md](NEW_PC_SETUP.md).

**신고가 안 들어온 것 같을 때** — 자기 글·자기 사진은 신고할 수 없다(앱에서 메뉴 자체가 안 뜬다).
같은 대상을 같은 사람이 두 번 신고하면 두 번째는 409 로 막힌다.

---

## 6. 푸시가 안 갈 때

```sql
-- 이 사람에게 보낼 주소가 있는가. 행이 없으면 푸시는 아예 안 나간다.
SELECT * FROM device_tokens WHERE user_id = 4;

-- 앱 안의 푸시 스위치를 껐는가. 행이 없으면 기본 켬이다.
SELECT * FROM notification_settings WHERE user_id = 4;

-- 실제로 보낸 이력
SELECT id, type, target_id, band_id, title, created_at
FROM notification_dispatches WHERE user_id = 4 ORDER BY id DESC LIMIT 20;
```

`band_id` 가 `NULL` 인 이력은 **앱의 알림 목록에 안 뜬다**(목록을 밴드별로 조회한다).
신고 접수 알림이 여기 해당한다 — 푸시는 가지만 목록에는 안 남는다.

---

## 7. 백업에서 되돌리기 — 새 PC(포맷 뒤)에서도 따라 하는 복구 안내

> 이 절만 보고 **아무것도 깔려 있지 않은 Windows PC** 에서 백업을 열고 복원할 수 있게 썼다.
> 서버 쪽 스크립트 설명은 [DEPLOY.md §4·§5](DEPLOY.md) 에 있다. 마지막 실제 훈련: **2026-10-05**(§7-8).

### 7-0. 백업이 어디에 몇 개 있나

| 어디 | 무엇 | 잠김 | 개수 | 언제 |
|---|---|---|---|---|
| 서버 `/opt/bandapp/backups/` | `bandapp-<UTC시각>.dump` | **안 잠김**(서버가 털리면 DB 도 털리므로 잠가 봐야 소용없다) | 최근 7개 | 매일 03:30 KST |
| R2 `s3://bandule-prod/db-backups/` | `bandapp-<UTC시각>.dump.gpg` | **공개키로 잠김** — 개인키가 있어야 열린다 | 최근 7개 | 매일 03:30 KST |
| 서버 `backups/predeploy/`, R2 `db-backups/predeploy/` | 배포 직전 덤프 | 위와 같음 | 각 7개 | 서버 코드가 바뀐 배포 때만 |

- **서버가 살아 있으면** 서버의 안 잠긴 덤프로 복원한다(§7-6-A). 개인키가 필요 없다.
- **서버가 날아갔으면** R2 의 잠긴 사본을 PC 로 받아 개인키로 연다(§7-2~§7-4) → 새 서버에 올린다(§7-6-B).
- 덤프 하나 = **회원정보 전체**(이메일·이름·비밀번호 해시·밴드·일정·정산). 다룰 때 §7-7 을 지킨다.
- 파일 이름의 시각은 **UTC** 다. `20261003T183001Z` = 2026-10-04 03:30 KST.

### 7-1. 새 PC 에 필요한 것

| 필요한 것 | 어디서 | 확인 |
|---|---|---|
| Git for Windows (Git Bash, **gpg 2.4 포함**) | git-scm.com | Git Bash 에서 `gpg --version` |
| Docker Desktop | docker.com — 설치 뒤 한 번 실행해 둔다 | `docker info` 가 오류 없이 나온다 |
| 이 저장소 | `git clone https://github.com/Yekapark/bandApp.git` | — |
| **백업 개인키 파일 + 그 암호** | 사용자가 따로 보관한 곳(2곳 이상 — QA OPS-16) | 지문 `B4A794DF793BE679250199A84218121C3F08D54F` |
| R2 접근 값 4개 (`R2_ACCOUNT_ID` `R2_ACCESS_KEY_ID` `R2_SECRET_ACCESS_KEY` `R2_BUCKET`) | 서버 `/opt/bandapp/.env.prod`, 또는 Cloudflare 대시보드 › R2 › API 토큰에서 새로 발급 | 저장소에는 **없다**(git 미추적) |
| (서버에 올릴 때) 배포 SSH 키 `~/.ssh/bandule_deploy` | 사용자 보관. 잃었으면 서버 업체 콘솔에서 새 공개키를 등록 | `ssh -i ~/.ssh/bandule_deploy root@<서버> 'bandule health'` |

> PC 를 통째로 옮기는 경우의 전체 목록(앱 서명 키·계정·결제 포함)은 **§9**.
>
> **개인키를 잃으면 R2 사본은 아무도 못 연다.** 저장소의 공개키(`deploy/backup/backup-pubkey.asc`)는 잠그기만 한다.
> 개인키를 처음 만든 PC 에서 내보내 두는 법: `gpg --export-secret-keys --armor B4A794DF793BE679250199A84218121C3F08D54F > bandule-backup-secret.asc`
> (이 파일과 암호를 **서로 다른 곳**에 둔다 — 예: 파일은 USB, 암호는 비밀번호 관리자).

### 7-2. 개인키 가져오기 (새 PC 에서 한 번)

```bash
gpg --import bandule-backup-secret.asc            # 보관해 둔 개인키 파일. 암호를 묻는다
gpg --list-secret-keys --keyid-format long        # 아래가 보이면 된다
#   sec   rsa4096/4218121C3F08D54F
#         B4A794DF793BE679250199A84218121C3F08D54F
#   uid   bandule-backup <notice@bandule.com>
```

신뢰도(trust) 설정은 필요 없다 — 여는 데는 개인키만 있으면 된다.

### 7-3. R2 에서 잠긴 백업 받기 (PC 로 바로)

작업 폴더는 **저장소 밖**에 둔다(예: `~/restore`). R2 키는 화면·셸 기록에 남지 않게 `read` 로 넣는다.

```bash
mkdir -p ~/restore && cd ~/restore
read -p  'R2_ACCOUNT_ID: '        R2_ACCOUNT_ID
read -p  'R2_BUCKET: '            R2_BUCKET             # 운영은 bandule-prod
read -p  'R2_ACCESS_KEY_ID: '     AWS_ACCESS_KEY_ID
read -sp 'R2_SECRET_ACCESS_KEY: ' AWS_SECRET_ACCESS_KEY; echo
export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY

# Git Bash 는 /backup 같은 경로를 C:/... 로 바꿔 버린다 — MSYS_NO_PATHCONV=1 로 막는다.
r2() {
  MSYS_NO_PATHCONV=1 docker run --rm -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION=auto \
    -v "$(pwd -W 2>/dev/null || pwd):/backup" amazon/aws-cli \
    --endpoint-url "https://$R2_ACCOUNT_ID.r2.cloudflarestorage.com" "$@"
}
r2 s3 ls "s3://$R2_BUCKET/db-backups/"                                  # 목록 — 맨 아래가 최신
r2 s3 cp "s3://$R2_BUCKET/db-backups/bandapp-<UTC시각>.dump.gpg" /backup/
```

- 목록에 `.gpg` 가 아닌 `.dump` 가 보이면 암호화 도입(2026-10-02) 전의 **평문 사본**이다. 7개 보관 규칙으로 저절로 밀려나지만,
  당장 지우려면 `r2 s3 rm "s3://$R2_BUCKET/db-backups/<그 파일>"`(되돌릴 수 없다).
- 서버가 살아 있고 R2 키를 PC 에 넣기 싫으면 서버에서 받아 `scp` 로 가져와도 된다([DEPLOY.md §5-2](DEPLOY.md)).

### 7-4. 열기 (복호화)

```bash
gpg --output bandapp-<UTC시각>.dump --decrypt bandapp-<UTC시각>.dump.gpg     # 개인키 암호를 묻는다
head -c 5 bandapp-<UTC시각>.dump; echo                                         # PGDMP 로 시작하면 정상
```

### 7-5. 복원 훈련 — PC 의 임시 DB 로 (운영 서버를 건드리지 않는다)

분기마다, 그리고 개인키·PC 를 바꾼 뒤에 한 번씩 한다. 운영과 같은 **Postgres 16** 을 쓴다.

```bash
DUMP=bandapp-<UTC시각>.dump
docker run -d --name bandule-restore-drill -e POSTGRES_USER=bandapp -e POSTGRES_PASSWORD=drill \
  -e POSTGRES_DB=restore_drill postgres:16-alpine
until docker exec bandule-restore-drill pg_isready -U bandapp -d restore_drill >/dev/null 2>&1; do sleep 1; done

docker exec -i bandule-restore-drill pg_restore --list < "$DUMP" | grep -c "TABLE DATA"    # 0 이 아니면 읽힌다
docker exec -i bandule-restore-drill pg_restore -U bandapp -d restore_drill --no-owner --exit-on-error < "$DUMP"
docker exec bandule-restore-drill psql -U bandapp -d restore_drill -tAc "select
  'users='||(select count(*) from users)||' bands='||(select count(*) from bands)
  ||' reservations='||(select count(*) from reservations)||' settlements='||(select count(*) from settlements)
  ||' flyway='||(select max(installed_rank) from flyway_schema_history where success);"
```

**운영과 맞는지 대조** — 백업 시각(파일 이름의 UTC 시각) 뒤에 생긴 행을 운영에서 세어 더하면 같아야 한다. 운영은 **읽기만** 한다.
서버에 접속해 [§1-B](#1-b-운영-db-vm--ssh-터널로만) 의 psql 로 들어가서:

```sql
-- '2026-10-03T18:30:01Z' 자리에 백업 파일 이름의 시각을 넣는다
select (select count(*) from users) users,  (select count(*) from users where created_at > '2026-10-03T18:30:01Z') users_after,
       (select count(*) from bands) bands,  (select count(*) from bands where created_at > '2026-10-03T18:30:01Z') bands_after,
       (select max(installed_rank) from flyway_schema_history where success) flyway;
```

복원본 건수 + `*_after` = 운영 건수, `flyway` 같음 → 통과. (그 사이 삭제가 있었으면 그만큼 어긋난다 — 원인을 적어 둔다.)

끝나면 **바로 지운다** — 컨테이너와 그 데이터, 열린 덤프, 받은 `.gpg`:

```bash
docker rm -f -v bandule-restore-drill      # -v: 이 컨테이너의 데이터 볼륨까지. docker volume prune 은 쓰지 않는다(다른 볼륨도 지운다)
rm -f bandapp-*.dump bandapp-*.dump.gpg
```

### 7-6. 실제 복구

**A. 서버는 살아 있고 데이터만 깨졌을 때** — 서버의 안 잠긴 덤프로. 복원하는 동안 앱이 멈춘다.

```bash
ssh -i ~/.ssh/bandule_deploy root@<서버>
cd /opt/bandapp && ls -1 backups/bandapp-*.dump | tail -3                              # 되돌릴 시점 고르기
RESTORE_DB=restore_drill sh deploy/backup/pg-restore.sh backups/bandapp-<시각>.dump    # 먼저 훈련 DB 로 확인('yes')
docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T postgres dropdb -U bandapp restore_drill
sh deploy/backup/pg-restore.sh backups/bandapp-<시각>.dump                             # 운영 DB 를 덮어쓴다('yes')
```

`pg-restore.sh` 가 앱 정지 → 스키마 비우기 → 복원 → 행 수 출력 → 앱 재기동까지 한다.

**B. 서버가 통째로 날아갔을 때** — 새 VM 을 [DEPLOY.md §1·§2](DEPLOY.md) 대로 올린 뒤(앱이 뜨면 빈 스키마가 생긴다),
§7-3·§7-4 로 PC 에서 연 덤프를 올려 복원한다.

```bash
scp -i ~/.ssh/bandule_deploy bandapp-<시각>.dump root@<새 서버>:/opt/bandapp/backups/
ssh -i ~/.ssh/bandule_deploy root@<새 서버> 'cd /opt/bandapp && sh deploy/backup/pg-restore.sh backups/bandapp-<시각>.dump'
rm -f bandapp-<시각>.dump                                                                # PC 에서 바로 지운다
```

DB 밖이라 복구되지 않는 것(R2 사진·영상의 참조, Redis 로그인 상태, 인증서)은 [DEPLOY.md §5-2](DEPLOY.md) 표를 본다.

### 7-7. 덤프를 다룰 때 지킬 것

- **저장소 폴더 안에 두지 않는다.** 이 저장소는 공개다. `tools/check-repo-rules.sh`·pre-commit 이 `*.dump` 를 막지만 그것만 믿지 않는다.
- 연 덤프는 쓰고 나면 **바로 지운다**(PC·서버 임시 위치 모두).
- 개인키 암호·R2 키를 명령줄 인자나 문서에 적지 않는다(§7-3 의 `read`).
- 메신저·메일·클라우드 드라이브로 덤프를 옮기지 않는다. 꼭 옮겨야 하면 `.gpg` 상태로.

### 7-8. 훈련 기록

| 날짜 | 어디서 | 백업 | 결과 |
|---|---|---|---|
| 2026-09-06 | 로컬 스택(V13) | 로컬 덤프 | `restore_drill` 복원·운영 경로 복원 모두 성공([DEPLOY.md §5-3](DEPLOY.md)) |
| **2026-10-05** | 사용자 PC(Windows, Git Bash gpg 2.4.7, Docker 29.7.2, `postgres:16-alpine`) — 클로드, 복호화는 사용자 | R2 `bandapp-20261003T183001Z.dump.gpg`(40,526 B) | R2 → PC 로 받음 → 개인키로 복호화(147,638 B, `PGDMP`) → `pg_restore --list` 테이블 데이터 24개 → 임시 DB 복원 성공: users 24·bands 16·reservations 56·settlements 6·settlement_shares 15·flyway 23. 운영(읽기만): users 28(백업 뒤 가입 4)·bands 17(뒤 1)·reservations 56(뒤 0)·flyway 23 → **백업 시점과 일치**. 덤프·`.gpg`·컨테이너 삭제. QA OPS-02·OPS-11 |

---

## 8. 매일 자동 점검

배포 직후 헬스체크 말고는 감시가 없었다. 그래서 구매 알림이 **7일 동안 초당 한 번씩**
서버를 때리는 동안 아무도 몰랐다 ([TROUBLESHOOTING.md](TROUBLESHOOTING.md) 2026-09-09).

이제 매일 07:00 KST 에 GitHub Actions 가 서버를 본다
([`.github/workflows/prod-check.yml`](../.github/workflows/prod-check.yml)).
걸리면 **`prod-check` 라벨이 붙은 이슈**가 열리고, 이미 열려 있으면 거기 댓글이 달린다.

| 보는 것 | 걸리는 기준 |
|---|---|
| 밖에서 HTTPS | `https://api.bandule.com/actuator/health` 가 200 이 아님 |
| 인증서 | 만료까지 14일 미만 (Let's Encrypt 갱신이 멈춘 것) |
| 앱 · 컨테이너 | health 가 UP 이 아님, 컨테이너가 덜 떠 있음 |
| 디스크 | 85% 이상 |
| 백업 | 마지막 덤프가 36시간보다 오래됨 |
| 에러 | 24시간 에러·예외 로그 200줄 초과 |
| 요청 | 1시간 요청 2000건 초과 (초당 1회 = 3600건) |

임계값은 [`deploy/prod-check.sh`](../deploy/prod-check.sh) 맨 위 변수로 몰아 뒀다.
오탐이 잦으면 거기만 고친다.

**직접 돌려 볼 때** — Actions 로그는 공개라 자동 점검은 숫자만 남긴다(`QUIET=1`).
어떤 경로가 얼마나 맞고 있는지, 어떤 에러가 많은지까지 보려면 직접 돌린다:

```bash
ssh -i ~/.ssh/bandule_deploy root@64.176.231.126 'sh -s' < deploy/prod-check.sh
```

워크플로를 손으로 한 번 돌리려면 GitHub → Actions → **운영 점검** → Run workflow.

---

## 9. 새 컴퓨터로 옮기기 — 포맷 전 백업 · 계정과 비밀번호 · 결제 · 새 PC 설치

> PC 를 포맷하거나 바꿀 때 **이 절만 위에서부터 따라 하면** 새 PC 에서 개발·빌드·배포·운영을 그대로 이어 갈 수 있게 썼다.
> 2026-10-05 이 PC 를 조사해서 만든 목록이다. 새 비밀 파일·계정·결제가 생기면 이 절과
> [SERVICES_AND_SHUTDOWN.md](SERVICES_AND_SHUTDOWN.md) 에 같이 적는다.
>
> **⚠️ 이 저장소는 공개다. 비밀번호·키·토큰의 "값" 은 여기 절대 적지 않는다.** 여기에는 무엇이 필요한지, 어디에 두는지,
> 잃으면 어디서 다시 받는지만 적는다. 값은 **개인 보관함**에 둔다 — 이 절에서 "개인 보관함" 은
> ① 비밀번호 관리자(계정·암호용)와 ② 암호를 건 USB 또는 암호화한 압축 파일(파일용), **서로 다른 두 곳**을 말한다.
> 메일·메신저·공개 클라우드 폴더에 평문으로 두지 않는다.

### 9-1. 포맷 전에 — 개인 보관함으로 옮길 파일

이 파일들은 git 에 **없다**(`.gitignore`). PC 와 함께 사라진다.

| 중요 | 파일 (이 PC 기준 경로) | 무엇 | 잃으면 | 다시 얻는 법 |
|---|---|---|---|---|
| 🔴 | `client/android/bandule-release.jks` | Play **업로드 키**(앱 서명) | Play 에 업데이트를 못 올린다 | Play Console › 앱 무결성 › 업로드 키 재설정 요청(며칠 걸림). **반드시 백업** |
| 🔴 | `client/android/key.properties` | 위 키스토어의 암호·별칭·경로(`storePassword` `keyPassword` `keyAlias` `storeFile`) | 키스토어가 있어도 못 연다 | 암호를 비밀번호 관리자에도 따로 적어 둔다 |
| 🔴 | `~/.ssh/bandule_deploy`, `~/.ssh/bandule_deploy.pub` | 운영 서버 SSH 키 | 서버 접속 불가 | Vultr 웹 콘솔로 새 키 등록(§9-6) |
| 🔴 | `~/.android/debug.keystore` | 개발 빌드 서명 키 — **카카오 콘솔에 이 키 해시가 등록돼 있다** | 새 PC 의 개발 빌드에서 카카오 로그인·지도 실패 | 카카오 개발자 콘솔에 새 키 해시 추가(§9-6) |
| 🔴 | 백업 복호화 개인키(gpg, 지문 `B4A794DF793BE679250199A84218121C3F08D54F`) + 암호 | R2 DB 백업을 여는 열쇠 | R2 백업을 아무도 못 연다 | **다시 만들 수 없다.** 2026-10-05 두 곳 보관 확인(QA OPS-16). 새 PC 로 가져오기는 §7-2 |
| 🟡 | 서버의 `/opt/bandapp/.env.prod` | 운영 설정·비밀값 전부(DB·Redis 비번, JWT 키, R2 키, 카카오 키, 메일 SMTP, 결제 웹훅 비밀 등) | 서버가 날아가면 새 서버를 못 세운다 | **로컬 `.env.prod` 가 아니라 서버 것을** 받아 보관: `scp -i ~/.ssh/bandule_deploy root@64.176.231.126:/opt/bandapp/.env.prod ./env.prod.backup` → 바로 개인 보관함으로 옮기고 PC 에서 지운다(저장소 폴더에서 받지 않는다 — 2026-09-08 유출 사고) |
| 🟡 | 서버의 서비스 계정 JSON 2개 — `.env.prod` 의 `PLAY_SA_HOST_PATH`(Play 구매 검증), `FCM_CREDENTIALS_HOST_PATH`(푸시) 가 가리키는 파일 | Google 서비스 계정 키 | 결제 검증·푸시가 멈춘다 | Google Cloud 콘솔 › IAM › 서비스 계정 › 키 새로 만들기(옛 키는 삭제) |
| 🟡 | `.env.prod` (저장소 폴더) | 서버 것의 **옛 사본**일 수 있다 | — | 서버 것을 정본으로 쓴다. 이 파일은 백업하지 않아도 된다 |
| 🟡 | `.env` (저장소 폴더) | 로컬 개발 설정(개발 DB·JWT·카카오·R2·FCM) | 로컬 `docker compose up` 이 안 뜬다 | 개발용 값이라 새로 만들어도 된다. 키 이름은 이 파일에서 확인 |
| 🟡 | `client/dart_defines.json` | `KAKAO_NATIVE_APP_KEY`, `API_BASE_URL` | 앱 빌드에 카카오 키가 안 들어간다 | 카카오 개발자 콘솔 › 앱 키 › 네이티브 앱 키 |
| 🟡 | `client/android/local.properties` | `kakao.appKey`(직접 넣는 줄) + 나머지는 flutter 가 채움 | 안드로이드 카카오 리다이렉트 실패 | 위와 같은 네이티브 앱 키 한 줄 |
| 🟡 | `client/android/app/src/prod/google-services.json`, `.../dev/google-services.json` | Firebase 앱 설정(푸시·Crashlytics) | 빌드 실패 또는 푸시 꺼짐 | Firebase 콘솔 › 프로젝트 설정 › 앱 › `google-services.json` 다운로드(운영 `bandule-b94d2`, 개발 `bandapp-dev-67c6f`) |
| 🟡 | `secrets/bandapp-dev-67c6f-firebase-adminsdk-*.json` | 개발 서버 푸시용 서비스 계정 | 로컬 서버 푸시 안 됨 | Firebase 콘솔(개발) › 서비스 계정 › 새 비공개 키 |
| 🟢 | `C:\Users\<사용자>\.claude\projects\C--band-bandApp\memory\` | 클로드 작업 기억(답변 언어, 커밋 규칙 등) | 클로드가 이전 합의를 잊는다 | 폴더째 백업 → 새 PC 같은 위치에 복사 |
| 🟢 | `.codex/`, `.agents/`, `.claude/settings.local.json` | 코덱스·클로드 이 PC 전용 설정 | 다시 설정하면 된다 | 필요하면 함께 백업 |
| ⚪ | `local.properties`(루트), `bin/`, `build/`, `.gradle/` 등 | 자동 생성 | — | 백업하지 않는다 |
| ⚪ | `backups/bandapp-20260905T*.dump` | 2026-09-05 로컬 개발 DB 덤프 | — | **백업하지 않는다.** 회원 데이터일 수 있으니 필요 없으면 지운다 |

포맷 전 마지막 확인:

```bash
# 저장소에 커밋 안 한 작업이 없는지 — 있으면 브랜치에 커밋·푸시한다
cd /c/band/bandApp && git status --short && git stash list
# 키 해시를 적어 둔다(새 PC 에서 카카오 콘솔 등록이 맞는지 비교용 — 값이 아니라 지문이라 적어도 된다)
keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android | openssl sha1 -binary | openssl base64
#   → 2026-10-05 이 PC: ahCJ5a5dXyiPh3x9ksny6yMbjzk=  (카카오 콘솔에 등록된 개발 키 해시. 새 PC 에서 debug.keystore 를 옮겼다면 같은 값이 나와야 한다)
```

### 9-2. 계정과 비밀번호 — 비밀번호 관리자에 있어야 하는 것

**값은 비밀번호 관리자에만.** 아래 표의 "보관할 것" 이 모두 들어 있는지 포맷 전에 확인한다.
**2단계 인증(OTP) 앱이 폰에 있으면** 폰은 포맷하지 않으니 괜찮지만, 각 서비스의 **복구 코드**도 함께 보관한다.

| 계정 | 쓰는 곳 | 보관할 것 |
|---|---|---|
| **Google 계정**(개발자 계정 주인) | Play Console, Firebase(운영·개발), Google Cloud(서비스 계정·Pub/Sub), Google 결제 프로필(정산 계좌) | 비밀번호, 2단계 복구 코드 |
| **GitHub** `Yekapark` | 저장소 `Yekapark/bandApp`, Actions 시크릿(`DEPLOY_HOST` `DEPLOY_USER` `DEPLOY_KEY` `DEPLOY_PORT`), GHCR | 비밀번호, 2단계 복구 코드. 새 PC 에서는 `gh auth login` |
| **Cloudflare** | 도메인 `bandule.com`(등록·DNS), R2(`bandule-prod`), Pages(약관 사이트), Email Routing(`notice@bandule.com` → 개인 메일) | 비밀번호, 2단계 복구 코드, R2 API 토큰(값은 서버 `.env.prod`) |
| **Vultr** | 운영 서버(서울 VM `64.176.231.126`) | 비밀번호, 2단계 복구 코드. 서버 **root 비밀번호**는 Vultr 서버 상세 화면에 있다(웹 콘솔 로그인용) |
| **Resend** | 메일 발송(인증·재설정·신고 알림) | 비밀번호. SMTP 값은 서버 `.env.prod`(`MAIL_SMTP_*`) |
| **카카오 개발자** | 카카오 로그인·장소 검색·지도 | 비밀번호. 앱 키(네이티브·REST·어드민)는 콘솔에서 다시 볼 수 있다 |
| **UptimeRobot** | `/actuator/health` 감시 | 비밀번호 |
| **개인 메일**(`notice@bandule.com` 전달받는 곳, Play 스토어 연락처) | 사용자 문의·인증 메일 | 비밀번호, 복구 수단 |
| **홈택스 · 위택스 · 정부24** | 사업자등록·통신판매업·등록면허세 | 공동/간편 인증서(인증서 파일이면 백업) |
| **업로드 키스토어 암호** | `key.properties` 의 두 암호 | 비밀번호 관리자에 따로 한 번 더 |
| **gpg 백업 개인키 암호** | R2 백업 복호화 | 개인키 파일과 **다른 곳**에(OPS-16) |
| 심사·QA 데모 계정 `demo.leader@bandule.com` `demo.guitar@bandule.com` `demo.drum@bandule.com` | Play 심사 로그인 안내, QA | 비밀번호(Play Console › 앱 콘텐츠 › 앱 액세스에도 적혀 있다) |
| Play **라이선스 테스터** Google 계정 | 테스트 결제 | 비밀번호 |
| Claude · Codex(ChatGPT) | 작업 도구 | 로그인 정보 |

### 9-3. 돈이 나가는 곳 — 새 PC 와 상관없이 계속 청구된다

결제 수단·청구서는 PC 가 아니라 각 서비스 계정에 묶여 있다. 포맷해도 끊기지 않고, 계정 로그인만 되면 된다.
자세한 금액·주기·폐업 시 정리는 [SERVICES_AND_SHUTDOWN.md §1·§2](SERVICES_AND_SHUTDOWN.md).

| 💳 결제 | 금액 · 주기 | 청구서 보는 곳 |
|---|---|---|
| Vultr 운영 서버 | 매월, 약 $10대 | Vultr 콘솔 › Billing |
| 도메인 `bandule.com` | 매년 자동 갱신, 약 $10대 | Cloudflare › Domain Registration |
| Google Play 개발자 등록 | 최초 1회 $25(끝남) | Play Console › 설정 › 개발자 계정 |
| 통신판매업 등록면허세 | 신고 시 + 매년 1월 | 위택스 |
| Google Play 수수료 | 구독 매출의 15%(정산에서 차감) | Play Console › 재무 보고서 |
| 결제 수단만 걸린 무료 서비스 | Cloudflare R2(무료 한도 초과분), Google Cloud(Pub/Sub) | 각 콘솔 › 결제 |

### 9-4. 새 PC 설치 순서

이 PC 기준 버전(2026-10-05). CI 도 같은 버전을 쓴다(`.github/workflows/`).

| 순서 | 설치 | 버전 · 확인 | 왜 |
|---|---|---|---|
| 1 | **Git for Windows** (Git Bash) | `git --version`, `gpg --version`(2.4 포함) | 저장소·셸·백업 복호화 |
| 2 | **GitHub CLI** `gh` | 2.98 → `gh auth login`(Yekapark) | PR·CI 확인 |
| 3 | **JDK 21** | `java -version` → 21 | 백엔드 빌드(Gradle). 안드로이드 빌드도 21 로 된다 |
| 4 | **Docker Desktop** | `docker info` | 로컬 실행·통합 테스트(Testcontainers)·백업 복원 |
| 5 | **Android Studio** (SDK·platform-tools) | `adb version` | 안드로이드 SDK, 폰 연결(QA) |
| 6 | **Flutter 3.47.2 stable** | `flutter --version`, `flutter doctor` | 앱 |
| 7 | **Python 3.13** + `pip install pillow markdown` | `python --version` | 빌드 스크립트(`release_store.py` 등), 스토어 이미지(Pillow), 약관 사이트(markdown) |
| 8 | **Node.js 24** + `npm i -g firebase-tools` → `firebase login` | `firebase --version`(15) | 테스터 배포(`client/tools/release_tester.py`) |

설치 뒤:

```bash
# 1) 저장소
cd /c && mkdir -p band && cd band
git clone https://github.com/Yekapark/bandApp.git && cd bandApp
git config core.hooksPath .githooks          # 비밀 파일 커밋 차단 훅 — 새 PC 마다 한 번(CLAUDE.md 규칙)
git config user.name "<이름>" && git config user.email "<GitHub 이메일>"

# 2) 개인 보관함의 파일을 제자리에 (§9-1 표의 경로 그대로)
#    client/android/bandule-release.jks, client/android/key.properties, client/dart_defines.json,
#    client/android/app/src/{prod,dev}/google-services.json, secrets/…adminsdk….json, .env
#    ~/.ssh/bandule_deploy(.pub)  → chmod 600 ~/.ssh/bandule_deploy
#    ~/.android/debug.keystore    (Android Studio 를 처음 연 뒤 생긴 것을 덮어쓴다)
#    클로드 기억: memory 폴더를 C:\Users\<사용자>\.claude\projects\C--band-bandApp\memory\ 로
echo "kakao.appKey=<네이티브 앱 키>" >> client/android/local.properties   # 값은 비밀번호 관리자/카카오 콘솔에서

# 3) 백업 개인키 (§7-2)
gpg --import bandule-backup-secret.asc
```

### 9-5. 새 PC 에서 잘 되는지 확인

| 확인 | 명령 | 기대 |
|---|---|---|
| 비밀 파일이 git 에 안 잡힘 | `git status --short` | 아무 것도 안 나옴(위 파일들은 무시 목록) |
| 서버 접속 | `ssh -i ~/.ssh/bandule_deploy root@64.176.231.126 'bandule health'` | 안쪽·바깥 모두 정상 |
| 백엔드 | `./gradlew build` (Docker 켠 채로) | 테스트 포함 성공 |
| 로컬 실행 | `docker compose up` | app·postgres·redis 기동 |
| 앱 분석·테스트 | `cd client && flutter pub get && flutter analyze && flutter test` | 오류 0·전부 통과 |
| 스토어 빌드 검사 | `cd client && python tools/release_store.py --no-bump` | 서버 주소·16KB·targetSdk·**업로드 키 서명** 모두 OK(번호는 안 올림) |
| 카카오(개발 빌드) | 폰에 개발 빌드 설치 → 카카오 로그인 | 로그인됨 — 안 되면 키 해시(§9-6) |
| 백업 열기 | §7-3·§7-4 | `.dump.gpg` 가 열린다 |
| PR·CI | `gh pr list` | 목록이 나온다 |

### 9-6. 잃어버렸을 때

| 잃은 것 | 복구 |
|---|---|
| **업로드 키스토어**(`.jks`) 또는 그 암호 | Play Console › 테스트 및 출시 › 앱 무결성 › 앱 서명 › **업로드 키 재설정 요청**. 새 키스토어를 만들어(`keytool -genkeypair`) 인증서(.pem)를 제출 → Google 승인 뒤 새 키로 올린다. 승인까지 업데이트를 못 올린다 |
| **SSH 키** | 새 키 `ssh-keygen -t ed25519 -f ~/.ssh/bandule_deploy` → Vultr 콘솔 › 서버 › **View Console** 에서 root 로 로그인(root 비밀번호는 서버 상세 화면) → `/root/.ssh/authorized_keys` 끝에 새 `.pub` 한 줄 추가. GitHub 자동 배포는 Actions 시크릿 `DEPLOY_KEY` 를 따로 쓰므로 영향 없다 |
| **debug.keystore** | 새 PC 의 키 해시(§9-1 명령)를 카카오 개발자 콘솔 › 앱 › 플랫폼 › Android › **키 해시 추가**. 옛 해시는 지워도 된다 |
| **gpg 백업 개인키** | 복구 불가. 새 키 쌍을 만들어 공개키를 `deploy/backup/backup-pubkey.asc` 로 바꾸고 배포 → 그 뒤 백업부터 새 키로 잠긴다. **옛 R2 백업은 영영 못 연다** |
| **서버 `.env.prod`**(서버도 날아감) | 키 이름은 로컬 `.env.prod`·`docker-compose.prod.yml` 에서 확인하고 값은 각 콘솔에서 새로 발급: R2(Cloudflare), 카카오, Resend SMTP, Google 서비스 계정. DB·Redis 비밀번호·JWT 키는 새로 만든다(`openssl rand -base64 48`) — **JWT 를 바꾸면 전원 다시 로그인** |
| **google-services.json · dart_defines · kakao.appKey** | §9-1 "다시 얻는 법" 칸 |
| **GitHub 로그인(2단계 포함)** | 복구 코드로 로그인. 없으면 GitHub 지원 — 저장소 접근이 막히면 배포도 못 한다 |
