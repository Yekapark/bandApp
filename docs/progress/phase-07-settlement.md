# Phase 7 — 정산(N빵)

## 1. 한 줄 요약

합주 일정에 **방값 총액**을 입력하면 멤버별로 낼 몫(`SettlementShare`)을 만들어 둔다. 나누는 방식은
`EQUAL`(현재 밴드 멤버 전원 균등) 또는 `ATTENDEES_ONLY`(참석 표시한 멤버만 균등) 중 선택한다.
**나누어떨어지지 않는 나머지는 밴드장이(밴드장이 대상에 없으면 가장 먼저 가입한 사람이) 부담**해서
몫 합계가 항상 총액과 정확히 일치한다. 참석자가 바뀌면 **재계산 API**로 다시 나누며(서버가 자동으로
다시 나누지는 않는다), 이때 이미 납부 체크한 멤버의 상태는 보존된다. 납부 여부는 **본인이 직접 체크**한다.

## 2. 이 Phase의 목표 (`docs/BUILD_PLAN.md` 기준)

- 일정에 총 비용 입력 → `splitType`에 따라 `SettlementShare` 생성
  - `EQUAL`: 밴드 멤버 전원 균등분배
  - `ATTENDEES_ONLY`: 참석(ATTENDING) 멤버만 균등분배
- 나누어떨어지지 않는 금액의 처리 규칙을 명시적으로 구현 (예: 나머지는 밴드장 부담)
- 정산 생성 후 참석자가 바뀐 경우 재계산 API 제공 (자동 재계산은 하지 않음)
- 본인 납부 체크 API (본인 share만 수정 가능)
- 정산 현황 조회

**완료 기준**: 3명이 10,000원을 나누는 등 나머지가 발생하는 케이스에서 share 합계가 총액과 정확히
일치하고, `ATTENDEES_ONLY`인데 참석자가 0명인 경우가 명시적으로 처리되는 테스트가 통과한다.

### `BUILD_PLAN.md`에 없어 새로 정한 정책 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| **나머지 처리 규칙** | 분담 대상자를 "밴드장 먼저 → 가입일 순"으로 정렬하고, 나머지 R원을 **앞에서부터 한 명당 1원씩** 더한다 | BUILD_PLAN은 "예: 나머지는 밴드장 부담"이라고만 적혀 있다. 이 규칙이면 보통은 밴드장이 부담하고, `ATTENDEES_ONLY`라 밴드장이 불참이면 가장 먼저 가입한 참석자가 부담한다(→ "밴드장 우선, 없으면 최고참", 지시자 확인). 항상 `합계 = 총액`, 결정적 |
| **재계산 API의 범위** | `POST .../settlement/recalculate` 가 현재 멤버·참석자로 몫을 다시 만들되, 본문에 `totalAmount`·`splitType`을 **선택적으로** 받아 같이 갱신(생략 시 유지) | 지시자 확인(1안). 실사용에서 "숫자를 다시 만지는 김에 금액도 고친다"가 자연스럽고 엔드포인트가 하나로 끝난다. 별도 수정 API를 만들지 않았다 |
| **일정당 정산 개수** | 하나(`settlements.reservation_id` UNIQUE). 두 번째 생성은 409 `SETTLEMENT_ALREADY_EXISTS` | 총액 재입력·참석자 변경은 전부 재계산으로 흡수된다. "자동 재계산은 하지 않음" 요건과도 맞다(명시적 호출 하나) |
| **정산 생성·재계산 권한** | 일정 등록자(`reservations.requested_by`) 본인 또는 밴드장. 그 외 403 `NOT_SETTLEMENT_MANAGER` | 일정 수정 권한(Phase 4 `NOT_RESERVATION_OWNER`)과 같은 눈높이 — 정산은 그 일정에 딸린 것이다 |
| **납부 체크 권한** | `PUT .../settlement/shares/{userId}` 에서 `{userId}`가 요청자 본인이 아니면 403 `NOT_SETTLEMENT_SHARE_OWNER`. 요청자가 분담 대상이 아니면 404 `SETTLEMENT_SHARE_NOT_FOUND` | 완료 기준의 "본인 share만 수정 가능"을 그대로 검증할 수 있는 형태(참석 체크 API와 동일 구조) |
| **`ATTENDEES_ONLY` + 참석자 0명** | 생성·재계산 모두 409 `SETTLEMENT_NO_ATTENDEES`로 거부하고 **아무것도 저장하지 않는다** | 완료 기준이 이 경우의 "명시적 처리"를 요구한다. 0명에게 나눌 수 없고, 0원짜리 빈 정산을 만드는 것도 의미가 없다 |
| **정산 대상 일정의 상태** | `status` 무관(CONFIRMED·PENDING·CANCELLED·REJECTED 모두 허용) | 취소된 합주도 위약금·방값이 나갈 수 있어 정산이 필요하다. BUILD_PLAN이 상태 제한을 두지 않았다 |
| **재계산 시 기존 몫 처리** | 계속 대상인 멤버 → 금액만 새로 매기고 `paid`/`paidAt` **보존**. 빠진 멤버 → 행 삭제. 새 멤버 → 미납으로 추가 | 이미 낸 사람의 납부 기록을 재계산이 지우면 안 된다 |
| `created_at` | `settlement_shares`에도 `BaseTimeEntity`로 자동 관리(BUILD_PLAN 모델엔 없음) | Phase 5·6과 동일 — 다른 모든 테이블과 맞춘다 |

