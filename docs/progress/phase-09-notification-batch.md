# Phase 9 — 알림 · 배치잡

## 1. 한 줄 요약

합주 앱이 이제 **푸시 알림**을 보낸다 — 새 일정 등록, 승인 요청·결과, 일정 취소, 정산 요청,
일정 리마인더(사용자가 정한 "N분 전"), 참석 미응답 독촉. 발송 채널은 **FCM**(Firebase Cloud Messaging)이고,
서비스 계정 키가 없으면 **발송만 조용히 건너뛰며** 나머지(디바이스 토큰 등록·알림 on/off 설정)는 정상
동작한다. 함께 **미디어 정리 배치 2종**을 붙였다 — 보관기한(무료 30일)이 지난 사진·영상을 R2 에서 지우고
`EXPIRED` 로 돌리는 야간 배치, 업로드 콜백이 오지 않아 1시간 넘게 떠 있는 고아 첨부를 치우는 시간당 배치.
**R2 삭제가 실패해도** 트랜잭션이 깨지지 않고 그 건은 다음 실행에서 자동 재시도된다.

## 2. 이 Phase의 목표 (`docs/BUILD_PLAN.md` 기준)

- FCM 연동, 디바이스 토큰 등록/해제
- 일정 리마인더 발송 (사용자별 설정된 시점)
- 알림 트리거: 일정 리마인더, 새 일정 등록, 승인 요청/결과, 정산 요청, 참석 미응답 독촉
- 사용자별 알림 설정 (on/off, 리마인더 시점 복수 지정)
- 배치잡 1 (일 1회): `expiresAt` 지난 READY 미디어 → R2 삭제 후 EXPIRED 전환
- 배치잡 2 (시간당): 1시간 이상 PENDING 인 미디어 레코드 정리
- 배치잡 3: 정기 일정 회차 이어서 생성 (Phase 5) — **이미 완료**

**완료 기준**: 배치잡이 R2 삭제 실패 시에도 트랜잭션이 깨지지 않고 재시도 가능한 구조이며,
각 배치의 단위 테스트가 통과한다.

### 배치잡 3은 Phase 5에서 이미 끝났다

정기 일정 회차를 이어 만드는 배치(`RecurringExtensionJob`)는 Phase 5에서 구현됐다
(`docs/progress/phase-05-recurring.md` 참조). Phase 9에서는 새로 만들지 않았고, 아래 §5의 확인
절차에 "이미 있는 배치의 동작 확인"만 포함한다.

