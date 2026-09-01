# Phase 6 — 참석 체크(RSVP) · 셋리스트

## 1. 한 줄 요약

일정마다 **밴드 멤버 전원의 참석 응답**을 관리한다. 일정을 만들 때 그 시점의 활성 멤버 전원이
`PENDING`(미응답)으로 생성되고, 이후 각자 **본인 것만** `ATTENDING`/`ABSENT`로 바꾼다. 일정 상세
조회에 참석 현황(멤버별)과 집계("참석 N / 전체 M")가 함께 실린다. **일정 생성 이후 밴드에 합류한
멤버도 참석 응답이 가능**하며(응답 시 참석 행이 새로 만들어진다), **타인의 참석 상태 변경은 403**이다.
더불어 일정별 **셋리스트**(곡명·아티스트·참고 링크·순서)의 추가·수정·삭제·재정렬을 제공한다.

## 2. 이 Phase의 목표 (`docs/BUILD_PLAN.md` 기준)

- 일정 생성 시 밴드 멤버 전원의 `ReservationAttendance`를 PENDING으로 생성
- 본인 참석 상태 변경 API (본인 것만 수정 가능)
- 일정 상세 조회 시 멤버별 참석 현황 및 집계(참석 N / 전체 M) 포함
- 셋리스트 CRUD — 곡명, 아티스트, 참고 링크, 순서

**완료 기준**: 일정 생성 이후 밴드에 합류한 멤버도 참석 응답이 가능하며, 타인의 참석 상태 변경이
403으로 차단되는 테스트가 통과한다.

### `BUILD_PLAN.md`에 없어 새로 정한 정책 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 참석 집계의 "전체 M" 기준 | 저장된 참석 행이 아니라 **현재 활성 밴드 멤버 수** | 완료 기준이 "생성 이후 합류한 멤버도"를 요구한다. 멤버 목록은 시점에 따라 바뀌므로 조회 때마다 지금의 멤버로 계산한다. 그 사이 탈퇴한 멤버는 빠진다 |
| 뒤늦게 합류한 멤버의 참석 행 | 참석 상태 변경 API가 **행이 없으면 만들어 upsert** | 일정 생성 시점엔 없던 멤버라 초기 PENDING 행이 없다. `(reservation_id, user_id)` 유니크 + `DataIntegrityViolationException` 캐치로 동시 최초 응답도 안전 (CLAUDE.md 규칙) |
| 본인 확인 방식 | `PUT .../attendances/{userId}` 경로에 대상 userId를 받고, 요청자와 다르면 403 `NOT_ATTENDANCE_OWNER` | 완료 기준의 "타인의 참석 상태 변경이 403"을 그대로 검증할 수 있는 형태 |
| 취소·거절된 일정에 대한 응답 | 409 `RESERVATION_NOT_EDITABLE`로 거부 | 죽은 일정에 RSVP는 의미가 없다. 기존 일정 수정과 같은 에러코드 재사용 |
| 셋리스트 편집 권한 | 밴드 멤버 **누구나** (등록자 제한 없음) | 셋리스트는 협업 산출물이다. BUILD_PLAN이 등록자 제한을 걸지 않았다 |
| 셋리스트 순서 | 추가 시 맨 뒤(`order_no` = 현재 최대 + 1). 순서 변경은 별도 **재정렬 API**가 1..N을 다시 매김 | `order_no`에 유니크를 걸지 않아(재정렬 중 일시적 충돌 회피) 곡 수정 API는 순서를 건드리지 않고 곡 정보만 바꾼다 |
| 정기 일정(Phase 5) 회차의 참석 행 | 회차 생성 시 **미리 만들지 않음** — 참석 현황·응답이 "현재 멤버 + upsert" 방식이라 회차도 자동으로 올바르게 동작한다 | 회차 벌크 생성/배치 경로에 밴드-멤버 조회를 끼우지 않기 위해서. 사용자에게 보이는 동작은 동일(모두 PENDING으로 시작) |

## 3. 무엇을 만들었나

### 3.1 데이터베이스 — `src/main/resources/db/migration/V6__attendance_setlist.sql`

