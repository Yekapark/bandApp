# Phase 4 — 일정 등록 (Reservation)

## 1. 한 줄 요약

밴드가 "이미 전화·카톡으로 잡아 둔 합주 예약"을 앱에 기록하는 기능을 붙였다. 밴드 설정
(`reservationPermission`)에 따라 등록 즉시 확정되거나(LEADER_ONLY·ANYONE) 밴드장 승인을 기다리며
(APPROVAL_REQUIRED), 캘린더용 기간 조회·수정·취소를 지원한다. **시간대가 겹치는 일정도 그대로
저장하고, 겹침은 등록/수정 응답에 경고(`overlaps`)로만 싣는다** — 이 앱은 예약을 대행하지 않으므로
겹침을 이유로 거부하지 않는다. 일정 등록 시 해당 합주실의 `usageCount`가 오르고, 취소·거절 시 내린다.

## 2. 이 Phase의 목표 (`docs/BUILD_PLAN.md` 기준)

- 일정 등록 — `Band.reservationPermission`에 따른 권한 분기 및 초기 status 결정
- 밴드장의 승인/거절 API (`APPROVAL_REQUIRED` 모드)
- 일정 수정/취소
- 기간별 일정 목록 조회 (캘린더용)
- 일정 등록 시 해당 Room의 `usageCount` 증가
- **겹침 경고** — 등록/수정 응답에 같은 밴드의 겹치는 일정 목록을 포함하되, 저장은 정상 수행하고
  이를 이유로 요청을 거부하지 않는다

**완료 기준**: 세 가지 권한 모드가 각각 의도대로 동작하고, 시간대가 겹치는 일정을 등록해도 정상
저장되면서 응답에 겹침 정보가 담기는 테스트가 통과한다.

### `BUILD_PLAN.md`에 없어 지시자 승인을 받은 정책 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 일정 수정·취소 권한 | **등록자 본인 + 밴드장** (승인/거절은 밴드장 전용) | 자기가 올린 기록은 스스로 고치되, 밴드장은 밴드 전체 일정을 관리 |
| APPROVAL_REQUIRED에서 확정된 일정 수정 | **시간 또는 합주실이 바뀌면 다시 PENDING**, 비고·비용만 바뀌면 CONFIRMED 유지 | 승인의 의미가 "이 시간, 이 장소"라서 핵심이 바뀌면 재승인이 맞다 |
| 합주실 `usageCount` | **등록 시 +1, 취소·거절 시 −1**, 수정으로 합주실이 바뀌면 이전 −1 / 새 +1 | 목록 정렬이 "실제로 자주 쓰는 곳"을 반영하도록 |
| `recurring_rule_id` | V4에 **nullable 컬럼만** 만들고 FK·테이블은 Phase 5로. Phase 4 코드는 항상 null | 엔티티를 Phase 5에서 다시 고치지 않으려고 컬럼만 미리 확보 |

## 3. 무엇을 만들었나

### 3.1 데이터베이스 — `src/main/resources/db/migration/V4__reservation.sql`

테이블 1개.

| 테이블 | 역할 | 핵심 |
|---|---|---|
| `reservations` | 밴드가 앱에 기록한 합주 일정. 합주실의 실제 예약 상태가 아니라 "밴드 내부 일정으로서의 등록 상태" | 아래 인덱스 2개, CHECK 2개 |

- `status VARCHAR(20)` — `PENDING / CONFIRMED / CANCELLED / REJECTED` (CHECK 제약).
- `start_at`, `end_at TIMESTAMPTZ NOT NULL` — `ck_reservations_period CHECK (end_at > start_at)`로
  **시간 순서만** 강제한다. 서로 다른 일정끼리의 겹침은 **일부러 막지 않는다** — `EXCLUDE`
  (`tstzrange &&`)나 유니크 제약을 두지 않은 것이 의도다(BUILD_PLAN 2장 2번, 파일 상단 주석 참조).
- `room_id BIGINT NOT NULL REFERENCES rooms(id)` — `rooms`는 소프트 삭제라 삭제된 합주실을
  참조하는 과거 일정도 계속 유효하다.