### `BUILD_PLAN.md`에 없어 이번에 정한 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| **FCM 라이브러리** | `com.google.firebase:firebase-admin:9.4.3` 추가 | BUILD_PLAN Phase 9 "FCM 연동" + `DESIGN.md` 스택(Firebase Cloud Messaging)에 근거가 있다. 멀티캐스트·무효 토큰 정리를 SDK 가 처리해 코드가 가장 적다. **스펙 외 의존성이라 커밋 메시지·이 표에 근거를 남긴다**(Phase 8 AWS SDK 선례). |
| **FCM 키 미설정 시 동작** | 디바이스 토큰 등록·알림 설정 API 는 정상, **푸시 발송만 no-op**(예외 없음) | 알림은 부가 기능이라 미설정이 일정 등록·정산을 깨서는 안 된다. `R2StorageClient` 는 사용자가 직접 부르는 API 라 503 을 던지지만, 알림 발송은 부수 효과라 조용히 건너뛰는 게 맞다. `FcmPushSender.isConfigured()` 로 갈린다. |
| **리마인더 시점 저장** | PostgreSQL `integer[]` 한 컬럼(`notification_settings.reminder_offsets`) | 사용자당 한 행이라 별도 테이블보다 조회·수정이 단순하다. 값은 "일정 시작 N분 전"의 분 단위 정수. |
| **중복 발송 방지** | `notification_dispatches` 이력 테이블 + `(user_id, type, target_id, variant)` 유니크 제약 | 리마인더 배치는 5분마다 돌고 서버가 재시작될 수도 있다. "직전 실행 이후" 같은 시간 창 계산 대신 유니크 제약에 멱등을 맡긴다 — 서버가 잠깐 멈춰 실행을 걸러도 복구 후 한 번은 나가고 두 번은 안 나간다. `variant` 는 리마인더의 offset(분), 그 외 트리거는 0. |
| **알림 발화 방식** | Spring `ApplicationEventPublisher` + `@TransactionalEventListener(AFTER_COMMIT)` | 이 코드베이스 최초의 도메인 이벤트 도입. `ReservationService`·`SettlementService` 는 비관적 락(`SELECT … FOR UPDATE`)을 잡으므로, 발송(FCM HTTP)을 트랜잭션 안에서 하면 락을 쥔 채 외부 I/O 를 하게 된다(CLAUDE.md 금지). 커밋 후로 미뤄야 한다. `fallbackExecution=true` 로 트랜잭션 없이 발행돼도 실행되게 해 테스트를 단순화했다. |
| **AFTER_COMMIT 리스너 동기 실행** | 지금은 요청 스레드에서 동기 발송(FCM 타임아웃 5초로 상한). `@Async` 는 후속 과제 | `@EnableAsync` 를 지금 도입하면 트랜잭션·예외 처리 경로가 한 번에 두 개 늘어난다. 부하가 실측으로 문제되면 그때 붙인다. |
| **리마인더 배치 주기** | 5분마다(`0 */5 * * * *`). 상한 시점은 기본 24시간 전 | 5분 격자면 사용자가 지정한 "N분 전"과 최대 5분 오차. 충분하다. |
| **참석 독촉 대상 산정** | 현재 활성 밴드 멤버 − (ATTENDING/ABSENT 응답자) | 일정 생성 이후 합류한 멤버는 참석 행 자체가 없고 논리적으로 미응답이다. PENDING 행만 훑으면 그들을 놓친다(`AttendanceService.boardFor` 주석과 같은 취지). |
| **고아 PENDING 기준** | `app.media.orphan-age` 기본 `PT1H`. `created_at` 이 그보다 오래된 PENDING 행 | BUILD_PLAN "1시간 이상 PENDING". |
| **디바이스 토큰 유니크** | `token` 전역 유니크. 재등록 시 소유자만 갱신(upsert) | 기기 하나의 FCM 토큰이 계정 전환으로 다른 사용자에게 붙을 수 있다. |
| **레이트리밋** | 디바이스 토큰 등록 계정당 분당 30 (`app.ratelimit.device-token-per-user-per-min`) | 기존 `RedisRateLimiter` 재사용. |
| `created_at` | `notification_settings`·`device_tokens` 에도 `BaseTimeEntity` 로 자동 관리(도메인 모델엔 `updatedAt` 만) | Phase 5~8 과 동일 — 다른 테이블과 맞춘다. |

## 3. 무엇을 만들었나

### 3.1 데이터베이스 — `src/main/resources/db/migration/V9__notification.sql`

테이블 3개 신설 + 인덱스 1개 추가.

| 대상 | 내용 |
|---|---|
| `notification_settings` | `user_id`(PK=FK), `push_enabled`(기본 true), `reminder_offsets integer[]`(기본 `{60}`), `created_at`, `updated_at`. 배열 길이 ≤ 20 CHECK. |
| `device_tokens` | `id`, `user_id`(FK), `token`(전역 유니크), `platform`(IOS/ANDROID/WEB CHECK), `created_at`, `updated_at`. `user_id` 인덱스. |
| `notification_dispatches` | `id`, `user_id`, `type`, `target_id`, `variant`(기본 0), `created_at`. `(user_id,type,target_id,variant)` **유니크 = 멱등 키**. `created_at` 인덱스(이력 정리용). |
| `reservations` 인덱스 추가 | `ix_reservations_upcoming ON (start_at) WHERE status = 'CONFIRMED'` — 리마인더·독촉 배치가 "곧 시작하는 확정 일정"을 훑는 경로. |

### 3.2 알림 도메인 — `src/main/java/com/yeka/bandapp/notification/`

