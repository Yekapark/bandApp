# Phase 10 — 요금제 (결제 어댑터 제외)

## 1. 한 줄 요약

밴드마다 **FREE / PREMIUM 요금제**가 생겼다. FREE 는 사진·영상 첨부를 **업로드일 + 30일** 보관하고,
PREMIUM 은 **무제한**(만료일 없음) 보관한다. 밴드장이 `구독`을 누르면 그 밴드의 **기존 첨부 미디어
만료일이 전부 사라지고**(무제한), `해지`를 누르면 **해지 시점부터 30일** 유예 뒤 만료되도록 다시
계산된다. 실제 결제는 앱 밖(앱스토어·구글플레이 결제)에서 이뤄지므로, 이번 구현은 **`PaymentGateway`
인터페이스 + 아무것도 하지 않고 성공만 반환하는 no-op 구현체**까지다. 구독 시작/갱신/해지 로직은
이 인터페이스에만 의존하므로, 나중에 실제 PG 어댑터만 갈아 끼우면 된다.

## 2. 이 Phase의 목표 (`docs/BUILD_PLAN.md` 기준)

- `BandPlan` 관리 — 밴드별 FREE/PREMIUM 티어
- 미디어 `expiresAt` 계산이 밴드의 현재 플랜을 따르도록 연결
- 플랜 변경 시 기존 미디어의 `expiresAt` 재계산 (업그레이드 시 연장, 다운그레이드 시 유예기간 부여)
- `PaymentGateway` 인터페이스 정의 + no-op 구현체
- 결제 도메인 로직(구독 시작/갱신/해지)은 인터페이스에만 의존하도록 작성

**완료 기준**: no-op 게이트웨이로 FREE → PREMIUM 전환 시 기존 미디어의 만료일이 연장되는 테스트가 통과한다.

> **실제 PG 연동은 이 Phase에 포함하지 않는다.**

### `BUILD_PLAN.md`에 없어 이번에 정한 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| **테이블명** | `band_plans` (복수형) | 다른 테이블(`bands`·`device_tokens`·`notification_settings`)과 같은 규칙. 밴드당 한 행이어도 복수형. |
| **행 하나 vs 이력 테이블** | 밴드당 `band_plans` 한 행, 티어 변경 시 그 행을 제자리 수정(`started_at`·`expires_at` 갱신) | BUILD_PLAN 필드 스케치(`{ id, bandId, tier, mediaRetentionDays, startedAt, expiresAt }`)가 "현재 플랜 한 행" 모양이다. 구독 이력이 필요하면 나중에 별도 테이블로. |
| **"업그레이드 시 연장"의 의미** | 기존 READY 미디어의 `expires_at` 을 **NULL(무제한)** 로 | BUILD_PLAN "PREMIUM 은 null(무제한)" + `V8` 마이그레이션 주석("프리미엄(Phase 10)은 NULL")과 일치. 사용자 확인 완료. |
| **다운그레이드 유예기간** | **30일**, "교체" 방식(기존 만료일을 `now + 30일` 로 덮어씀) | 디자인 시안 문구 "해지 후에도 이미 올린 미디어는 30일간 볼 수 있어" + FREE 신규 업로드 보관기간(30일)과 동일. 사용자 확인 완료. `app.plan.downgrade-grace-days` 로 조정 가능. |
| **PREMIUM 구독 만료(`expires_at` 경과) 자동 처리** | **이번 Phase 제외**. `expires_at` 은 저장·응답에 노출만 하고 강제하지 않는다 | no-op 게이트웨이는 "구독이 끝났다"고 알려줄 주체가 없다. 실제 만료 판정은 PG 웹훅·정산이라, "만료된 PREMIUM → FREE" 배치는 PG 어댑터 Phase 로 미룬다. 사용자 확인 완료. `PlanService` 에 `TODO(PG 어댑터)` 주석. |
| **`subscription_ref` 컬럼(nullable)** | 지금 추가 | BUILD_PLAN 필드 목록엔 없지만, 실제 어댑터의 `renew`/`cancel` 이 게이트웨이 구독 id 를 필요로 한다. 지금 넣어 V11 마이그레이션을 피한다. no-op 은 `"noop-{bandId}"` 를 쓴다. |
| **no-op 전환에 대한 재요청** | 이미 PREMIUM 인데 `구독` → 409 `PLAN_ALREADY_PREMIUM`, 이미 FREE 인데 `해지` → 409 `PLAN_ALREADY_FREE` | `RESERVATION_NOT_PENDING` 등 기존 스타일. 게이트웨이 호출 **전에** 걸러 실 PG 이중 청구를 막는다. |
| **`renew` 엔드포인트** | 포함 (`POST /plan/renew`) | BUILD_PLAN "구독 시작/갱신/해지" 를 인터페이스가 온전히 갖추도록. PREMIUM 구독기간만 연장, 미디어 재계산 없음. |
| **동시 티어 변경 직렬화** | `band_plans` 행에 `SELECT … FOR UPDATE`(`PESSIMISTIC_WRITE`) | 상태 전이에 비관적 락을 거는 `ReservationRepository`/`SettlementService` 선례. 구독 더블탭 시 한 번만 전이·재계산되고 나머지는 409. |
| **결제 게이트웨이 호출 위치** | `PlanService`(트랜잭션 없음)가 게이트웨이를 **트랜잭션 밖에서** 먼저 호출 → 확정값으로 `PlanMutationService`(`@Transactional`)에서 티어 플립 + 미디어 재계산을 한 트랜잭션으로 | CLAUDE.md "외부 HTTP 호출은 `@Transactional` 안에서 하지 않는다". 게이트웨이 뒤 DB 쓰기 사이엔 외부 I/O 가 없어 한 트랜잭션으로 묶는 게 안전하다. |
| **요금제 행이 없는 밴드(레거시)** | 미디어 업로드 경로는 FREE/30 으로 폴백 + `warn` 로그(업로드가 500 나면 안 됨). 요금제 조회·전환 경로는 `PLAN_NOT_FOUND`(시끄럽게) | 백필 + 생성 시 provisioning 으로 행은 항상 있어야 한다. 폴백은 안전망, 전환 실패는 버그 신호. |