- `cost INT` nullable — 참고용 메모 성격(정산 Phase 7의 입력이 아니다).
- `note VARCHAR(500)` nullable — 외부 예약 방법 자유 기재("카톡 예약 완료, 예약자 홍길동").
- `recurring_rule_id BIGINT` nullable — **Phase 4에서는 항상 NULL.** `recurring_rules` 테이블과 FK는
  Phase 5 마이그레이션에서 `ALTER`로 추가한다.

인덱스:

- `ix_reservations_band_period` — `(band_id, start_at, end_at)`: 캘린더 기간 조회·겹침 검색.
- `ix_reservations_room` — `(room_id)`: 합주실 역참조(사용 집계·삭제 영향 확인).

### 3.2 일정 도메인 — `src/main/java/com/yeka/bandapp/reservation/`

- **엔티티** `entity/Reservation.java` — `Room`/`Band`와 같은 스타일(연관관계 매핑 없이 `Long` FK,
  정적 팩토리, 의미 있는 상태 변경 메서드). `BaseTimeEntity` 상속으로 `createdAt`을 얻는다.
  - `Reservation.create(bandId, roomId, requestedBy, status, startAt, endAt, cost, note)` — 초기
    `status`는 호출 측이 밴드 권한 모드로 정해서 넘긴다
  - 상태 전이: `approve()` / `reject()` / `cancel()` / `revertToPending()`.
    `cancel()`은 **이미 CANCELLED면 `false`를 반환**해 호출 측이 `usageCount` 감소를 멱등하게 처리하게 한다
  - `reschedule(roomId, startAt, endAt)` / `changeDetails(cost, note)` — PUT 전체 교체의 두 조각
  - `belongsTo(bandId)` / `isRequestedBy(userId)` / `isActive()`
- **상태 enum** `entity/ReservationStatus.java` — `isActive()`(= PENDING 또는 CONFIRMED) 헬퍼 포함.
  javadoc에 "합주실의 실제 예약 상태가 아니다"를 명시.
- **저장소** `repository/ReservationRepository.java`
  - `findByIdAndBandId(id, bandId)` — 상세·수정·승인에서 밴드 교차 접근 차단
  - `findByBandIdAndStatusInAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(...)` — 캘린더.
    구간 겹침 조건이 `startAt < to AND endAt > from`이라 파라미터가 `(to, from)` 순서로 들어간다.
    호출 측이 `statuses`로 활성만 볼지 전부 볼지 정한다
  - `findOverlapping(bandId, startAt, endAt, excludeId)` (`@Query`) — **겹침 경고 전용.**
    살아 있는(PENDING·CONFIRMED) 일정 중 **반열림 구간**(`startAt < :endAt AND endAt > :startAt`)이
    겹치는 것. 수정 시 자기 자신은 `excludeId`로 제외(신규 등록은 존재할 수 없는 값을 넘김).
    **이 결과는 거부에 쓰지 않는다.**
- **서비스** `service/ReservationService.java` — 아래 4장.
- **컨트롤러** `controller/ReservationController.java` — `@RequestMapping("/api/v1/bands/{bandId}/reservations")`.
  수정은 `PATCH` 미지원 클라이언트 대비 `PUT`(전체 교체).

### 3.3 도메인 간 참조 창구 (신규)

코딩 컨벤션상 "도메인 간 참조는 저장소가 아니라 서비스 레이어를 통한다". 일정 도메인이 밴드·합주실
정보를 읽어야 해서, `UserDirectoryService`와 같은 패턴의 창구를 둘 만들었다.

- `band/service/BandDirectoryService.java` — `reservationPermissionOf(bandId)` 하나. 등록 직후
  status와 재승인 여부가 이 값으로 갈린다. (멤버십·역할 검증은 기존 `BandAccessGuard`가 계속 담당.)
- `room/service/RoomDirectoryService.java`
  - `requireActiveRoom(bandId, roomId)` → `RoomBrief(id, name)` — 다른 밴드의 `roomId`거나 삭제된
    합주실이면 `ROOM_NOT_FOUND` (존재를 알리지 않는다, `RoomService.room`과 같은 방침)
  - `increaseUsage(roomId)` / `decreaseUsage(roomId)` — 아래 3.4
  - `namesOf(roomIds)` — 겹침 경고·목록 응답에 합주실 이름을 싣기 위해. 소프트 삭제된 방도 포함

### 3.4 합주실 `usageCount` 증감 — `room/repository/RoomRepository.java`에 2개 추가