| 파일 | 역할 |
|---|---|
| `push/PushSender` (인터페이스) + `push/FcmPushSender` | 푸시 발송의 유일한 접점(`StorageClient`·`GeocodingClient` 와 같은 역할). 바이트를 나르지 않는다. `FcmPushSender` 는 키가 없으면 `firebaseMessaging` 을 만들지 않고 조용히 뜬다. 발송은 `sendEachForMulticast`(1회 500개)로 청크 처리하고, FCM 이 무효라고 응답한 토큰을 돌려준다. |
| `push/PushMessage`, `push/PushResult`, `push/TokenChunks` | 메시지·결과 값 객체, 500개 청크 분할 순수 함수. |
| `push/FcmProperties` | `app.fcm.*` 바인딩. `isConfigured()` = projectId + (JSON 또는 파일 경로). |
| `entity/NotificationSetting`, `DeviceToken`, `NotificationDispatch`, `DevicePlatform`, `NotificationType` | 엔티티·enum. `NotificationSetting.reminderOffsets` 는 `@JdbcTypeCode(SqlTypes.ARRAY) int[]`. |
| `repository/…` 3종 | 각 엔티티 저장소. 삭제·정리 쿼리에는 저장소에 직접 `@Transactional`(외부 I/O 를 트랜잭션 밖에 두려는 서비스가 짧은 트랜잭션만 쓰므로). |
| `NotificationProperties` | `app.notification.*` — zone, 기본 리마인더 시점, 시점 상한(분)·개수, 독촉 리드타임, 이력 보관일. cron 은 `@Scheduled` 가 직접 참조(관례). |
| `service/ReminderOffsets` | 리마인더 시점 목록의 검증·정규화(중복 제거+정렬, 범위·개수 위반 400). 순수 함수. |
| `service/NotificationMessages` | 트리거별 푸시 문구·data 페이로드(`type`,`bandId`,`reservationId`) 조립. 순수 함수. |
| `service/DeviceTokenService` | 토큰 upsert/해제, 계정 탈퇴 시 토큰·설정 삭제. 레이트리밋. 순수 DB 라 일반 `@Transactional`. |
| `service/NotificationSettingService` | 설정 조회(없으면 기본값 행 생성)·변경. 배치가 쓸 "푸시 끈 사용자"·"사용자별 시점" 조회. |
| `service/NotificationSender` | **발송 오케스트레이션 — `@Transactional` 금지**(FCM HTTP). 수신자 필터 → 멱등 이력 기록 → 토큰 모아 1회 발송 → 무효 토큰 삭제. 새로 발송 처리한 수신자 수를 반환. |
| `service/NotificationDispatchRecorder` | 이력 기록·무효 토큰 삭제만 담는 `REQUIRES_NEW` 트랜잭션 조각. 트리거 알림은 `AFTER_COMMIT` 리스너에서 불리는데, 그때는 방금 커밋된 트랜잭션의 동기화가 정리 중이라 일반(REQUIRED) 트랜잭션으로 시작한 쓰기가 커밋되지 않고 사라진다 — 새 트랜잭션을 강제한다. 유니크 경합(이미 발송)이 나머지를 막지 않도록 **수신자당 한 번씩** 호출한다. |
| `service/ReminderService` | 리마인더 발송 로직. `now` 기준 발송 시점이 도래한 (수신자 × 시점)을 모아 offset 별로 발송. |
| `service/AttendanceNudgeService` | 리드타임 안에 시작하는 확정 일정의 미응답자에게 독촉(일정당 1회). |
| `event/NotificationEvents` (record 모음) + `event/NotificationEventListener` | 도메인 이벤트와 `@TransactionalEventListener(AFTER_COMMIT)` 핸들러. 모든 핸들러를 try/catch 로 감싸 발송 실패가 커밋된 본 작업을 되돌리지 않게 한다. |
| `schedule/ReservationReminderJob` | 5분마다 `ReminderService.runOnce` + 오래된 이력 정리. |
| `schedule/AttendanceNudgeJob` | 매일 19:00 KST `AttendanceNudgeService.runOnce`. |
| `controller/NotificationController` | `POST/DELETE /api/v1/notifications/device-tokens`, `GET/PUT /api/v1/notifications/settings`. |

### 3.3 미디어 정리 배치 — `src/main/java/com/yeka/bandapp/board/`

| 파일 | 역할 |
|---|---|
| `MediaMaintenanceProperties` | `app.media.*` — zone, `orphanAge`. cron 은 `@Scheduled` 가 직접 참조. |
| `service/MediaMaintenanceService` | **`@Transactional` 금지**(R2 delete). `expireOverdue(now)` = 만료 READY 를 페이지 단위로 훑어 **R2 삭제 → 그다음 DB `EXPIRED`**. 삭제 실패 건은 READY 로 남아 다음 실행이 재시도. `cleanupOrphans(threshold)` = 오래된 PENDING 을 R2 best-effort 삭제 후 행 제거. |
| `repository/MediaAttachmentRepository` (추가) | `findExpiredReady`(부분 인덱스 `ix_media_attachments_expires` 와 조건 일치), `markExpired`(조건부 `READY→EXPIRED`), `findStalePending`(부분 인덱스 `ix_media_attachments_pending`). |
| `schedule/MediaExpirationJob` | 매일 04:15 KST. |
| `schedule/OrphanMediaCleanupJob` | 매시 10분. |

