# 운영 안내서 — DB 접속과 자주 쓰는 쿼리

> 운영자 화면이 아직 없어서 통계·쿠폰 발급·신고 확인은 DB 를 직접 본다.
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

## 5. 신고 확인

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

## 7. 백업에서 되돌리기

[DEPLOY.md](DEPLOY.md) 의 복구 절을 따른다. 덤프는 하루 한 번 R2 로 올라가고 7개를 보관한다.

```bash
cd /opt/bandapp && ./deploy/backup/pg-restore.sh --dry-run   # 먼저 훈련 모드로
```