```java
@Modifying @Query("update Room r set r.usageCount = r.usageCount + 1 where r.id = :id")
int increaseUsageCount(long id);

// 어떤 경로로도 0 밑으로 내려가지 않도록 조건을 건다.
@Modifying @Query("update Room r set r.usageCount = r.usageCount - 1 where r.id = :id and r.usageCount > 0")
int decreaseUsageCount(long id);
```

원자 UPDATE다 — 엔티티 read-modify-write는 동시 등록/취소에서 갱신이 유실된다
(`updateEditableFields`와 같은 계열). 단 이 둘은 **`clearAutomatically`를 쓰지 않는다**:
`ReservationService`가 같은 트랜잭션에서 `Reservation` 상태 전이를 dirty로 들고 있어, 영속성
컨텍스트를 비우면 아직 flush 안 된 그 변경이 사라지기 때문이다(개발 중 실제로 이 증상을 겪고 고쳤다 —
7장 참조).

`usageCount`가 바뀌는 지점:

| 동작 | 효과 |
|---|---|
| 등록 | 새 방 **+1** |
| 승인(approve) | 변화 없음 (등록 때 이미 셌다) |
| 거절(reject) | **−1** |
| 취소(cancel) | **−1** (이미 CANCELLED면 아무 일도 안 함 — 두 번 깎이지 않는다) |
| 수정 — 합주실 변경 | 이전 방 **−1**, 새 방 **+1** |
| 수정 — 그 외 | 변화 없음 |

### 3.5 공통 — `common/exception/ErrorCode.java`

Phase 4 코드 추가. **겹침은 예외가 아니므로 에러코드가 없다**(경고만 하고 등록은 성공).

| 코드 | HTTP | 언제 |
|---|---|---|
| `RESERVATION_NOT_FOUND` | 404 | 없는 일정 / 타 밴드 일정을 자기 밴드 경로로 조회 |
| `INVALID_RESERVATION_PERIOD` | 400 | `endAt <= startAt`, 또는 캘린더 조회에서 `to <= from` |
| `NOT_RESERVATION_OWNER` | 403 | 등록자도 밴드장도 아닌 사람이 수정·취소 시도 |
| `RESERVATION_NOT_PENDING` | 409 | 대기 상태가 아닌 일정을 승인/거절 |
| `RESERVATION_NOT_EDITABLE` | 409 | 취소·거절된 일정을 수정 |

`LEADER_ONLY` 밴드에서 일반 멤버가 등록하면 기존 `NOT_BAND_LEADER`(403)를 재사용한다.

### 3.6 API 목록

인증 필요(Bearer). 모든 엔드포인트가 밴드 멤버십을 검증한다.

| 메서드 · 경로 | 설명 | 권한 |
|---|---|---|
| `POST /api/v1/bands/{bandId}/reservations` | 일정 등록 (→ 201, `{reservation, overlaps}`) | 밴드 멤버 (LEADER_ONLY면 밴드장) |
| `GET /api/v1/bands/{bandId}/reservations?from=&to=&includeInactive=` | 캘린더 기간 조회 | 밴드 멤버 |
| `GET /api/v1/bands/{bandId}/reservations/{id}` | 일정 상세 | 밴드 멤버 |
| `PUT /api/v1/bands/{bandId}/reservations/{id}` | 일정 수정 (전체 교체, → `{reservation, overlaps}`) | 등록자 본인 또는 밴드장 |
| `POST /api/v1/bands/{bandId}/reservations/{id}/approve` | 승인 (PENDING → CONFIRMED) | 밴드장 |
| `POST /api/v1/bands/{bandId}/reservations/{id}/reject` | 거절 (PENDING → REJECTED) | 밴드장 |
| `DELETE /api/v1/bands/{bandId}/reservations/{id}` | 취소 (→ 204, status만 CANCELLED) | 등록자 본인 또는 밴드장 |

등록/수정 요청 본문: `{"roomId": 필수, "startAt": 필수(ISO-8601), "endAt": 필수, "cost": 선택, "note": 선택}`.
`from`/`to`는 `2026-09-10T10:00:00Z` 같은 ISO-8601 문자열.

## 4. 어떻게 동작하나

### 권한 모드에 따른 초기 status (완료 기준 ①)