### 3.4 기존 코드 변경

- `reservation/service/ReservationService` — 생성/승인/거절/취소/수정(재승인 전환)에 `eventPublisher.publishEvent(...)` 한 줄씩. 수신자 목록은 이미 조회하던 것을 재사용.
- `settlement/service/SettlementService` — 생성·재계산에 `SettlementRequested` 발행(요청자 제외).
- `band/service/BandDirectoryService` — `leaderUserIds(bandId)` 추가(승인 요청 알림 수신자).
- `reservation/service/ReservationDirectoryService` — `upcomingConfirmed(from, to, limit)` + `UpcomingReservation` 레코드(알림 패키지가 `Reservation` 엔티티를 몰라도 되게).
- `reservation/service/AttendanceService` + `ReservationAttendanceRepository` — `respondedUserIds(reservationId)`(독촉 대상 산정).
- `user/service/UserAccountService` — 탈퇴 시 `deviceTokenService.deleteAllOf(userId)`.
- `common/exception/ErrorCode` — `INVALID_REMINDER_OFFSET`, `TOO_MANY_REMINDER_OFFSETS`, `DEVICE_TOKEN_NOT_FOUND`.
- `common/ratelimit/RateLimitProperties` — `deviceTokenPerUserPerMin`(기본 30).
- `application.yml` / `.env.example` / `docker-compose.yml` — `app.fcm.*`, `app.notification.*`, `app.media.*`, `FCM_*` 환경변수.
- `build.gradle.kts` — `firebase-admin:9.4.3`.

## 4. 어떻게 동작하나

### 4.1 이벤트 트리거 (새 일정·승인·정산 등)

1. `ReservationService.create` 등이 자기 트랜잭션 안에서 `NotificationEvents.*` 레코드를 발행한다.
2. 트랜잭션이 **커밋되면** `NotificationEventListener` 가 그 이벤트를 받는다(롤백되면 알림도 없다).
3. 리스너가 `NotificationSender.notify(type, targetId, variant, 수신자들, 메시지)` 를 부른다.
4. `notify` 는 ① 푸시를 끈 사용자를 뺀다 ② 각 수신자에 대해 `notification_dispatches` 에 행을 INSERT —
   유니크 제약에 걸리면(이미 보냄) 그 사람은 건너뛴다 ③ 남은 사람들의 디바이스 토큰을 모아 FCM 에 1회 발송
   ④ FCM 이 무효라고 한 토큰을 지운다.

### 4.2 리마인더 배치 (5분마다)

1. `now` 부터 "상한 시점(기본 24시간)" 안에 시작하는 **확정** 일정을 훑는다.
2. 일정마다 활성 멤버 각자의 리마인더 시점 배열을 읽는다(설정 행이 없으면 기본 `[60]`).
3. `일정 시작 − 시점`이 `now` 이하로 지났으면 그 (일정, 시점, 사용자)는 "발송 대상".
4. 시점(offset)별로 묶어 `notify(RESERVATION_REMINDER, 일정id, offset, 대상들, 메시지)`.
   `variant = offset` 이라 사용자가 여러 시점을 지정하면 각각 한 번씩 나간다.
5. 끝에 보관기한(기본 30일) 지난 `notification_dispatches` 행을 지운다.

### 4.3 참석 독촉 배치 (매일 19:00 KST)

리드타임(기본 24시간) 안에 시작하는 확정 일정마다, `활성 멤버 − 응답자`에게 독촉 1회(`variant = 0`).

### 4.4 미디어 만료 배치 (매일 04:15 KST)

`expires_at < now` 이고 `READY` 인 첨부를 오래된 것부터 페이지 단위로:
**R2 에서 객체 삭제 → 성공하면 DB 를 `EXPIRED` 로**. R2 삭제가 실패하면 그 건은 로그만 남기고 **READY 로
그대로 둔다** → 다음 날 실행이 다시 시도한다. 한 페이지에서 한 건도 진행이 없으면(전부 삭제 실패) 루프를 끊는다.