## 3. 무엇을 만들었나

### 3.1 데이터베이스 — `src/main/resources/db/migration/V10__plan.sql`

`band_plans` 테이블 1개 신설 + 기존 밴드 백필.

| 컬럼 | 내용 |
|---|---|
| `id` | PK |
| `band_id` | `bands(id)` FK, **유니크**(밴드당 한 행) |
| `tier` | `FREE` / `PREMIUM` CHECK, 기본 `FREE` |
| `media_retention_days` | 첨부 보관일수. FREE=30, PREMIUM=NULL |
| `subscription_ref` | 게이트웨이 구독 식별자(nullable). no-op = `noop-{bandId}` |
| `started_at` | 현재 티어가 시작된 시각 |
| `expires_at` | PREMIUM 구독기간 종료(nullable). **정보성** — 경과해도 자동 다운그레이드 없음 |
| `created_at` / `updated_at` | `device_tokens` 처럼 `updated_at` 은 자체 컬럼 |

CHECK 제약으로 불변식을 DB 레벨에서 강제한다:
- `ck_band_plans_retention` — FREE ⇒ 보관일수 있음(>0) / PREMIUM ⇒ 보관일수 NULL
- `ck_band_plans_free_no_expiry` — FREE ⇒ `expires_at` NULL

백필: `INSERT … SELECT b.id, 'FREE', 30, b.created_at, now(), now() FROM bands b`. Phase 10 코드는 밴드마다
요금제 행이 있다고 가정한다(`ddl-auto: validate`). `30` 은 `BandPlan.FREE_RETENTION_DAYS` 와 일치시킨다.

### 3.2 요금제 도메인 — `src/main/java/com/yeka/bandapp/plan/`