등록 흐름: **① 멤버십 검증 → ② 기간 검증(`endAt > startAt`) → ③ 합주실 검증 → ④ 밴드 권한 모드로
초기 status 결정 → ⑤ 저장 → ⑥ 합주실 `usageCount` +1 → ⑦ 겹침 목록을 응답에 첨부.**

④가 핵심이다.

| `reservationPermission` | 누가 등록 가능 | 등록 직후 status |
|---|---|---|
| `LEADER_ONLY` (기본) | 밴드장만 (그 외 403 `NOT_BAND_LEADER`) | `CONFIRMED` |
| `ANYONE` | 활성 멤버 누구나 | `CONFIRMED` |
| `APPROVAL_REQUIRED` | 활성 멤버 누구나 | `PENDING` → 밴드장 `approve` 시 `CONFIRMED`, `reject` 시 `REJECTED` |

### 겹쳐도 저장된다 — 이건 버그가 아니다 (완료 기준 ②)

이 앱은 합주실 예약을 대행하지 않는다. 실제로 같은 시간에 두 곳을 잡았거나 장소를 옮긴 경우도
**있는 그대로 기록**될 수 있어야 한다. 그래서 `create`/`update`는:

1. 저장을 **먼저, 정상적으로** 끝낸다 (201 / 200).
2. 그다음 `findOverlapping`으로 같은 밴드의 살아 있는 일정 중 시간대가 겹치는 것을 찾아
   응답의 `overlaps` 배열에 담는다.

`overlaps`가 비어 있지 않아도 요청은 성공이다. 클라이언트는 이 목록을 사용자에게 보여 주고
"그대로 두겠다 / 고치겠다"를 **저장이 끝난 상태에서** 고르게 하면 된다.

겹침 판정은 **반열림 구간** `A.start < B.end AND A.end > B.start`이다. 앞 일정의 종료 시각과 뒤
일정의 시작 시각이 정확히 같으면(10–13시, 13–16시) 겹치지 않는다. 취소·거절된 일정은 겹침
대상에서 빠진다.

### 수정과 재승인

`PUT`은 전체 교체다. 등록자 본인 또는 밴드장만 호출할 수 있고(그 외 403 `NOT_RESERVATION_OWNER`),
취소·거절된 일정은 수정할 수 없다(409 `RESERVATION_NOT_EDITABLE`).

`APPROVAL_REQUIRED` 밴드에서 **이미 확정된** 일정의 **시간 또는 합주실**이 바뀌면 다시
`PENDING`으로 돌아간다. 비고·비용만 바뀌면 `CONFIRMED`를 유지한다. 합주실이 바뀌면 이전 방
`usageCount` −1, 새 방 +1.

### 취소는 멱등하다

`DELETE`는 행을 지우지 않고 `status`를 `CANCELLED`로만 바꾼다(과거 기록·정산이 계속 참조). 이미
취소된 일정에 다시 호출해도 204이고, **합주실 `usageCount`는 한 번만 깎인다** — 엔티티 `cancel()`이
"이번 호출에서 실제로 바뀌었는가"를 알려 주고, 서비스는 그 결과에 감소를 묶는다. 이미 거절된
일정은 취소 대상이 아니다(409).

### 캘린더 조회

`from`~`to` 구간과 조금이라도 겹치는 일정을 `startAt` 오름차순으로 준다. `from`·`to`는 필수이고
`to > from`이어야 한다(아니면 400). 기본은 취소·거절 건을 제외하며 `includeInactive=true`면 전부 포함한다.

## 5. 직접 확인하는 법

### 사전 준비

Phase 2·3과 동일. `.env`에 `JWT_SECRET`(32자 이상)이 있어야 앱이 뜬다. Docker Desktop 필요.
`NAVER_MAP_CLIENT_ID`/`SECRET`은 비워 둬도 된다(합주실만 좌표 없이 등록됨, 일정과는 무관).

### 방법 A — 전체 스택 실행 후 스모크 스크립트 (권장)

```bash
cd band
docker compose up --build -d
curl -s http://localhost:8080/actuator/health                        # {"status":"UP"}
docker compose exec postgres psql -U bandapp -d bandapp -c '\d reservations'   # CHECK 2개, 인덱스 2개, FK 3개
```