## 3. 무엇을 만들었나

### 3.1 데이터베이스 — `src/main/resources/db/migration/V7__settlement.sql`

테이블 2개 신설.

| 대상 | 내용 |
|---|---|
| `settlements` (신규) | `reservation_id`, `total_amount`(원, >0), `split_type`(EQUAL/ATTENDEES_ONLY), `created_at` |
| CHECK | `split_type` 값 제한, `total_amount > 0` |
| `ux_settlements_reservation` | `reservation_id` **UNIQUE** — 일정당 정산 하나. 동시 생성의 안전장치이기도 하다 |
| `settlement_shares` (신규) | `settlement_id`, `user_id`, `amount`(원, ≥0), `paid`(기본 FALSE), `paid_at`, `created_at` |
| FK | `settlement_id → settlements(id) ON DELETE CASCADE` (재계산·향후 삭제 시 자식 정리) |
| CHECK | `amount >= 0` |
| `ux_settlement_shares_member` | `(settlement_id, user_id)` **UNIQUE** — 정산당 멤버 하나 |
| `ix_settlement_shares_settlement` | `(settlement_id)` — 정산 현황 조회 |

### 3.2 정산 도메인 — `src/main/java/com/yeka/bandapp/settlement/`

- **분배 enum** `entity/SplitType.java` — `EQUAL / ATTENDEES_ONLY`.
- **엔티티** `entity/Settlement.java` — `create(reservationId, totalAmount, splitType)` /
  `changeTerms(totalAmount, splitType)`(재계산). 연관관계 없이 `Long` FK, 정적 팩토리, 의미 있는 메서드.
- **엔티티** `entity/SettlementShare.java` — `of(settlementId, userId, amount)` /
  `reassign(amount)`(재계산 — 금액만, `paid` 유지) / `markPaid(paid, when)`(취소하면 `paidAt`도 지움).
- **순수 계산** `service/SettlementCalculator.java` — `split(total, recipients)` → `userId → 몫` 맵.
  균등 몫은 내림, 나머지는 앞에서부터 1원씩. **컨테이너 없이 단위 테스트**한다(`OccurrenceGenerator` 선례).
- **저장소** `repository/SettlementRepository.java` — `findByReservationId`,
  `findByReservationIdForUpdate`(재계산 시 비관적 락). `repository/SettlementShareRepository.java` —
  `findBySettlementId`(현황), `findBySettlementIdAndUserId`(본인 납부 체크).
- **서비스** `service/SettlementService.java`
  - `create(bandId, reservationId, callerId, req)` — 권한(등록자/밴드장) → 대상자 산출 →
    (ATTENDEES_ONLY·0명이면 409) → `settlements` INSERT(유니크 경합 시 409 `SETTLEMENT_ALREADY_EXISTS`)
    → `SettlementCalculator.split` → `settlement_shares` 저장 → 현황 반환.
  - `recalculate(...)` — 권한 → 정산 행 비관적 락 → `totalAmount`/`splitType` 넘어온 값 반영(없으면 유지)
    → 대상자 재산출(0명이면 409) → 기존 몫과 대사: 유지·삭제·추가 → 현황 반환.
  - `markPaid(bandId, reservationId, targetUserId, callerId, paid)` — 멤버십 → 본인 확인(아니면 403) →
    일정 밴드 대조(404) → 본인 몫 조회(없으면 404) → 더티 업데이트(flush 없음) → 현황 반환.
  - `get(...)` — 멤버십 → 일정 밴드 대조(404) → 정산 없으면 404 → 현황 반환.
  - `recipientsFor(...)` — EQUAL은 현재 활성 멤버 전원, ATTENDEES_ONLY는 그중 `attendingUserIds`인 멤버.
    어느 쪽이든 **밴드장을 맨 앞으로** 옮기고 나머지는 가입 순 유지.