| 파일 | 역할 |
|---|---|
| `entity/PlanTier` | `FREE` / `PREMIUM` enum |
| `entity/BandPlan` | 엔티티. 상태 변경은 setter 가 아니라 `upgradeToPremium(now, periodEnd, ref)` / `downgradeToFree(now)` / `renew(now, newEnd)` 메서드. `freePlan(bandId, now)` 정적 팩토리. FREE⇔30일 / PREMIUM⇔NULL 을 메서드가 항상 짝으로 맞춘다. |
| `repository/BandPlanRepository` | `findByBandId`(조회), `findByBandIdForUpdate`(`PESSIMISTIC_WRITE` — 전환용) |
| `gateway/PaymentGateway` | 인터페이스. `subscribe`/`renew`/`cancel` + 커맨드·결과 record. 커맨드에 카드·토큰 정보 없음(어댑터가 자체 처리) |
| `gateway/NoOpPaymentGateway` | `@Component`. 항상 즉시 성공. `subscribe`/`renew` → `ref="noop-{bandId}"`, 구독기간 종료 = 요청 시각 + `premiumPeriodDays` |
| `config/PlanProperties` | `app.plan.*` — `premiumPeriodDays`(365, 밴드별 1년 구독), `downgradeGraceDays`(30), `planCode`(`PREMIUM_YEARLY`), `expireCron`, `zone`. 유효하지 않으면 기본값 복귀 |
| `service/MediaRetention` | **순수 함수** `expiresAt(uploadedAt, retentionDays)` — null ⇒ 무제한(null), ≤0 ⇒ 예외. `FREE_RETENTION_DAYS=30` |
| `service/PlanProvisioningService` | `createDefaultPlan(bandId, now)` — 밴드 생성 시 FREE 행 생성. `BandService.create` 가 호출 |
| `service/PlanDirectoryService` | 타 도메인용 읽기 창구. `mediaExpiresAt(bandId, uploadedAt)`(게시판이 호출), `currentPlan(bandId)`(→ `PlanView` record, 엔티티 노출 안 함) |
| `service/PlanMutationService` | `@Transactional`. `applyUpgrade`/`applyDowngrade`/`applyRenew` — `FOR UPDATE` 로 행 잠그고 티어 플립 + 미디어 재계산을 한 트랜잭션으로. `MediaDirectoryService` 주입 |
| `service/PlanService` | **`@Transactional` 없음**. 게이트웨이를 트랜잭션 밖에서 호출한 뒤 `PlanMutationService` 호출. `view`/`subscribe`/`cancel`/`renew` |
| `dto/PlanResponse` | `{ tier, mediaRetentionDays, startedAt, expiresAt }` record |
| `controller/PlanController` | `/api/v1/bands/{bandId}/plan` — `GET`(멤버), `POST /subscribe`·`/cancel`·`/renew`(밴드장). 태그 "16. 요금제" |

### 3.3 게시판 미디어 연결 — `src/main/java/com/yeka/bandapp/board/`

| 변경 | 내용 |
|---|---|
| `service/MediaDirectoryService` (신규) | 요금제 도메인이 밴드 미디어 보관기한을 재계산하는 창구. `extendRetentionForBand`(→무제한) / `applyGracePeriodForBand`(→유예 종료). `RoomDirectoryService` 미러 |
| `repository/MediaAttachmentRepository` | `@Modifying` 벌크 UPDATE 2개 추가 — `clearExpiryForBandReadyMedia`, `setExpiryForBandReadyMedia`. `WHERE status='READY' AND boardPostId IN (SELECT p.id FROM BoardPost p WHERE p.bandId = :bandId)` |
| `service/MediaAttachmentService.complete` | `MediaPolicy.freePlanExpiresAt(now)` → `planDirectory.mediaExpiresAt(bandId, now)`. `PlanDirectoryService` 주입 |
| `service/MediaPolicy` | `freePlanExpiresAt` / `FREE_PLAN_RETENTION_DAYS` **삭제** — 형식·크기 전용으로 복귀. 보관기한 계산은 전부 `plan` 으로 이동 |

### 3.4 기타

- `common/exception/ErrorCode` — `PLAN_NOT_FOUND`(404), `PLAN_ALREADY_PREMIUM`(409), `PLAN_ALREADY_FREE`(409), `PAYMENT_FAILED`(402)
- `band/service/BandService.create` — 밴드·리더 멤버 저장 뒤 `planProvisioningService.createDefaultPlan(...)` 추가
- `application.yml` — `app.plan.*` 블록