리포지토리에 `scripts/` 같은 곳에 넣지 않은 1회용 확인 스크립트는 아래 흐름을 그대로 따르면 된다
(개발 중 `python`으로 돌린 스모크 테스트와 동일한 시나리오):

```
가입(리더/멤버) → 밴드 생성 → 멤버 초대·참여 → 합주실 등록
# LEADER_ONLY(기본)
멤버가 일정 등록            → 403 NOT_BAND_LEADER
밴드장이 일정 등록          → 201, status=CONFIRMED, overlaps=[]
# 겹침
같은 방 12–15시 등록        → 201, overlaps=[앞 일정]        ← 완료 기준 ②
같은 방 15–18시 등록        → 201, overlaps=[]  (반열림)
# usageCount
GET rooms/{id}             → usageCount = 등록 수
일정 취소(DELETE) 2회       → 둘 다 204, usageCount는 1만 감소  (멱등)
# APPROVAL_REQUIRED
PUT settings APPROVAL_REQUIRED
멤버가 일정 등록            → 201, status=PENDING
멤버가 approve             → 403
밴드장이 approve           → 200, status=CONFIRMED
밴드장이 다시 approve      → 409 RESERVATION_NOT_PENDING
확정 일정 메모만 수정       → 200, status=CONFIRMED
확정 일정 시간 수정         → 200, status=PENDING            ← 재승인
# 기간·검증
GET reservations?from=..&to=..   → 범위 밖 일정 제외
endAt <= startAt 로 등록          → 400 INVALID_RESERVATION_PERIOD
```

정리: `docker compose down -v`

### 방법 B — Swagger UI

`http://localhost:8080/swagger-ui.html` → **"7. 일정"** 태그. 위 흐름을 클릭으로 재현할 수 있다.
`POST .../reservations` 응답 본문에서 `data.reservation.status`와 `data.overlaps`를 확인한다.

### 방법 C — 자동 테스트

`./gradlew test`. 이 개발 PC에서는 Testcontainers가 안 떠서(메모리: 로컬 Testcontainers/Docker 이슈)
`main` 대상 PR의 CI에서 최종 pass/fail이 나온다. 컨테이너 없이 로컬에서 가능한 것:

```bash
./gradlew compileJava compileTestJava
```

### 문제 해결

- **일정 등록이 `INVALID_INPUT`**: 요청 본문의 `startAt`/`endAt`이 ISO-8601인지 확인
  (`2026-09-10T10:00:00Z`). `roomId`가 숫자인지도.
- **`ROOM_NOT_FOUND`인데 방은 있다**: 그 방이 **이 밴드** 것인지, 삭제되지 않았는지 확인.
  경로의 `bandId`와 방의 소유 밴드가 다르면 일부러 404를 준다.
- **취소했는데 `usageCount`가 안 줄었다 / 너무 많이 줄었다**: 최신 이미지인지 확인
  (`docker compose up --build -d`). 개발 중 이 버그가 있었고 3.4에 적힌 대로 고쳤다.
- **`ddl-auto validate` 실패로 기동 불가**: `V4__reservation.sql`과 `Reservation` 엔티티 매핑
  불일치. 마이그레이션을 고친다(엔티티 아님).

## 6. 실제 검증 기록

### 6.1 컴파일 (2026-09-01, 개발 PC)

`./gradlew build -x test` 통과. `compileJava` / `compileTestJava` 통과.

### 6.2 `docker compose` 전체 스택 수동 검증 (2026-09-01, 개발 PC)

`docker compose up --build -d` 후 `/actuator/health` = `UP`. `flyway_schema_history`에
`4 | reservation | t`. `\d reservations`로 CHECK 2개(`ck_reservations_period`, `ck_reservations_status`),
인덱스 2개(`ix_reservations_band_period`, `ix_reservations_room`), FK 3개 확인.

`python` 스모크 스크립트(22개 assertion) — 전부 `PASS`:

| 검증 | 결과 |
|---|---|
| **LEADER_ONLY — 멤버 등록** | **403 `NOT_BAND_LEADER`** ← 완료 기준 ① |
| **LEADER_ONLY — 밴드장 등록** | **201, `status=CONFIRMED`, `overlaps=[]`** ← 완료 기준 ① |
| **겹치는 시간대 등록 (12–15시)** | **201, `overlaps=[앞 일정]`** ← 완료 기준 ② |
| **인접(15–18시) 등록** | **201, `overlaps=[]`** (반열림) |
| 3건 등록 후 합주실 `usageCount` | 3 |
| 일정 취소 2회 | 둘 다 204, `usageCount` 3 → **2** (멱등) |
| **APPROVAL_REQUIRED — 멤버 등록** | **201, `status=PENDING`** ← 완료 기준 ① |
| 멤버가 approve | 403 |
| 밴드장이 approve | 200, `status=CONFIRMED` |
| 확정 일정 재-approve | 409 `RESERVATION_NOT_PENDING` |
| 확정 일정 메모만 수정 | 200, `status=CONFIRMED` |
| 확정 일정 시간 수정 | 200, `status=PENDING` (재승인) |
| 캘린더 기간 조회 (범위 안/밖) | 안=2건, 밖=0건 |
| `endAt <= startAt` 등록 | 400 `INVALID_RESERVATION_PERIOD` |

동시성·타 밴드 격리·거절 시 `usageCount` 원복·`includeInactive`는 통합 테스트에서 CI로 검증한다.

### 6.3 CI — 자동 테스트 (2026-09-01, PR #22)