### 4.5 고아 PENDING 정리 배치 (매시 10분)

`created_at` 이 `orphan-age`(기본 1시간)보다 오래된 `PENDING` 첨부를 R2 best-effort 삭제 후 DB 행 삭제.
업로드 콜백이 끝내 안 온 첨부(대개 R2 에 객체도 없음)를 청소한다.

## 5. 직접 확인하는 법

### 사전 준비

```bash
cp .env.example .env      # JWT_SECRET 만 채우면 된다. FCM_* 는 비워도 전 구간 동작(발송만 no-op)
docker compose up --build
```

FCM 실제 발송까지 보려면 Firebase 콘솔 > 프로젝트 설정 > 서비스 계정 에서 비공개 키(JSON)를 받아
`.env` 에 `FCM_PROJECT_ID` 와 `FCM_CREDENTIALS_JSON`(JSON 문자열) 또는 `FCM_CREDENTIALS_PATH`(파일 경로)를 채운다.

### 흐름 (FCM 키 없이도 전부 확인 가능)

1. 두 계정 가입 → 밴드 생성·초대·참여.
2. `POST /api/v1/notifications/device-tokens` `{"token":"dev-1","platform":"ANDROID"}` 로 각각 토큰 등록.
3. `GET /api/v1/notifications/settings` → `{"pushEnabled":true,"reminderOffsets":[60]}` (첫 조회 시 자동 생성).
4. `PUT /api/v1/notifications/settings` `{"pushEnabled":true,"reminderOffsets":[10,60,10]}` →
   재조회하면 `[10,60]` (중복 제거·정렬).
5. 밴드 설정을 `APPROVAL_REQUIRED` 로 바꾸고 일반 멤버가 일정 등록 → 앱 로그에
   `NotificationEventListener` → `NotificationSender` 흐름과 (FCM 키가 있으면) 발송 로그.
   DB `notification_dispatches` 에 `type='RESERVATION_APPROVAL_REQUESTED'` 행이 밴드장 앞으로 생긴다.
6. 밴드장이 승인 → `RESERVATION_APPROVED` 행이 요청자 앞으로.
7. 같은 승인/등록을 반복해도 `notification_dispatches` 행이 늘지 않는다(멱등).
8. 미디어 만료 배치 수동 확인:
   ```sql
   -- 업로드까지 끝낸 첨부 하나의 만료일을 과거로 민다
   UPDATE media_attachments SET expires_at = now() - interval '1 day' WHERE id = <id>;
   ```
   `app.media.expire-cron` 을 `0 * * * * *`(매분) 등으로 잠깐 바꿔 재기동하면, 다음 분에 그 행이
   `EXPIRED` 로 바뀌고 R2 객체가 사라진다.

### 기대 결과 / 문제 해결

| 증상 | 원인·해결 |
|---|---|
| 알림 설정 API 는 되는데 푸시가 안 온다 | `FCM_*` 미설정. 앱 로그에 "FCM 자격증명이 없어 푸시 발송은 비활성" 이 뜬다. 키를 채우고 재기동. |
| `PUT /settings` 가 400 `INVALID_REMINDER_OFFSET` | 시점 값이 1 미만이거나 상한(`app.notification.max-reminder-offset-minutes`, 기본 1440) 초과. |
| `PUT /settings` 가 400 `TOO_MANY_REMINDER_OFFSETS` | 고유 시점 개수가 상한(기본 5) 초과. |
| 리마인더가 안 온다 | 일정이 확정(CONFIRMED) 상태인지, 시작이 "상한 시점" 안인지 확인. 취소·거절·대기 일정은 대상이 아니다. |
| 배치가 여러 인스턴스에서 중복 실행 | 현재 단일 VM 전제라 분산 락이 없다(`SchedulingConfig`). 다중 인스턴스로 가면 락이 필요. |

## 6. 실제 검증 기록

로컬 PC 는 Docker 데몬이 없어 Testcontainers 통합 테스트를 돌릴 수 없다(기존 Phase 와 동일 제약).
**순수 단위 테스트는 로컬에서 통과**를 확인했고, Testcontainers 통합 테스트는 CI 에서 검증한다.

로컬 통과(Docker 불필요, 18건):