- **컨트롤러** `controller/SettlementController.java` —
  `/api/v1/bands/{bandId}/reservations/{reservationId}/settlement` 아래
  `POST`(생성) · `GET`(현황) · `POST /recalculate` · `PUT /shares/{userId}`(납부 체크).
- **DTO** `dto/` — `CreateSettlementRequest`(`totalAmount` `@Positive` 필수, `splitType` 필수),
  `RecalculateSettlementRequest`(둘 다 선택), `UpdateSharePaidRequest`(`paid` 필수),
  `SettlementResponse`(총액·방식·`shareCount`·`paidCount`·`paidAmount`·`outstandingAmount`·`shares`),
  `SettlementShareResponse`(userId·name·role·amount·paid·paidAt).

### 3.3 기존 코드 변경

| 파일 | 변경 |
|---|---|
| `reservation/service/AttendanceService.java` | `attendingUserIds(reservationId)` 추가 — 저장된 참석 행 중 `ATTENDING`인 userId 집합. `ATTENDEES_ONLY` 분배 대상 산출용 |
| `reservation/service/ReservationDirectoryService.java` | `requesterOf(bandId, reservationId)` 추가 — 일정의 `requestedBy` 반환(권한 판단용). 경로 `bandId`와 대조해 타 밴드 일정이면 404 `RESERVATION_NOT_FOUND` |
| `band/service/BandDirectoryService.java` | `displayNamesOf(userIds)` 추가 — 정산 현황이 **정산 생성 이후 밴드를 떠난** 과거 분담자의 이름을 채울 때 사용(조회 불가 시 "(알 수 없음)") |
| `common/exception/ErrorCode.java` | `SETTLEMENT_NOT_FOUND`(404), `SETTLEMENT_ALREADY_EXISTS`(409), `SETTLEMENT_NO_ATTENDEES`(409), `NOT_SETTLEMENT_MANAGER`(403), `SETTLEMENT_SHARE_NOT_FOUND`(404), `NOT_SETTLEMENT_SHARE_OWNER`(403) |

> 기존 일정·참석·셋리스트 API의 응답 형태는 건드리지 않았다. 정산은 별도 엔드포인트로만 노출된다
> (`GET /reservations/{id}` 상세에는 넣지 않았다 — BUILD_PLAN Phase 7이 요구하지 않는다).

## 4. 어떻게 동작하나

### 4.1 정산 생성

`POST .../settlement` body `{"totalAmount":30000,"splitType":"EQUAL"}`:
1. 요청자가 그 밴드 활성 멤버인가? (아니면 403 `NOT_BAND_MEMBER`)
2. 그 일정이 이 밴드 것인가? (아니면 404 `RESERVATION_NOT_FOUND`)
3. 요청자가 일정 등록자 본인이거나 밴드장인가? (아니면 403 `NOT_SETTLEMENT_MANAGER`)
4. 분배 대상자 산출 — EQUAL이면 현재 활성 멤버 전원, ATTENDEES_ONLY이면 그중 참석(ATTENDING)한 멤버.
   ATTENDEES_ONLY인데 0명이면 **409 `SETTLEMENT_NO_ATTENDEES`, 저장 안 함**.
5. `settlements` INSERT — 이미 있으면(유니크 경합) **409 `SETTLEMENT_ALREADY_EXISTS`**.
6. 대상자를 "밴드장 먼저 → 가입 순"으로 정렬 → `total / n`씩, 나머지는 앞에서부터 1원씩 →
   `settlement_shares` 저장.
7. 정산 현황 반환(몫 목록 + `paidCount`/`paidAmount`/`outstandingAmount`).

### 4.2 재계산