브랜치 `phase-4-reservation` → `main` [PR #22](https://github.com/Yekapark/bandApp/pull/22).
GitHub Actions `build` 잡: `./gradlew build --no-daemon` → `Build & test` 통과, `build in 1m18s`.
[actions/runs/33488006395](https://github.com/Yekapark/bandApp/actions/runs/33488006395)

테스트 클래스:
- `reservation/ReservationIntegrationTest` — 완료 기준(권한 3모드·겹침 저장/경고) + 반열림 경계,
  취소된 일정 제외, `usageCount` 증감·취소 멱등·합주실 이동, 수정 권한, 재승인 조건, 취소 후 수정 409,
  기간 검증, 캘린더 범위·`includeInactive`, 타 밴드 격리(roomId·reservationId·목록).
  §8.1 후속: 동시 취소/거절이 `usageCount`를 한 번만 깎음, 잘못된 파라미터 400, 400일 초과 조회 400
- `reservation/ReservationApiSupport` — 일정 픽스처 헬퍼. `RoomApiSupport`를 `public`으로 올려 재사용

## 7. 알려진 이슈 / 제약

- **개발 중 실제로 겪어 고친 버그**: `increaseUsageCount`/`decreaseUsageCount`에 처음
  `@Modifying(clearAutomatically = true)`를 달았더니, 취소 트랜잭션에서 아직 flush되지 않은
  `Reservation` 상태 전이(CONFIRMED→CANCELLED)가 컨텍스트 clear로 사라져 **일정은 CONFIRMED로
  남고 `usageCount`만 계속 깎이는** 증상이 났다. 이 두 쿼리 뒤에는 Room을 다시 읽지 않으므로
  `clearAutomatically`를 뺐다(`updateEditableFields`는 뒤에서 Room을 재조회해서 유지). 스모크
  스크립트가 이걸 잡았다.
- **겹침 검사는 "같은 밴드"만 본다.** 다른 밴드가 같은 물리적 합주실을 각자 등록한 경우, 그 방에서의
  타 밴드 일정과의 겹침은 경고하지 않는다 — 밴드 간 데이터는 서로 보이지 않는다는 원칙과, 애초에
  기록용 도구라는 전제 때문이다.
- **`cost`는 정산과 아직 연결되지 않는다.** 참고용 숫자일 뿐이고, N빵 계산은 Phase 7에서 `Settlement`가
  별도로 받는다.
- **일정 개수·개별 일정 길이에 상한이 없다.** 10년짜리 일정도, 밴드당 수만 건도 막지 않는다(기록용
  도구라 저장 자체는 거부하지 않는다). 대신 캘린더 조회 기간은 400일로 제한하고, 겹침 경고 목록은
  20건으로 잘라 응답이 무한정 커지지 않게 한다(§8.1 fix 3). 일정 생성 레이트리밋은 Phase 8로.
- **`recurring_rule_id`는 컬럼만 있고 안 쓴다.** 정기 일정 생성·회차 관리는 Phase 5 전체가 담당한다.
- Testcontainers 통합 테스트는 이 PC에서 실행 불가 — CI로만 확인.

## 8. 커밋 · CI

- 브랜치 `phase-4-reservation` → **[PR #22](https://github.com/Yekapark/bandApp/pull/22)** (`main` 대상)
- 커밋 (기능 단위):
  1. `feat(reservation): 일정 도메인 모델 + V4 마이그레이션`
  2. `feat(reservation): 밴드/합주실 참조 창구 + usageCount 증감 쿼리`
  3. `feat(reservation): 등록·승인·수정·취소·캘린더 조회 API`
  4. `test(reservation): Phase 4 통합 테스트 + 진행 기록`
- CI: [actions/runs/33488006395](https://github.com/Yekapark/bandApp/actions/runs/33488006395) — pass

### 8.1 머지 후 코드 재검토 후속 수정 (2026-09-01, 별도 PR — Phase 5 시작 전)

PR #22 머지 뒤 Phase 4 전 경로를 다시 훑어 나온 3건을 별도 브랜치 `fix/phase-4-followup`에서 고쳤다.

| # | 문제 | 수정 |
|---|---|---|
| 1 | **동시 취소/거절 시 `usageCount` 이중 차감.** 같은 일정에 DELETE(또는 reject)가 병렬로 들어오면 두 트랜잭션이 각자 `CONFIRMED`를 읽고 각자 `-1`을 실행 → 한 번의 논리적 취소에 카운터가 2 감소. | 상태를 바꾸는 명령(승인·거절·수정·취소)이 대상 일정 행을 `SELECT … FOR UPDATE`(`ReservationRepository.findByIdAndBandIdForUpdate`, `@Lock(PESSIMISTIC_WRITE)`)로 잠근다. 두 번째 요청은 락을 기다렸다가 이미 바뀐 상태를 읽어 `cancel()`이 `false`를 반환하거나 `requirePending`이 409를 던진다 → 감소는 정확히 한 번. 합주실 이동 시 두 방 UPDATE는 방 id 오름차순으로 실행해 AB-BA 교착도 피한다(`shiftUsage`). |
| 2 | **잘못된 쿼리 파라미터 → 500.** `?from=엉터리`, `?includeInactive=huh`, 오프셋 없는 날짜가 `MethodArgumentTypeMismatchException`을 던지는데 `GlobalExceptionHandler`에 매핑이 없어 catch-all이 500으로 처리. Phase 4가 타입 있는 `@RequestParam`을 쓴 첫 엔드포인트라 이제 노출. | `GlobalExceptionHandler`에 `MethodArgumentTypeMismatchException`·`MissingServletRequestParameterException` → `INVALID_INPUT`(400) 핸들러 추가. |
| 3 | **조회 범위·응답 크기 상한 없음.** `list`가 `from=1970..to=2999`도 그대로 처리하고, 넓은 구간 일정 하나가 모든 캘린더 창·모든 `overlaps`에 걸림. | `list` 조회 기간을 400일로 제한(`RESERVATION_RANGE_TOO_WIDE`, 400). 겹침 경고는 `PageRequest`로 20건까지만 조회. |

검증(`docker compose`, 2026-09-01): fix smoke 스크립트 10개 assertion 전부 PASS —
`?from=not-a-date` → 400 `INVALID_INPUT`, 범위 401일 → 400 `RESERVATION_RANGE_TOO_WIDE`, 364일 → 200,
같은 일정 8-스레드 동시 취소 → 전부 204 + `usageCount` 1→0, 8-스레드 동시 거절 → 정확히 1건 200·나머지 409 + `usageCount` 1→0.
동시성은 통합 테스트(`concurrent_cancel_decrements_usage_exactly_once`, `concurrent_reject_decrements_usage_exactly_once`)로 CI 재검증.

## 9. 다음 Phase 예고 — Phase 5 (정기 일정)

반복 규칙(주간/격주/월간, 요일·시간) 등록 → 향후 N주분 `Reservation` 자동 생성. 개별 회차
수정/취소는 해당 `Reservation`만 바꾸고 규칙은 유지. 규칙 삭제 시 미래 회차만 삭제하고 과거 기록은
보존. 만료 임박 규칙의 회차를 이어 만드는 배치잡. 이번에 `reservations.recurring_rule_id` 컬럼을
미리 뚫어 뒀으므로, Phase 5는 `recurring_rules` 테이블과 FK를 `ALTER`로 추가하고 생성 로직만 붙이면 된다.