| 테스트 | 건수 | 내용 |
|---|---|---|
| `notification.ReminderOffsetsTest` | 8 | 중복 제거·정렬, null→빈 배열, 범위·개수 위반, CSV 기본값 파싱·fallback |
| `notification.NotificationMessagesTest` | 5 | 트리거별 문구·data(`type`/`bandId`/`reservationId`), 승인/거절 문구 분기, data 불변 |
| `notification.push.TokenChunksTest` | 5 | 500 경계, 순서 보존, 빈 목록, 잘못된 chunk size |

`./gradlew compileJava compileTestJava build -x test` 로컬 성공(firebase-admin 의존성 해소, 전체 컴파일·jar 조립).
`board.NoFileStreamArchitectureTest` 로컬 통과 — 새 코드에 파일 스트림 마커가 없음을 재확인.

CI 에서 검증되는 통합 테스트:

| 테스트 | 시나리오 |
|---|---|
| `notification.NotificationSettingIntegrationTest` | 기본값 자동 생성, PUT 전체 교체·정규화, 빈 배열=리마인더 없음, 범위·개수 위반 400, 미인증 401 |
| `notification.DeviceTokenIntegrationTest` | 등록→해제, 재등록 시 소유자 이전(행 1개 유지), 남의 토큰 해제 404, 레이트리밋 429, 미인증 401 |
| `notification.NotificationTriggerIntegrationTest` | 확정 일정→등록자 뺀 전원, 승인대기→밴드장만, 승인/거절→요청자, **취소 2회 호출해도 1회 발송**, 푸시 끈 멤버 제외, 정산 생성→분담자(요청자 제외) |
| `notification.ReservationReminderJobTest` | 기본 시점 도래 시 1회 발송 + **2회 실행해도 1회(멱등)**, 지정 시점 각각 1회, 아직 안 도래한 시점 미발송, 취소 일정 제외 |
| `notification.AttendanceNudgeJobTest` | 응답자 제외 + 생성 후 합류한(행 없는) 멤버 포함, **멱등**, 리드타임 밖 일정 무시 |
| `board.MediaExpirationJobTest` | 만료 READY→R2 삭제+EXPIRED, **R2 삭제 실패 시 READY 유지·다음 실행에서 성공**, 다른 건은 계속 처리, 미만료 무영향, 멱등 |
| `board.OrphanMediaCleanupJobTest` | 오래된 PENDING 삭제(+R2), 최근 PENDING 유지, READY 는 고아 취급 안 함, 멱등 |

### 6.1 구현 후 자체 점검(보안·누락) 결과