`POST .../settlement/recalculate` body `{}` 또는 `{"totalAmount":32000}` 또는 `{"splitType":"ATTENDEES_ONLY"}`:
정산 행에 비관적 락 → 넘어온 값이 있으면 총액·방식 갱신(없으면 유지) → 4.1의 4~6과 같은 방식으로 대상자
재산출 → **기존 몫과 대사**: 계속 대상인 멤버는 금액만 새로 매기고 `paid`/`paidAt` 보존, 빠진 멤버는
행 삭제, 새 멤버는 미납 행 추가. → 갱신된 현황 반환.

### 4.3 납부 체크 (본인만)

`PUT .../settlement/shares/{userId}` body `{"paid":true}`:
요청자 본인의 userId가 아니면 403 → 그 일정이 이 밴드 것이 아니면 404 → 정산이 없으면 404 →
요청자의 몫이 없으면(분담 대상이 아님) 404 `SETTLEMENT_SHARE_NOT_FOUND` → `paid`/`paidAt` 갱신
(취소하면 `paidAt`도 비움). 갱신된 현황 반환.

### 4.4 나머지 배분 예시

10,000원 / 3명, 대상자 순서 `[밴드장, 멤버A, 멤버B]` → `10000 / 3 = 3333`, 나머지 `1` → **밴드장 3334**,
멤버A 3333, 멤버B 3333. 합계 10,000. `ATTENDEES_ONLY`라 밴드장이 불참이면 대상자는 `[멤버A, 멤버B]`
(가입 순) → 나머지는 멤버A가 진다.

## 5. 직접 확인하는 법

### 사전 준비
`docker compose up`(app + postgres + redis). 아래는 세 사용자(리더 L, 멤버 M1·M2)로 진행한다.

### 흐름
1. L로 가입·로그인 → 밴드 생성 → 초대코드로 M1·M2 합류 → 합주실 등록 →
   `POST /api/v1/bands/{bandId}/reservations`로 일정 생성(`reservationId` 확보).
2. **완료 기준 ①** — `POST /api/v1/bands/{bandId}/reservations/{reservationId}/settlement`
   body `{"totalAmount":10000,"splitType":"EQUAL"}` → 201. 응답 `shares`의 `amount` 합이 `10000`,
   밴드장(L) 몫이 `3334`, M1·M2가 각 `3333`.
3. **완료 기준 ②** — 새 일정을 하나 더 만들고 참석 표시 없이
   `POST .../settlement` body `{"totalAmount":30000,"splitType":"ATTENDEES_ONLY"}` →
   **409 `SETTLEMENT_NO_ATTENDEES`**. 이어서 `GET .../settlement` → **404 `SETTLEMENT_NOT_FOUND`**
   (정산이 만들어지지 않았음).
4. 2의 정산에서 L이 `PUT .../attendances/{L의 userId}` `{"status":"ATTENDING"}` →
   `POST .../settlement/recalculate` body `{"splitType":"ATTENDEES_ONLY"}` → 몫이 L 하나(10000)로 바뀜.
5. `PUT .../settlement/shares/{L의 userId}` `{"paid":true}` → 200, 응답 `outstandingAmount:0`.
   M1 토큰으로 `PUT .../settlement/shares/{L의 userId}` → **403 `NOT_SETTLEMENT_SHARE_OWNER`**.
6. `POST .../settlement` 를 같은 일정에 다시 → **409 `SETTLEMENT_ALREADY_EXISTS`**.
7. M2 토큰으로(등록자·밴드장 아님) 다른 일정에 `POST .../settlement` → **403 `NOT_SETTLEMENT_MANAGER`**.

### 기대 결과 / 문제 해결
- 2에서 몫 합이 10000이 아니면 버그. 나머지는 항상 목록 맨 앞(밴드장)으로 몰린다.
- 3에서 201이 나오면 참석자 판정이 잘못된 것(아무도 ATTENDING이 아니어야 한다).
- 4에서 몫이 안 바뀌면 재계산이 호출되지 않았거나 참석 응답이 저장되지 않은 것.
- 5의 403은 path의 userId가 본인인지 확인(`GET /api/v1/users/me`의 `id`).

## 6. 실제 검증 기록