## 4. 어떻게 동작하나

### 미디어 보관기한 결정 (업로드 완료 시)

`POST …/media/{id}/complete` → `MediaAttachmentService.complete` 가 R2 HEAD 로 실제 업로드를 확인한 뒤:

```
expiresAt = planDirectory.mediaExpiresAt(bandId, now)
          = FREE  → now + 30일
          = PREMIUM → null (무제한)
mediaRepository.markReady(mediaId, now, expiresAt)
```

`null` 이면 컬럼에 `NULL` 이 들어가고, Phase 9 만료 배치(`findExpiredReady` 의 `expires_at IS NOT NULL`
+ 부분 인덱스)가 자동으로 건너뛴다 → PREMIUM 미디어는 절대 만료되지 않는다.

### 티어 전환 (`POST …/plan/subscribe`)

```
PlanService.subscribe (트랜잭션 없음)
 ├─ accessGuard.requireLeader
 ├─ findByBandId → 이미 PREMIUM 이면 409 (게이트웨이 호출 전)
 ├─ paymentGateway.subscribe(...)          ← 트랜잭션 밖 (no-op: 즉시 성공)
 │    실패면 402 PAYMENT_FAILED
 └─ PlanMutationService.applyUpgrade (@Transactional)
      ├─ findByBandIdForUpdate            ← 행 잠금 (동시 요청 직렬화)
      ├─ isFree() 재확인 → 아니면 409
      ├─ plan.upgradeToPremium(now, periodEnd, ref)   → tier=PREMIUM, retention=NULL, expires_at=periodEnd
      └─ mediaDirectory.extendRetentionForBand(bandId) → 그 밴드 READY 미디어 expires_at = NULL
      (둘이 한 트랜잭션 — 원자 커밋)
```

`cancel` 은 대칭: `applyDowngrade` 가 `plan.downgradeToFree(now)` + `setExpiryForBandReadyMedia(bandId, now + 30일)`.
`renew` 는 `plan.renew(now, newPeriodEnd)` 만(미디어는 이미 무제한).

`EXPIRED` 미디어는 `WHERE status='READY'` 로 제외된다 — R2 객체가 이미 삭제돼 되살릴 수 없다.
`PENDING` 미디어도 제외 — 완료 콜백이 그때의 요금제로 계산한다.

## 5. 직접 확인하는 법

### 사전 준비

로컬 실행: `docker compose up` (app + postgres + redis). R2 키가 없어도 요금제 API 는 전부 동작하고,
미디어 업로드만 503 이다. 요금제 전환 자체(FREE↔PREMIUM)와 응답은 미디어 없이도 확인할 수 있다.

### 흐름

1. 계정 가입 → 밴드 생성 (`POST /api/v1/bands`). 이때 FREE 요금제 행이 자동 생성된다.
2. `GET /api/v1/bands/{bandId}/plan` →
   `{"tier":"FREE","mediaRetentionDays":30,"startedAt":"…","expiresAt":null}`
3. `POST /api/v1/bands/{bandId}/plan/subscribe` (밴드장 토큰, 본문 없음 `{}`) →
   `{"tier":"PREMIUM","mediaRetentionDays":null,"startedAt":"…","expiresAt":"<약 30일 뒤>"}`
4. 다시 `subscribe` → **409 `PLAN_ALREADY_PREMIUM`**
5. 일반 멤버 토큰으로 `subscribe` → **403 `NOT_BAND_LEADER`**
6. `POST …/plan/renew` → `expiresAt` 이 다시 30일 뒤로 밀린다
7. `POST …/plan/cancel` → `{"tier":"FREE","mediaRetentionDays":30,…}`. 다시 `cancel` → **409 `PLAN_ALREADY_FREE`**

### 미디어 재계산까지 보기 (R2 키 필요)

1. FREE 상태에서 게시글 + 이미지 첨부를 올려 READY 로 만든다.
   ```sql
   SELECT id, status, expires_at FROM media_attachments WHERE board_post_id = <postId>;
   -- expires_at 이 업로드 + 30일
   ```