테이블 2개 신설.

| 대상 | 내용 |
|---|---|
| `reservation_attendances` (신규) | `reservation_id`, `user_id`, `status`(ATTENDING/ABSENT/PENDING), `responded_at`(응답 시각, 미응답이면 NULL), `created_at` |
| CHECK | `status` 값 제한 |
| `ux_reservation_attendances_member` | `(reservation_id, user_id)` **UNIQUE** — 한 멤버가 한 일정에 응답 하나. 동시 최초 응답의 안전장치이기도 하다 |
| `ix_reservation_attendances_reservation` | `(reservation_id)` — 일정 상세의 참석 현황 조회 |
| `setlist_items` (신규) | `reservation_id`, `title`(필수, 200), `artist`(200), `reference_url`(2000), `order_no`(1부터), `created_at` |
| CHECK | `order_no > 0` |
| `ix_setlist_items_reservation` | `(reservation_id, order_no)` — 일정별 셋리스트를 순서대로 조회 |

> 참석 집계는 이 테이블의 행 수가 아니라 **현재 밴드 멤버**를 기준으로 계산하므로, 멤버가 나갔다고
> 참석 행을 지우지 않는다(행은 남고 집계에서만 빠진다).

### 3.2 참석 체크 — `src/main/java/com/yeka/bandapp/reservation/`

- **엔티티** `entity/ReservationAttendance.java` — `Reservation`과 같은 스타일(연관관계 없이 `Long` FK,
  정적 팩토리, 의미 있는 메서드). `pending(reservationId, userId)` / `respond(status, when)` — PENDING으로
  되돌리면 `respondedAt`도 지운다.
- **상태 enum** `entity/AttendanceStatus.java` — `ATTENDING / ABSENT / PENDING`.
- **저장소** `repository/ReservationAttendanceRepository.java` — `findByReservationId`(현황),
  `findByReservationIdAndUserId`(upsert).
- **서비스** `service/AttendanceService.java`
  - `createPendingFor(reservationId, members)` — 일정 생성 직후 `ReservationService`가 호출.
  - `respond(bandId, reservationId, targetUserId, callerUserId, status)` — 멤버십 → 본인 확인(아니면 403)
    → 일정 밴드 대조(404) → 활성 여부(409) → upsert → 갱신된 전체 현황 반환.
  - `getBoard(...)` / `boardFor(bandId, reservationId)` — **현재 활성 멤버 전원** + 각자의 저장된 응답
    (없으면 PENDING). `attendingCount` = ATTENDING 수, `memberCount` = 현재 활성 멤버 수.
- **컨트롤러** `controller/AttendanceController.java` — `GET`/`PUT /api/v1/bands/{bandId}/reservations/{reservationId}/attendances[/{userId}]`.

### 3.3 셋리스트 — `src/main/java/com/yeka/bandapp/reservation/`

- **엔티티** `entity/SetlistItem.java` — `create(...)` / `edit(title, artist, referenceUrl)` / `moveTo(orderNo)`.
- **저장소** `repository/SetlistItemRepository.java` — `findByReservationIdOrderByOrderNoAscIdAsc`,
  `findByIdAndReservationId`(일정 대조), `maxOrderNo`(다음 순서 번호).
- **서비스** `service/SetlistService.java` — `list` / `itemsFor`(일정 상세 내장용) / `add`(맨 뒤) /
  `update`(곡 정보만) / `delete` / `reorder`(itemIds가 현재 항목 전체와 정확히 일치해야 하며 1..N 재부여).
- **컨트롤러** `controller/SetlistController.java` — `GET`/`POST`, `PUT /reorder`, `PUT/DELETE /{itemId}`
  under `/api/v1/bands/{bandId}/reservations/{reservationId}/setlist`.

### 3.4 기존 코드 변경