- `./gradlew compileJava compileTestJava` — **성공**.
- 순수 단위 테스트 `SettlementCalculatorTest` (6건: 나머지 배분, 나누어떨어짐, 2원 나머지, 1인,
  총액<인원, 잘못된 입력 거부) — **로컬에서 통과**. 기존 순수 단위 테스트(`OccurrenceGeneratorTest`,
  `InviteCodeGeneratorTest`, `JwtTokenProviderTest`, `NaverGeocodingParseTest`)도 함께 통과.
- **Testcontainers 통합 테스트는 이 로컬 환경에서 실행하지 못했다** — Phase 0~6과 동일한
  Docker/Testcontainers 버전 불일치(`NoClassDefFoundError`로 컨테이너 초기화 중단). Phase 6의 기존
  통합 테스트도 같은 지점에서 실패하는 것을 재확인했다. 검증은 **CI(GitHub Actions)** 에서 이뤄진다.
- **대신 앱을 로컬에서 실제로 띄워 시나리오를 돌렸다.** `docker compose`의 postgres가 이 PC의
  네이티브 Windows PostgreSQL 서비스(5432 선점)와 충돌해, 임시 postgres/redis 컨테이너를 다른
  포트(5544/6390)에 띄우고 `build/libs`의 부트 jar를 그 DB로 붙여 기동:
  - **Flyway가 V1~V7 7개 마이그레이션을 모두 적용**하고 `ddl-auto: validate` 통과 →
    신규 엔티티(`Settlement`, `SettlementShare`)와 V7 스키마가 일치함을 확인.
  - curl 시나리오 결과: 완료 기준 ①(3명 10,000원 EQUAL → 3334/3333/3333, 합계 10,000, 나머지 밴드장),
    완료 기준 ②(ATTENDEES_ONLY 0명 → 409, GET 404), 재계산(방식 변경 + 참석자 재반영, 합계 유지),
    납부 체크(본인 200 / 타인 403, 집계 반영), 중복 생성 409, 권한 없는 멤버 생성 403 — **전부 기대대로**.
- 신규 통합 테스트 `src/test/java/com/yeka/bandapp/settlement/SettlementIntegrationTest.java` —
  완료 기준 2건 + 참석자 기준 분배, 재계산(신규 참석자 추가·`paid` 보존), 총액 수정,
  권한(등록자/밴드장), 본인만 납부 체크(타인 403·비대상자 404), 중복 생성 409, 타 밴드 격리,
  **납부 체크 × 재계산 동시 실행**(재계산이 몫을 지웠다 되살리는 사이 납부 체크가 끼어들어도
  500 없이 200/404만, 최종 합계 = 총액).
  **CI 실행 대기 중**(아래 링크는 PR 생성 후 갱신).

## 6.1 구현 후 자체 점검(보안·누락) 결과

| 발견 | 심각도 | 조치 |
|---|---|---|
| **타 밴드 격리** — 모든 엔드포인트가 `requireActiveMember(bandId, …)` + `reservationDirectory.requesterOf(bandId, reservationId)`로 일정의 밴드 소속을 대조. 경로에 남의 `reservationId`를 끼우면 404 `RESERVATION_NOT_FOUND`, 비멤버는 403 `NOT_BAND_MEMBER`. | — | 문제 없음(설계대로, 통합 테스트로 검증) |
| **본인만 납부 체크** — `PUT .../shares/{userId}`에서 `{userId}≠요청자`면 403. 성공 경로는 언제나 요청자 본인 몫뿐이라 타인의 `paid`를 바꿀 수 없다. | — | 문제 없음 |
| **동시 생성 경합** — 두 요청이 동시에 `create`하면 `ux_settlements_reservation`에 걸려 진 쪽은 `DataIntegrityViolationException` → 409 `SETTLEMENT_ALREADY_EXISTS`로 변환(CLAUDE.md 규칙). `saveAndFlush`로 그 자리에서 잡는다. | — | 조치 완료 |
| **동시 재계산 경합** — 정산 행에 `PESSIMISTIC_WRITE` 락(`findByReservationIdForUpdate`)을 걸어 같은 일정의 동시 재계산을 직렬화. 상태 전이가 없어 usageCount 같은 부수효과는 없다. | — | 조치 완료 |
| **납부 체크 × 재계산 경합** — 처음엔 `markPaid`가 정산 행 락을 안 잡아, 재계산이 그 멤버의 몫을 삭제/재산정하는 사이 납부 체크가 끼면 `paid` 갱신이 유실되거나 사라진 몫이 응답에 실릴 수 있었다(데이터 손상은 아님, 좁은 창). | 낮음 | `markPaid`도 `findByReservationIdForUpdate`로 **같은 정산 행 락**을 잡아 재계산과 직렬화(항상 재계산 커밋 후의 일관된 상태만 봄 → 몫 있으면 200·갱신 확정, 없으면 깔끔한 404). 동시 실행 회귀 테스트 추가 |
| **몫 합계 불변식** — `SettlementCalculator`가 `base * n + remainder == total`을 구조적으로 보장(나머지는 0..n-1, 앞에서부터 1씩). 단위 테스트가 여러 총액·인원 조합에서 합계=총액을 확인. | — | 문제 없음 |
| 외부 HTTP 호출 없음 — 정산은 전부 DB 작업이라 CLAUDE.md의 "외부 I/O는 트랜잭션 밖에서" 규칙 대상이 아니다. 각 명령은 단일 `@Transactional`. | — | 해당 없음 |
| 정산 생성/재계산에 rate limit 없음 — 기존 일정·참석·셋리스트 쓰기도 대상이 아니다(초대·인증·지오코딩만). 내부 저위험 쓰기라 일관되게 두었다. | 하 | 현행 유지 |
| SQL 인젝션 / 대량 바인딩 — 전부 JPA 파생 쿼리·JPQL, 파라미터 바인딩만. DTO는 명시적 `record`. | — | 문제 없음 |
| `deleteAll(빠진 멤버)` + `saveAll(새 멤버)`가 한 트랜잭션에 섞인다 — 빠진 멤버와 새 멤버의 `user_id`는 서로소라 `(settlement_id, user_id)` 유니크 충돌이 없다. | — | 문제 없음(주석에 근거 명시) |