| 발견 | 심각도 | 조치 |
|---|---|---|
| 발송(FCM HTTP)이 `ReservationService`/`SettlementService` 의 트랜잭션·비관적 락 안에서 일어나면 커넥션 풀이 마른다 | 높음 | `@TransactionalEventListener(AFTER_COMMIT)` 로 커밋 후 발송. `NotificationSender`·`ReminderService`·`AttendanceNudgeService`·`MediaMaintenanceService` 에 `@Transactional` 을 달지 않음(클래스 주석에 명시). |
| 리마인더 배치가 서버 재시작·중복 실행에서 같은 알림을 두 번 보낼 수 있다 | 높음 | `notification_dispatches` 유니크 제약을 멱등 키로. `NotificationDispatchRecorder` 가 `DataIntegrityViolationException` 을 잡아 "이미 보냄" 으로 처리. 배치 테스트마다 멱등 단언. |
| `@TransactionalEventListener(AFTER_COMMIT)` 안에서 일반 `@Transactional`(REQUIRED)로 시작한 DB 쓰기가 커밋되지 않고 사라진다 (CI 첫 실행에서 트리거 테스트 6건 실패로 발견) | 높음 | 이력 기록을 `NotificationDispatchRecorder`(`REQUIRES_NEW`)로 분리. FCM 호출은 여전히 트랜잭션 밖. 배치 경로(트랜잭션 없음)에서도 그냥 새 트랜잭션 하나가 열릴 뿐이라 무해. |
| 이력 기록을 `saveAndFlush` + `catch(DataIntegrityViolationException)` 로 하면, "이미 발송" 충돌의 flush 실패가 `REQUIRES_NEW` 트랜잭션을 rollback-only 로 만들어 커밋 시 `UnexpectedRollbackException` 이 난다 → **한 수신자의 "이미 발송됨"이 같은 `notify()` 의 나머지 수신자를 통째로 건너뛰게 한다**(리마인더에서 뒤늦게 도래한 offset, 독촉에서 나중에 합류한 멤버가 누락). 자체 검수에서 발견. | 높음 | `INSERT … ON CONFLICT DO NOTHING` 네이티브 쿼리로 교체 — 충돌이 예외 없이 흡수된다(0/1 반환). `NotificationSender.notify` 도 수신자별 try/catch 로 감싼다. 회귀 테스트 2건 추가(`a_newly_due_offset_still_fires_after_another_offset_was_already_sent`, `a_member_joining_between_runs_is_nudged_without_blocking_on_the_rest`). 같은 함정이 `NotificationSettingService.loadOrCreate`(동시 최초 접근)에도 있어 `insertDefaultsIfAbsent`(`ON CONFLICT (user_id) DO NOTHING`)로 교체. |
| FCM 서비스 계정 개인키를 환경변수(`FCM_CREDENTIALS_JSON`)로 넣으면 프로세스 목록·크래시 덤프로 새기 쉽다 | 낮음 | 파일 경로(`FCM_CREDENTIALS_PATH`)와 둘 다 있으면 **파일 경로를 우선**하도록 정렬. `.env.example` 에도 명시. |
| 미디어 만료 배치가 R2 삭제 순서를 잘못 잡으면(먼저 EXPIRED) R2 객체가 영구 고아가 된다 | 중간 | **R2 삭제 → 그다음 DB EXPIRED** 순서 고정. 삭제 실패 시 READY 유지 → 다음 실행 재시도. 테스트로 고정(`failNextDelete`). |
| FCM 키 미설정 시 앱이 안 뜨거나 알림 API 가 막히면 로컬·CI 개발이 어려워진다 | 중간 | `FcmPushSender` 가 키 없이 조용히 뜨고 발송만 no-op. 설정·토큰 API 는 정상. `IntegrationTestSupport` 는 `FakePushSender(@Primary)` 로 대체. |
| 발송 실패(FCM 오류)가 이미 커밋된 일정 등록·정산을 되돌리면 안 된다 | 중간 | 리스너 핸들러를 try/catch(RuntimeException)+log 로 감쌈. `NotificationSender.pushToDevices` 도 발송 예외를 삼킴(이력은 이미 남아 재발송 안 됨). |
| 남의 디바이스 토큰을 해제하거나 조회할 수 있으면 안 된다 | 중간 | 해제는 `deleteByUserIdAndToken`(본인 것만, 없으면 404). 설정 API 는 전부 토큰 주인 것만 다룸. |
| 계정 탈퇴 후에도 디바이스 토큰·알림 설정이 남으면 파기 요건 위반 | 낮음 | `UserAccountService.withdraw` 에서 `deviceTokenService.deleteAllOf(userId)`. |
| 리마인더 시점 배열이 무한정 커질 수 있다 | 낮음 | DTO `@Size(max=20)` + 서비스 `ReminderOffsets.normalize` 의 개수 상한(기본 5) + DB CHECK(≤20). |

## 7. 알려진 이슈 / 제약

- **로컬 통합 테스트 불가** — 이 PC 는 Docker 데몬이 없어 Testcontainers 를 못 돌린다. 통합 테스트는
  CI(우분투 러너)에서 검증됐다. 순수 단위 테스트와 컴파일·빌드는 로컬에서 확인했다.
- **`notification_settings.reminder_offsets` 의 `integer[]` 매핑** — Hibernate 6.6 `@JdbcTypeCode(SqlTypes.ARRAY)`
  + `ddl-auto: validate` 조합이 CI 통합 테스트 기동에서 문제없이 통과함을 확인했다(별도 어댑터 불필요).
- **배치 분산 락 없음** — 단일 VM 전제(`SchedulingConfig`). 다중 인스턴스로 확장하면 ShedLock 등이 필요하다.
- **`@Scheduled` 잡이 스레드 하나를 공유** — 스프링 기본 스케줄러는 단일 스레드다. 리마인더 잡이 FCM I/O
  (타임아웃 5초)를 일정마다 동기로 도므로, 확정 일정이 많으면 실행이 길어져 다른 잡(자정 파기 등)이 밀린다.
  `ThreadPoolTaskScheduler` 도입 또는 `@Async` 발송이 완화책(후속 과제).