| 파일 | 변경 |
|---|---|
| `reservation/service/ReservationService.java` | `create`가 저장 직후 `attendanceService.createPendingFor(...)` 호출. `get`이 `ReservationResponse` → **`ReservationDetailResponse`** 반환(참석 현황·셋리스트 포함) |
| `reservation/controller/ReservationController.java` | `GET /reservations/{id}` 응답 타입을 `ReservationDetailResponse`로 |
| `reservation/dto/ReservationDetailResponse.java` (신규) | 일정 필드를 그대로 펼치고(`id`, `status` … 목록 응답과 동일 위치) `attendance`, `setlist`를 더한 record |
| `band/service/BandDirectoryService.java` | `activeMembers(bandId)` 추가 — 현재 활성 멤버 요약(userId·name·role). `BandMemberRepository`·`UserDirectoryService` 주입 |
| `common/exception/ErrorCode.java` | `NOT_ATTENDANCE_OWNER`(403), `SETLIST_ITEM_NOT_FOUND`(404), `SETLIST_REORDER_MISMATCH`(400) |

> `GET /reservations/{id}` 응답은 기존 필드가 그대로 최상위에 남는다(`ReservationDetailResponse`가
> `ReservationResponse`의 필드를 펼쳐 담는다) — Phase 4·5의 기존 테스트·클라이언트가 깨지지 않는다.

## 4. 어떻게 동작하나

### 4.1 일정 생성 → 참석 행 생성

`POST /reservations` → `ReservationService.create` 트랜잭션 안에서 일정 저장 → 합주실 usageCount +1 →
`bandDirectory.activeMembers(bandId)`로 그 시점 활성 멤버를 모아 전원 `PENDING` 참석 행 생성.

### 4.2 참석 응답 (본인만)

`PUT /reservations/{id}/attendances/{userId}` body `{"status":"ATTENDING"}`:
1. 요청자가 그 밴드 활성 멤버인가? (아니면 403 `NOT_BAND_MEMBER`)
2. `{userId}`가 요청자 본인인가? (아니면 403 `NOT_ATTENDANCE_OWNER`)
3. 그 일정이 이 밴드 것인가? (아니면 404 `RESERVATION_NOT_FOUND`)
4. 일정이 살아 있는가(취소·거절 아님)? (아니면 409 `RESERVATION_NOT_EDITABLE`)
5. 참석 행 upsert — 없으면(뒤늦게 합류) 만들어서 갱신. 동시 최초 응답은 유니크 제약에 걸리면
   다시 읽어 갱신.
6. 갱신된 전체 참석 현황 반환.

### 4.3 참석 현황·집계

`GET /reservations/{id}/attendances` 또는 `GET /reservations/{id}`(상세)에 포함:
`bandDirectory.activeMembers(bandId)`(현재 멤버) × 저장된 응답 행을 합쳐, 행이 없는 멤버는 `PENDING`.
`attendingCount` = ATTENDING 수 / `memberCount` = 현재 활성 멤버 수.

### 4.4 셋리스트

추가는 `order_no = max + 1`로 맨 뒤. 재정렬은 `itemIds`(그 일정의 전 항목을 원하는 순서로)를 받아
1..N을 다시 매긴다 — 빠지거나 남거나 중복이면 400 `SETLIST_REORDER_MISMATCH`. 곡 수정은 순서를
건드리지 않는다.

## 5. 직접 확인하는 법

### 사전 준비
`docker compose up`(app + postgres + redis). 아래는 두 사용자(리더 A, 멤버 B)로 진행한다.

### 흐름
1. A로 가입·로그인 → 밴드 생성 → 합주실 등록 → `POST /api/v1/bands/{bandId}/reservations`로 일정 생성.
2. `GET /api/v1/bands/{bandId}/reservations/{id}/attendances` → `memberCount:1`, `attendingCount:0`,
   A가 `PENDING`.
3. B로 가입 → A가 초대코드 발급 → B가 참여.
4. `GET .../attendances` → `memberCount:2`, B도 목록에 `PENDING`으로 보임(= **생성 이후 합류한 멤버**).
5. B 토큰으로 `PUT .../attendances/{B의 userId}` body `{"status":"ATTENDING"}` → 200,
   응답의 `attendingCount:1`.
6. B 토큰으로 `PUT .../attendances/{A의 userId}` → **403 `NOT_ATTENDANCE_OWNER`** (타인 변경 차단).
7. `GET /api/v1/bands/{bandId}/reservations/{id}` → 최상위에 일정 필드, `attendance`에 현황,
   `setlist`에 곡 목록.