## 7. 알려진 이슈 / 제약

- **자동 재계산은 하지 않는다.** 참석 응답이 바뀌어도 서버는 몫을 다시 만들지 않는다 — 등록자·밴드장이
  `POST .../settlement/recalculate`를 호출해야 반영된다(BUILD_PLAN 요건).
- 재계산으로 어떤 멤버의 몫이 이미 낸 금액보다 작아질 수 있다(예: 4500 내고 체크했는데 참석자가 늘어
  3000이 됨). 시스템은 `paid=true`만 보존하고 차액 환불/추가 청구는 다루지 않는다 — 밴드 내부에서 정리한다.
- `EQUAL`은 **현재** 활성 멤버 기준이다. 정산 생성 후 멤버가 나가면 재계산 전까지 그 사람 몫이 그대로
  남는다. 현황 응답에서는 이름이 "(알 수 없음)", 역할이 "MEMBER"로 표시된다.
- `ATTENDEES_ONLY` 정산에서 참석자가 전부 빠진 뒤 `recalculate`를 부르면 409 `SETTLEMENT_NO_ATTENDEES`로
  막히고 **기존 몫은 그대로 동결**된다(페일세이프). 풀려면 누군가 다시 ATTENDING으로 응답하거나
  `recalculate`에 `splitType=EQUAL`을 실어 보낸다.
- 정산 대상 일정의 상태는 제한하지 않는다(취소·거절된 일정도 정산 가능). 방값·위약금이 실제로 나갈 수 있어서다.
- 금액 단위는 원(정수). 소수점·통화 구분은 없다.
- 정산 삭제 API는 만들지 않았다(BUILD_PLAN에 없음). 스키마상 `settlement_shares`는
  `ON DELETE CASCADE`라 향후 삭제를 붙이기는 쉽다.
- "정산 요청 알림"(BUILD_PLAN Phase 9 알림 트리거)은 이 Phase 범위 밖이다 — 여기서는 상태만 관리한다.

## 8. 커밋 · CI 링크

- 브랜치: `phase-7-settlement`
- PR: _(생성 후 갱신)_
- CI: _(생성 후 갱신)_

## 9. 다음 Phase 예고

Phase 8 — 게시판 · 미디어 업로드 · 신고. 게시글 CRUD(밴드 멤버만), R2 presigned PUT/GET URL 발급
(백엔드 경유 파일 스트림 없음), `MediaAttachment` PENDING 선생성 → 업로드 콜백 시 R2 HEAD로 크기 검증
후 READY 전환, 업로드 URL 발급 레이트리밋, 신고 접수 API, 사용자 차단(차단한 사용자 글이 목록에서 제외).