2. `POST …/plan/subscribe` →
   ```sql
   SELECT expires_at FROM media_attachments WHERE id = <mediaId>;  -- NULL
   ```
3. `POST …/plan/cancel` →
   ```sql
   SELECT expires_at FROM media_attachments WHERE id = <mediaId>;  -- now + 30일
   ```

### 기대 결과 / 문제 해결

| 증상 | 원인·해결 |
|---|---|
| `subscribe`/`cancel` 가 403 `NOT_BAND_LEADER` | 요금제 전환은 밴드장만. 조회(`GET`)는 멤버면 누구나. |
| `GET /plan` 이 404 `PLAN_NOT_FOUND` | 이 밴드에 요금제 행이 없다. 정상 경로(밴드 생성 시 자동 생성 + 기존 밴드 백필)에선 안 난다. V10 마이그레이션이 적용됐는지 확인. |
| `subscribe` 가 402 `PAYMENT_FAILED` | no-op 게이트웨이는 실패하지 않는다. 테스트에서 `FailingPaymentGatewayConfig` 를 `@Import` 했을 때만 난다. |
| 업그레이드했는데 오래전에 만료된 미디어가 안 살아난다 | 의도. `EXPIRED` 미디어는 R2 객체가 이미 삭제돼 복구 불가. `READY` 만 재계산 대상. |
| PREMIUM 인데 `expires_at` 이 지났는데도 그대로 PREMIUM | 의도(이번 릴리스). `expires_at` 은 정보성이며, 자동 다운그레이드는 실제 PG 연동 시 구현한다. |

## 6. 실제 검증 기록

로컬 PC 는 Docker 데몬이 없어 Testcontainers 통합 테스트를 돌릴 수 없다(기존 Phase 와 동일 제약 —
`IntegrationTestSupport.<clinit>` 에서 "Could not find a valid Docker environment"). **순수 단위 테스트와
컴파일은 로컬에서 통과**를 확인했고, Testcontainers 통합 테스트는 CI 에서 검증한다.

로컬 통과 (Docker 불필요, 11건):

| 테스트 | 건수 | 내용 |
|---|---|---|
| `plan.MediaRetentionTest` | 5 | FREE=업로드+보관일수, 임의 일수 존중, null=무제한, 0·음수=예외, `FREE_RETENTION_DAYS==30` |
| `plan.BandPlanTest` | 4 | `freePlan`/`upgradeToPremium`/`downgradeToFree` 의 티어·보관일수·만료일·구독참조 전이, `renew` 는 PREMIUM 전용 |
| `plan.NoOpPaymentGatewayTest` | 2 | `subscribe` → 성공·`noop-{bandId}`·구독기간종료 = 요청+30일, `cancel` → 즉시 성공 |

`./gradlew compileJava compileTestJava` 로컬 성공. `board.MediaPolicyTest`(보관기한 테스트 제거 후),
`board.NoFileStreamArchitectureTest` 로컬 통과 — 새 코드에 파일 스트림 마커 없음 재확인.

CI 에서 검증되는 통합 테스트:

| 테스트 | 시나리오 |
|---|---|
| `plan.PlanSubscriptionIntegrationTest` | **완료 기준**: FREE 업로드(만료 30일) → `subscribe` → tier PREMIUM·미디어 `expires_at` NULL. 업그레이드 후 신규 업로드도 NULL. `cancel` → 유예 30일. `cancel` 이 `EXPIRED` 미디어 불변. 같은 방향 재요청 409. 다운그레이드된 밴드 미디어가 유예 경과 후 Phase 9 만료 배치에 잡힘. 동시 `subscribe` 6개 → 정확히 1개 200·나머지 409·**500 없음** |
| `plan.PlanAuthorizationIntegrationTest` | 비-밴드장 멤버 `subscribe`/`cancel`/`renew` → 403 `NOT_BAND_LEADER`. 멤버는 조회 가능. 비-멤버 → 403 `NOT_BAND_MEMBER`. 무토큰 → 401 |
| `plan.PlanCrossBandIsolationIntegrationTest` | 밴드 A 업그레이드 → A 미디어만 NULL, B 미디어·요금제 불변. 다운그레이드 유예도 A 에만 |
| `plan.PlanDefaultIntegrationTest` | 밴드 생성 직후 `GET /plan` → FREE·30·`expiresAt` null (`BandService.create` 가 행을 시드함) |
| `plan.PlanGatewayContractIntegrationTest` | `@Primary` 실패 게이트웨이 주입 → `subscribe` 402 `PAYMENT_FAILED`, 요금제 FREE 유지, 미디어 불변 (도메인이 인터페이스에만 의존) |