8. 셋리스트: `POST .../setlist` `{"title":"곡A"}` 두 번 → `order_no` 1, 2 →
   `PUT .../setlist/reorder` `{"itemIds":[두번째,첫번째]}` → 순서 뒤집힘.

### 기대 결과 / 문제 해결
- 5에서 403이면 path의 userId가 본인인지 확인(`GET /api/v1/users/me`의 `id`).
- 4에서 B가 안 보이면 참여가 실패한 것(초대코드 만료/소진).
- 재정렬이 400이면 `itemIds`에 그 일정의 **모든** 항목 id가 정확히 한 번씩 들어갔는지 확인.

## 6. 실제 검증 기록

- `./gradlew compileJava compileTestJava` — **성공**.
- 순수 단위 테스트(`OccurrenceGeneratorTest`, `InviteCodeGeneratorTest`, `JwtTokenProviderTest`) — **통과**.
- **통합 테스트는 이 로컬 환경에서 실행하지 못했다.** Testcontainers 1.20.4(번들 docker-java)가
  로컬 Docker Desktop 4.88 / Engine 29(API 1.55)의 `/info` 호출에 `HTTP 400`을 받아
  "Could not find a valid Docker environment"로 중단된다. **Phase 0~5의 모든 기존 통합 테스트도
  동일하게 실패**하므로 Phase 6가 유발한 문제가 아니라 로컬 Docker/Testcontainers 버전 불일치다.
  검증은 **CI(GitHub Actions, 자체 Docker)** 에서 이뤄진다.
- 신규 통합 테스트 `src/test/java/com/yeka/bandapp/reservation/AttendanceSetlistIntegrationTest.java` —
  완료 기준 2건(① 생성 이후 합류 멤버 응답 가능, ② 타인 변경 403) + 초기 PENDING 생성, 상세 응답 내장,
  취소 일정 응답 거부, 타 밴드 격리, 셋리스트 CRUD·재정렬·권한.
- **CI 결과: `./gradlew build` (전체 테스트 포함) BUILD SUCCESSFUL** — PR #25,
  [run 33515673906](https://github.com/Yekapark/bandApp/actions/runs/33515673906). Phase 0~5 기존
  테스트도 함께 통과해 `GET /reservations/{id}` 응답 타입 변경(`ReservationDetailResponse`)의 회귀 없음이 확인됐다.

## 7. 알려진 이슈 / 제약

- 정기 일정(Phase 5) 회차는 참석 행을 미리 만들지 않는다(§2 표 참조). 사용자에게 보이는 동작은
  단발 일정과 같다(모두 PENDING). 필요하면 회차 벌크 생성 경로에 `createPendingFor`를 추가하면 된다.
- `respondedAt`은 서버 시각(`Instant.now()`)이며 클라이언트 시각을 신뢰하지 않는다.
- 셋리스트 삭제 후 `order_no`에 빈 번호가 생길 수 있다(예: 1,3). 정렬에는 영향 없고, 연속 번호가
  필요하면 재정렬 API를 호출한다.
- "참석 미응답 독촉"(BUILD_PLAN Phase 9 알림 트리거)은 이 Phase 범위 밖이다 — 여기서는 상태만 관리한다.

## 8. 커밋 · CI 링크

- 브랜치: `phase-6-attendance-setlist`
- PR: [#25](https://github.com/Yekapark/bandApp/pull/25)
- CI: [run 33515673906](https://github.com/Yekapark/bandApp/actions/runs/33515673906) — ✅ BUILD SUCCESSFUL

## 9. 다음 Phase 예고

Phase 7 — 정산(N빵). 일정 총비용을 `splitType`(EQUAL/ATTENDEES_ONLY)에 따라 `SettlementShare`로
분배. `ATTENDEES_ONLY`는 이 Phase에서 만든 `ReservationAttendance`가 `ATTENDING`인 멤버만 대상으로
한다. 나누어떨어지지 않는 나머지 처리 규칙, 참석자 변경 시 재계산 API, 본인 납부 체크.