- **리마인더·독촉 배치의 스캔 상한(`SCAN_LIMIT = 500`)** — 페이지네이션 없이 시작 시각 오름차순 500건만
  본다. 전 밴드 합산 미래 확정 일정이 창(24시간) 안에 500건을 넘으면 초과분 리마인더가 누락될 수 있다.
  현재 규모에선 문제없으나 커지면 `RecurringExtensionJob` 처럼 id 커서 페이징으로 바꿔야 한다.
- **디바이스 토큰 재등록 = 소유자 이전** — 같은 토큰 문자열을 다른 계정으로 등록하면 소유가 옮겨간다.
  기기 핸드오버를 지원하는 표준 FCM 방식이지만, 토큰이 유출되면 표적 알림 리다이렉트가 가능하다.
  클라이언트가 자기 현재 토큰만 보낸다는 전제다.
- **토큰 해제가 쿼리 파라미터(`DELETE ?token=`)** — 통합 테스트 도구가 본문 있는 DELETE 를 못 해서다.
  FCM 토큰이 접근 로그에 남을 수 있다(장기 비밀은 아니고 회전됨). 운영 클라이언트는 본문 DELETE 가 가능하니
  필요하면 계약을 바꿀 수 있다.
- **`notification_dispatches` 정리가 리마인더 잡에 묶여 있다** — 그 잡을 끄면 이력이 계속 쌓인다.
  탈퇴 시에도 dispatch 행은 즉시 지우지 않고 보관기한(30일) 뒤 정리된다(개인정보는 `user_id` + 대상 id 뿐).
- **알림 발송이 요청 스레드에서 동기** — AFTER_COMMIT 리스너가 FCM 왕복(타임아웃 5초)만큼 응답을 늦출 수
  있다. 부하가 실측으로 문제되면 `@Async` 를 붙인다(후속 과제).
- **인앱 알림함 없음** — `notification_dispatches` 는 멱등 키·이력일 뿐, "받은 알림 목록" 조회 API 는 이번
  범위가 아니다(BUILD_PLAN Phase 9 본문에 없음). 필요하면 이력 테이블을 그대로 활용해 추가할 수 있다.
- **이메일 발송 인프라·비밀번호 재설정** — `docs/BACKLOG.md` 가 "Phase 9 알림과 함께 설계" 로 미뤄 뒀으나
  BUILD_PLAN Phase 9 본문에 없어 이번 범위에서 제외했다. FCM 과 별개 채널이라 별도로 얹어야 한다.
- **카카오 unlink 재시도 큐** — `phase-01-auth.md` 가 "배치 인프라가 생기는 Phase 9" 로 예고했으나 BUILD_PLAN
  본문에 없어 제외.

## 8. 커밋 · CI 링크

- 브랜치: `phase-9-notification-batch`
- PR: [#30](https://github.com/Yekapark/bandApp/pull/30)
- CI: [run 33607252084](https://github.com/Yekapark/bandApp/actions/runs/33607252084) — ✅ 통과 (261 tests)
- 주요 커밋:
  - `chore(notification): FCM 의존성·설정 추가`
  - `feat(notification): V9 마이그레이션 + 엔티티 + FCM 어댑터`
  - `feat(notification): 발송 서비스·이벤트 트리거·리마인더/독촉 배치`
  - `feat(board): 미디어 만료·고아 PENDING 정리 배치`
  - `test(notification/board): 통합·단위 테스트`
  - `docs(progress): Phase 9 기록`
  - `fix(notification): AFTER_COMMIT 리스너의 이력 쓰기를 REQUIRES_NEW 로 분리` — CI 1차 실행에서
    트리거 테스트 6건 실패로 발견. `@TransactionalEventListener(AFTER_COMMIT)` 안의 일반 트랜잭션
    쓰기가 커밋되지 않던 문제.
  - `test(notification): 참석 독촉 테스트에서 셋업 알림 리셋` — CI 2차 실행에서 발견한 테스트 격리 결함.

## 9. 다음 Phase 예고

Phase 10 — 요금제(결제 어댑터 제외). `BandPlan`(FREE/PREMIUM), 미디어 `expiresAt` 계산을 밴드 플랜에 연결
(지금은 `MediaPolicy.freePlanExpiresAt` 로 30일 고정), 플랜 변경 시 기존 미디어 만료일 재계산,
`PaymentGateway` 인터페이스 + no-op 구현체. 실제 PG 연동은 제외.