### 6.1 구현 후 자체 점검 결과

| 발견 | 심각도 | 조치 |
|---|---|---|
| 결제 게이트웨이 호출(향후 실 PG 는 HTTP)이 티어 플립 트랜잭션 안에서 일어나면 커넥션을 왕복 시간만큼 붙잡는다 (CLAUDE.md 금지) | 높음 | `PlanService`(트랜잭션 없음)가 게이트웨이를 **먼저** 호출하고, 확정값으로 `PlanMutationService`(`@Transactional`)를 호출. 게이트웨이~DB 쓰기 사이엔 외부 I/O 가 없어 티어 플립 + 미디어 재계산을 한 트랜잭션으로 묶는다. 두 클래스 주석에 명시. |
| 동시 `subscribe`(더블탭)가 게이트웨이를 두 번 호출하고 티어·미디어를 두 번 건드릴 수 있다 | 중간 | ① 게이트웨이 호출 **전** 비트랜잭션 사전 확인으로 대부분 차단. ② `PlanMutationService` 가 `findByBandIdForUpdate`(`SELECT … FOR UPDATE`)로 행을 잠가 전이를 직렬화 — 두 번째는 재확인에서 409. `ReservationRepository`/`SettlementService` 선례. 회귀 테스트(`concurrent_subscribe_lands_exactly_one_premium_and_never_500`). |
| 요금제 행이 없는 밴드에서 미디어 업로드가 500 나면 안 된다 | 중간 | `PlanDirectoryService.mediaExpiresAt` 가 행 없으면 FREE/30 폴백 + `warn` 로그. 전환 경로(`PlanService`/`PlanMutationService`)는 반대로 `PLAN_NOT_FOUND` 로 시끄럽게 — 백필 후 행 없음은 버그 신호. |
| FREE⇔30일 / PREMIUM⇔NULL 불변식이 엔티티와 DB 에서 어긋나면 `DataIntegrityViolationException`(500) | 중간 | `ck_band_plans_retention` CHECK 로 DB 강제 + 엔티티 상태 변경 메서드가 두 필드를 항상 짝으로 설정 + `BandPlanTest` 로 전이 고정. `MediaRetention.FREE_RETENTION_DAYS == 30` 단언으로 마이그레이션 리터럴 드리프트 방지. |
| 업그레이드 후 PREMIUM 미디어(`expires_at` NULL)를 Phase 9 만료 배치가 건드리면 안 된다 | 중간 | 배치 쿼리(`findExpiredReady`)와 부분 인덱스가 이미 `expires_at IS NOT NULL` 조건이라 NULL 은 자동 제외. 다운그레이드 후 `now + 유예` 값이 들어가면 다시 편입돼 유예 경과 시 정상 만료 — 통합 테스트로 고정. |
| 이미 `EXPIRED` 된 미디어를 업그레이드가 되살리려 하면(R2 객체 없음) 깨진 상태가 된다 | 낮음 | 재계산 쿼리가 `WHERE status='READY'` 로 `EXPIRED`·`PENDING` 을 제외. `@Operation` 설명과 §5 문제 해결 표에 "복구되지 않는다" 명시. |
| 결제수단(카드·토큰) 정보가 `PaymentGateway` 커맨드에 섞이면 인터페이스가 특정 PG 에 종속된다 | 낮음 | 커맨드 record 에는 `bandId`·`planCode`·`subscriptionRef`·`requestedAt` 같은 PG 비종속 값만. 실제 결제수단은 어댑터가 자체 설정이나 후속 파라미터로 받는다. |
| 방금 완료된 미디어가 티어 플립 직전에 계산돼 잠깐 옛 요금제 만료일을 가질 수 있다 | 낮음 | 다음 티어 변경에서 self-heal. 벌크 UPDATE 를 트랜잭션 마지막 단계로 둬 창을 ~ms 로 좁힘. Phase 10 허용 범위로 문서화. |

## 7. 알려진 이슈 / 제약

- **로컬 통합 테스트 불가** — 이 PC 는 Docker 데몬이 없어 Testcontainers 를 못 돌린다. 통합 테스트는
  CI(우분투 러너)에서 검증한다. 순수 단위 테스트와 컴파일은 로컬에서 확인했다.
- **`band_plans.expires_at` 는 정보성** — PREMIUM 구독기간 종료일을 저장·응답하지만, 경과해도 자동으로
  FREE 로 되돌리지 않는다. "만료된 PREMIUM → FREE" 배치는 실제 PG 연동(웹훅·정산) Phase 로 미뤘다.
  `PlanService` 에 `TODO(PG 어댑터)` 주석.
- **`cancel` 게이트웨이 실패 시 402** — no-op 은 해지에 실패하지 않지만, 방어 분기가 `PAYMENT_FAILED` 를
  던진다. 실제 어댑터를 붙일 때 "결제는 못 끊었는데 등급만 내려가는" 문제를 막으려면 이 분기의 코드·정책을
  재검토해야 한다.
- **게이트웨이 성공 후 티어 플립 트랜잭션 실패** — no-op 은 무해(재시도하면 됨). 실제 PG 는 고전적 이중 쓰기
  문제라 보상 `cancel`·재조정 잡이 필요하다. `PlanService` 에 향후 어댑터용 주석만 남겼다.
- **미디어 재계산이 밴드 전체 벌크 UPDATE** — 게시글·첨부가 아주 많은 밴드는 한 UPDATE 가 길어질 수 있다.
  현재 규모에선 문제없다. 커지면 id 커서 페이징으로 나눠야 한다.
- **결제/구독 이력 없음** — `band_plans` 는 현재 상태 한 행뿐이다. "언제 구독했다 해지했다" 이력이 필요하면
  별도 테이블을 얹어야 한다(BUILD_PLAN 범위 밖).
- **실제 PG 연동 없음** — BUILD_PLAN Phase 10 명시. 요금 정책·PG 선택이 확정되면 `PaymentGateway` 구현체
  (`TossPaymentGateway` 등)만 추가하고 `@ConditionalOnProperty`/`@Primary` 로 선택, no-op 은 폴백으로 남긴다.

## 8. 커밋 · CI 링크

- 브랜치: `phase-10-plan`
- PR: [#33](https://github.com/Yekapark/bandApp/pull/33)
- CI: [run 33650220236](https://github.com/Yekapark/bandApp/actions/runs/33650220236) — ✅ 통과 (2m 14s)
- 주요 커밋:
  - `feat(plan): V10 마이그레이션 + BandPlan 엔티티 + 기본 FREE 플랜 생성`
  - `feat(plan): PaymentGateway 인터페이스 + no-op 구현체`
  - `feat(plan): 요금제 조회·전환 API + 미디어 보관기한 재계산`
  - `refactor(board): 미디어 보관기한 계산을 요금제 도메인에 위임`
  - `test(plan): 단위·통합 테스트`
  - `docs(progress): Phase 10 기록`

> FCM 자격증명 파일 마운트 정리(Phase 10 과 무관)는 별도 PR [#32](https://github.com/Yekapark/bandApp/pull/32).

## 9. 다음 Phase 예고

Phase 11 — 배포. 프로덕션 Docker Compose·Nginx, Let's Encrypt 자동 갱신, GitHub Actions 배포
파이프라인(이미지 빌드 → GHCR → SSH 배포), DB 자동 백업(`pg_dump` 일 1회 → R2, 7일 보관),
복구 절차 문서화 및 실제 복구 테스트 1회.

이후 백엔드 트랙 종료 → Flutter 클라이언트 트랙 시작.
