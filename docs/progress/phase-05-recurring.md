# Phase 5 — 정기 일정 (RecurringRule)

## 1. 한 줄 요약

"매주 토요일 15:00~18:00 합주" 같은 **반복 규칙**을 한 번 등록하면, 앞으로 8주분 회차가
`Reservation`(Phase 4의 일정과 같은 테이블·같은 상태 흐름)으로 자동 생성된다. 개별 회차의
수정·취소는 기존 일정 API를 그대로 쓰고 규칙은 그대로 유지된다. **규칙을 삭제하면 아직 시작하지
않은 회차만 취소(CANCELLED)되고, 과거 회차는 상태·행 모두 그대로 남는다** — 나중에 붙을 정산
기록이 깨지지 않게 하기 위해서다. 매일 새벽 배치가 지평선(8주 뒤)에 닿은 규칙의 회차를 이어서 만든다.
시간대 겹침은 Phase 4와 똑같이 등록을 막지 않고 응답에 경고로만 싣는다.

## 2. 이 Phase의 목표 (`docs/BUILD_PLAN.md` 기준)

- 반복 규칙 등록 (주간/격주/월간, 요일·시간 지정)
- 규칙에 따라 향후 N주분 `Reservation` 자동 생성
- 개별 회차 수정/취소 (규칙 자체는 유지)
- 규칙 삭제 시 미래 회차만 삭제하고 과거 기록은 보존
- 만료 임박한 규칙의 회차를 이어서 생성하는 배치잡

**완료 기준**: 규칙 삭제 후에도 과거 일정과 그에 연결된 정산 기록이 남아 있는 테스트가 통과한다.

### `BUILD_PLAN.md`에 없어 지시자 승인을 받은 정책 결정

| 항목 | 결정 | 이유 |
|---|---|---|
| 로컬시각 → UTC 변환 기준 | `app.recurring.zone: Asia/Seoul` 설정값 고정. 규칙마다 시간대를 저장하지 않는다 | 기존 `app.withdrawal.purge-zone`과 같은 패턴. 도메인 모델을 건드리지 않는다. 해외 밴드가 생기면 그때 규칙에 컬럼 추가 |
| MONTHLY(월간) 해석 | `startDate`에서 "그 달의 몇 번째 요일"인지 유도해 매월 같은 주차·요일 (예: 매월 둘째 주 토요일). 그 주차가 없는 달(5번째 토요일 등)은 건너뜀 | `RecurringRule`에 "몇째 주" 필드가 없다. 별도 컬럼 없이 구현 가능한 해석 |
| 규칙 삭제 시 미래 회차 | 하드 삭제가 아니라 **CANCELLED 전환**(행 보존) | 이 앱의 "기록은 지우지 않는다" 원칙(취소도 행 보존)과 일치. Phase 7 정산 FK가 붙어도 그대로 성립 |
| `recurring_rules.deleted_at` | 규칙도 **소프트 삭제** | 하드 삭제하면 과거 회차의 `recurring_rule_id` FK가 깨져 완료 기준("과거 일정 보존")을 만족할 수 없다. `rooms.deleted_at`과 같은 선례 |
| `recurring_rules.created_at` | 다른 모든 테이블과 동일하게 추가(`BaseTimeEntity`) | 규칙 목록 최신순 정렬·감사용. `band_invites.created_at`과 같은 선례 |
| `recurring_rules.cost` / `note` | 규칙에 저장하고 생성되는 모든 회차에 복사 | 배치가 이어 만드는 회차도 같은 값을 갖게 하려면 규칙에 있어야 한다. 회차별로 다르게 하려면 Phase 4 일정 수정 API로 |
| APPROVAL_REQUIRED 밴드의 규칙 등록 | 규칙 등록 자체를 **밴드장 전용**으로, 생성된 회차는 바로 CONFIRMED | 규칙 하나가 8~수십 회차를 만드는데 회차마다 승인받게 하면 실용성이 없다. 규칙 등록을 승인 행위로 본다 |
| 규칙 **수정** API | 만들지 않음 (삭제 후 재등록) | BUILD_PLAN Phase 5 항목에 "수정"이 없다. 규칙을 바꾸면 이미 만든 회차와의 정합성 규칙이 복잡해진다 |

### `BUILD_PLAN.md` 문서 간 불일치 (지시자 확인 필요)

1. **회차 연장 배치잡의 소속.** 216–222행은 이 배치를 **Phase 5** 항목으로, 275행은 "배치잡 3:
   정기 일정 회차 이어서 생성 (Phase 5)"을 **Phase 9**의 배치 묶음으로 적는다. → Phase 5 본문이
   더 구체적이므로 **이번에 구현**했다. Phase 9에서는 다른 두 배치(미디어 만료·PENDING 정리)만 남는다.
2. **PRO(요금제) 게이팅.** `example/*.dc.html` 디자인 시안은 "정기 예약"을 PRO 전용으로 표시하지만
   BUILD_PLAN은 Phase 5에 요금제 조건을 걸지 않았다(요금제는 Phase 10). → **게이팅 없이** 구현했다.
   Phase 10에서 정책이 정해지면 `RecurringRuleService.create` 앞단에 플랜 확인만 추가하면 된다.
3. **"연결된 정산 기록".** 완료 기준의 이 표현이 가리키는 `Settlement`는 Phase 7 산출물이라 아직
   없다. 이번엔 **과거 회차 행·상태 보존**까지 검증했고, 미래 회차를 삭제가 아닌 CANCELLED로 두는
   설계라 Phase 7에서 `settlements.reservation_id` FK가 붙어도 규칙 삭제가 그 FK를 건드리지 않는다.

## 3. 무엇을 만들었나

### 3.1 데이터베이스 — `src/main/resources/db/migration/V5__recurring.sql`

테이블 1개 신설 + 기존 `reservations`에 FK·인덱스 추가.

| 대상 | 내용 |
|---|---|
| `recurring_rules` (신규) | 반복 규칙. `frequency`(WEEKLY/BIWEEKLY/MONTHLY), `day_of_week`(java.time.DayOfWeek 이름), `start_time`/`end_time`(로컬 TIME), `start_date`/`end_date`(DATE, end_date NULL=무기한), `cost`/`note`(회차 복사값), `created_by`, `created_at`, `deleted_at`(소프트 삭제) |
| CHECK | `frequency` 값 제한, `end_time > start_time`, `end_date IS NULL OR end_date >= start_date` |
| `ix_recurring_rules_band` | `(band_id, created_at DESC) WHERE deleted_at IS NULL` — 활성 규칙 목록·배치 스캔 |
| `fk_reservations_recurring_rule` | Phase 4가 컬럼만 뚫어 둔 `reservations.recurring_rule_id`에 이제 FK 부여 |
| `ix_reservations_rule` | `(recurring_rule_id, start_at) WHERE recurring_rule_id IS NOT NULL` — 규칙별 회차 조회 |
| `ux_reservations_rule_slot` | `(recurring_rule_id, start_at)` **UNIQUE** partial — 한 규칙이 같은 시각 회차를 두 번 만들지 못하게(배치 재실행 멱등성) |

> `ux_reservations_rule_slot`은 **겹침 차단 제약이 아니다.** 서로 다른 규칙·수동 등록 일정끼리의
> 시간대 겹침은 여전히 전혀 막지 않는다(BUILD_PLAN 2장 2번). "같은 규칙 + 같은 start_at" 중복만 막는다.
> 개별 회차를 취소해도 행은 남으므로 배치가 다시 돌아도 취소한 회차가 되살아나지 않는다.

### 3.2 정기 일정 도메인 — `src/main/java/com/yeka/bandapp/recurring/`

- **엔티티** `entity/RecurringRule.java` — `Reservation`/`Room`과 같은 스타일(연관관계 없이 `Long` FK,
  정적 팩토리, 의미 있는 메서드). `create(...)` / `delete(when)` / `isDeleted()` / `belongsTo` / `isCreatedBy`.
- **주기 enum** `entity/RecurringFrequency.java` — `WEEKLY / BIWEEKLY / MONTHLY`.
- **회차 계산기** `service/OccurrenceGenerator.java` — **스프링 빈이 아닌 순수 함수 모음.**
  컨테이너 없이 단위 테스트하려고 이렇게 뒀다. `java.time`만 쓰고 iCal/RRULE 라이브러리를 넣지 않았다
  (BUILD_PLAN 2장 6번).
  - `occurrenceDates(rule, horizonEndInclusive, exclusiveAfter)` — `(exclusiveAfter, horizonEnd]` 구간의
    로컬 시작일 목록. anchor = `startDate` 이후 처음 맞는 요일. WEEKLY=+7, BIWEEKLY=+14,
    MONTHLY=매월 같은 주차·요일(없는 달 스킵). `endDate`·`MAX_OCCURRENCES_PER_RUN`(200)으로 상한.
  - `toInstant(date, time, zone)` — 로컬 날짜+시각 → UTC `Instant`. 서울은 DST가 없어 모호하지 않다.
- **설정** `service/RecurringProperties.java` — `@ConfigurationProperties("app.recurring")` record.
  `zone`(기본 Asia/Seoul), `horizonWeeks`(기본 8). `WithdrawalProperties`와 같은 형태.
- **서비스** `service/RecurringRuleService.java` — 아래 4장.
- **배치** `schedule/RecurringExtensionJob.java` — 아래 4장.
- **컨트롤러** `controller/RecurringRuleController.java` — `@RequestMapping("/api/v1/bands/{bandId}/recurring-rules")`,
  Swagger `@Tag(name = "8. 정기 일정")`.
- **DTO** `dto/` — `CreateRecurringRuleRequest`, `RecurringRuleResponse`, `RecurringRuleWriteResponse`
  (규칙 + 회차 + `overlaps`), `RecurringRuleDetailResponse`, `RecurringRuleListResponse`. 모두 record.

### 3.3 일정 도메인에 추가한 창구 — `reservation/service/ReservationDirectoryService.java` (신규)

코딩 컨벤션상 "도메인 간 참조는 저장소가 아니라 서비스 레이어를 통한다". 정기 도메인이 회차를
만들고 되돌릴 때 `ReservationRepository`를 직접 만지지 않게, `RoomDirectoryService`와 같은 창구를 뒀다.
"일정 도메인이 `Reservation`과 합주실 `usageCount`를 관리한다"는 Phase 4 불변조건을 유지한다.

| 메서드 | 내용 |
|---|---|
| `createOccurrences(...)` | 회차 벌크 저장 + 합주실 `usageCount`를 **한 번에** +N |
| `occurrenceStartsOf(ruleId)` | 이미 만든 회차 시작 시각들 — 재생성 시 중복 슬롯 제거용 |
| `lastOccurrenceStartOf(ruleId)` | 규칙의 마지막 회차 시각 — 배치가 "그 다음부터" 이어 만든다 |
| `occurrencesOf(ruleId)` | 규칙 상세용, 취소분 포함 |
| `cancelFutureOccurrences(ruleId, from)` | 아직 시작 안 한 살아 있는 회차만 CANCELLED + 방별 `usageCount` -N |
| `overlapsAmong(...)` | 등록 응답 겹침 경고 (규칙 자신의 회차는 제외) |

- `reservation/entity/Reservation.java` — 정적 팩토리 `ofRecurringRule(...)` 추가. `recurringRuleId`를
  채우고 status는 항상 `CONFIRMED`. 기존 `create(...)`는 그대로.
- `reservation/dto/ReservationResponse.java` — `recurringRuleId` 필드 추가(정기 회차 구분).
- `reservation/service/OccurrenceSlot.java` — 확정된 UTC `(startAt, endAt)` 한 건을 나르는 record.

### 3.4 합주실 `usageCount` 벌크 증감 — `room/repository/RoomRepository.java`에 2개 추가

```java
@Modifying @Query("update Room r set r.usageCount = r.usageCount + :delta where r.id = :id")
int increaseUsageCountBy(long id, int delta);

// 어떤 경로로도 0 밑으로 내려가지 않게 CASE 로 바닥을 맞춘다.
@Modifying @Query("update Room r set r.usageCount = case when r.usageCount > :delta then r.usageCount - :delta else 0 end where r.id = :id")
int decreaseUsageCountBy(long id, int delta);
```

회차 N건을 만들 때 UPDATE를 N번 치지 않으려고 한 번에 처리한다. Phase 4의 단건
`increaseUsageCount`/`decreaseUsageCount`와 같은 계열(원자 UPDATE, `clearAutomatically` 안 씀 —
같은 트랜잭션의 dirty 엔티티가 사라지는 Phase 4의 그 버그를 피한다). `RoomDirectoryService`에
`increaseUsageBy`/`decreaseUsageBy` 래퍼를 뒀다.

### 3.5 공통 — `common/exception/ErrorCode.java`

| 코드 | HTTP | 언제 |
|---|---|---|
| `RECURRING_RULE_NOT_FOUND` | 404 | 없는 규칙 / 타 밴드 규칙 / 이미 삭제된 규칙 |
| `INVALID_RECURRING_TIME` | 400 | `endTime <= startTime` |
| `INVALID_RECURRING_DATE_RANGE` | 400 | `endDate < startDate` |
| `NOT_RECURRING_RULE_OWNER` | 403 | 등록자도 밴드장도 아닌 사람이 규칙 삭제 시도 |

권한 모드 위반은 Phase 4의 `NOT_BAND_LEADER`(403), 비멤버는 `NOT_BAND_MEMBER`(403),
합주실 없음은 `ROOM_NOT_FOUND`(404)를 재사용한다.

### 3.6 설정 — `src/main/resources/application.yml`

```yaml
app:
  recurring:
    zone: Asia/Seoul                                     # 요일·시각 해석 기준 시간대
    horizon-weeks: ${RECURRING_HORIZON_WEEKS:8}          # 미리 만들어 둘 기간
    extend-cron: ${RECURRING_EXTEND_CRON:0 0 5 * * *}    # 매일 05:00 KST 회차 연장 배치
```

### 3.7 API 목록

인증 필요(Bearer). 모든 엔드포인트가 밴드 멤버십을 검증한다.

| 메서드 · 경로 | 설명 | 권한 |
|---|---|---|
| `POST /api/v1/bands/{bandId}/recurring-rules` | 규칙 등록 + 회차 즉시 생성 (→ 201, `{rule, occurrenceCount, occurrences, overlaps}`) | ANYONE이면 멤버 누구나, 그 외 밴드장 |
| `GET /api/v1/bands/{bandId}/recurring-rules` | 활성 규칙 목록 (최신순) | 밴드 멤버 |
| `GET /api/v1/bands/{bandId}/recurring-rules/{ruleId}` | 규칙 상세 + 회차 전체 | 밴드 멤버 |
| `DELETE /api/v1/bands/{bandId}/recurring-rules/{ruleId}` | 규칙 삭제 + 미래 회차 취소 (→ 204) | 등록자 본인 또는 밴드장 |

**개별 회차의 수정·취소는 새 API가 없다** — Phase 4의 `PUT`/`DELETE /reservations/{id}`를 그대로 쓴다.
규칙은 건드리지 않는다.

## 4. 어떻게 동작하나

### 규칙 등록과 회차 생성 (완료 기준의 전제)

등록 흐름: **① 멤버십 검증 → ② 권한 모드 검증 → ③ 시간·날짜 검증 → ④ 합주실 검증 → ⑤ 규칙 저장
→ ⑥ 지평선(오늘 + `horizonWeeks`)까지 회차 날짜 계산 → ⑦ 이미 있는 슬롯 제외하고 벌크 저장 +
합주실 `usageCount` += N → ⑧ 회차·겹침 목록을 응답에 첨부.**

- **로컬 → UTC.** `day_of_week`·`start_time`은 Asia/Seoul 기준이다. `2026-09-05` 토요일 `15:00`은
  `2026-09-05T06:00:00Z`가 된다. 서울은 DST가 없어 항상 UTC+9다.
- **주기별 회차.** WEEKLY=7일, BIWEEKLY=14일 간격. MONTHLY는 첫 회차가 "그 달의 몇 번째 요일"인지
  본 뒤 매월 같은 주차·요일 — 5번째 주가 없는 달은 건너뛴다.
- **회차 status는 항상 CONFIRMED.** APPROVAL_REQUIRED 밴드라도 규칙 등록 자체가 밴드장 승인 행위라,
  회차마다 다시 승인받지 않는다.

### 겹쳐도 저장된다 — Phase 4와 동일

규칙이 만든 회차가 기존 일정과 시간대가 겹쳐도 **201로 성공**하고, 겹치는 일정이 응답 `overlaps`에
담긴다. 저장을 막지 않는다(BUILD_PLAN 2장 2번). `overlaps`는 규칙 자신의 회차는 제외하고, 전체
회차를 통틀어 20건까지만 싣는다.

### 규칙 삭제 — 미래만 취소, 과거는 보존 (완료 기준 ①)

`DELETE`는 규칙 행을 지우지 않고 `deleted_at`을 찍는다(소프트 삭제 → 이후 목록·상세에서 404).
그리고 **`start_at`이 지금 이후인 살아 있는 회차만** `CANCELLED`로 바꾼다. 과거·진행 중인 회차는
상태도 행도 그대로다. 취소된 회차 수만큼 합주실 `usageCount`를 되돌린다(회차가 개별 수정으로 다른
방을 가리킬 수 있어 방별로 집계하고, 방 id 오름차순으로 UPDATE해 Phase 4 `shiftUsage`와 같은
교착을 피한다).

이 설계 덕에 Phase 7에서 `Settlement`가 과거 회차에 붙어도, 규칙 삭제가 그 회차 행이나 FK를
건드리지 않는다.

### 개별 회차와 규칙의 독립성

회차 하나를 Phase 4 API로 취소하면 그 `Reservation`만 `CANCELLED`가 되고 규칙은 그대로다.
배치가 다시 돌아도 그 회차는 **되살아나지 않는다** — 재생성 시 이미 존재하는 `start_at`
(취소분 포함)을 슬롯 후보에서 빼기 때문이다.

### 회차 연장 배치 — `RecurringExtensionJob`

매일 05:00 KST(`app.recurring.extend-cron`). 활성 규칙을 id 키셋 페이징으로 훑으며 규칙마다
**별도 트랜잭션**(`RecurringRuleService.extendRule`)으로:

1. 규칙의 마지막 회차 시각을 구한다.
2. 그 **다음**부터 지평선까지 회차 날짜를 다시 계산한다.
3. 아직 없는 슬롯만 저장한다.

한 규칙이 실패해도 로그를 남기고 다음 규칙으로 넘어간다. 여러 번 돌려도 회차 수가 늘지 않는다(멱등).
`WithdrawnUserPurgeJob`과 같은 계열이며 **분산 락은 없다**(단일 VM 전제 — 7장).

## 5. 직접 확인하는 법

### 사전 준비

Phase 2~4와 동일. `.env`에 `JWT_SECRET`(32자 이상), Docker Desktop 필요.
`NAVER_MAP_CLIENT_ID`/`SECRET`은 비워도 된다.

### 방법 A — 전체 스택 실행 후 스모크 (권장)

```bash
docker compose up --build -d
curl -s http://localhost:8080/actuator/health                                   # {"status":"UP"}
docker compose exec postgres psql -U bandapp -d bandapp -c '\d recurring_rules'  # CHECK 3, 인덱스 2, FK 3
docker compose exec postgres psql -U bandapp -d bandapp -c '\d reservations'     # FK 4, ux_reservations_rule_slot
```

curl로 아래 흐름을 따르면 된다(개발 중 `bash` 스모크로 실제 확인한 시나리오). **요청 본문은
Windows 환경에서 `--data-binary @파일`로 보내야 한다**(인라인 `-d`가 한글·따옴표에서 깨진다):

```
가입(리더/멤버) → 밴드 생성 → 멤버 초대·참여 → 합주실 등록
# LEADER_ONLY(기본)
멤버가 규칙 등록                       → 403 NOT_BAND_LEADER
밴드장이 WEEKLY 규칙 등록(토 15:00~18:00, cost 30000, note " 정기합주 ")
                                      → 201, occurrenceCount=8, 첫 회차 startAt=...T06:00:00Z (15:00 KST)
                                      → 회차 status=CONFIRMED, recurringRuleId 채워짐, cost=30000, note="정기합주"(trim)
GET rooms/{id}                        → usageCount = 8
GET recurring-rules                   → ruleCount = 1
회차 1건 DELETE(/reservations/{id})   → 204, 규칙 상세는 여전히 8건(그 1건만 CANCELLED)
# 과거·미래 섞인 규칙 (startDate = 3주 전)
새 규칙 등록                          → 예: 총 11건 = 과거 3 + 미래 8
GET rooms/{id}                        → usageCount = 11
규칙 DELETE                           → 204, GET 규칙 → 404
DB 확인: 과거 3건 status=CONFIRMED 그대로, 미래 8건 CANCELLED, 행 11건 모두 존재   ← 완료 기준 ①
GET rooms/{id}                        → usageCount = 3 (과거 건수)
# 검증
endTime <= startTime 로 등록          → 400 INVALID_RECURRING_TIME
```

정리: `docker compose down -v`

### 방법 B — Swagger UI

`http://localhost:8080/swagger-ui.html` → **"8. 정기 일정"** 태그. 응답 본문에서
`data.occurrences[*].startAt`(UTC), `data.occurrences[*].status`, `data.overlaps`를 확인한다.

### 방법 C — 자동 테스트

`./gradlew test`. 이 개발 PC에서는 Testcontainers가 안 떠서 CI에서 최종 pass/fail이 난다.
컨테이너 없이 로컬 가능:

```bash
./gradlew compileJava compileTestJava
./gradlew test --tests "com.yeka.bandapp.recurring.OccurrenceGeneratorTest"   # 순수 단위 — 컨테이너 불필요
```

### 문제 해결

- **`ddl-auto validate` 실패로 기동 불가**: `V5__recurring.sql`과 `RecurringRule` 엔티티 매핑 불일치.
  마이그레이션을 고친다(엔티티 아님). 특히 `day_of_week`는 `VARCHAR(10)` ↔ `@Column(length = 10)`.
- **회차가 하나도 안 생김**: `startDate`가 지평선(오늘 + 8주)보다 뒤이거나, `endDate`가 `startDate`보다
  앞. `MONTHLY`인데 `startDate`가 5번째 주라 대상 달에 그 주차가 없을 수도.
- **회차 시각이 예상과 9시간 다름**: 응답의 `startAt`은 UTC다. `15:00` KST = `06:00Z`가 정상.
- **규칙 삭제했는데 과거 회차가 사라짐 / 미래 회차가 안 지워짐**: 최신 이미지인지 확인
  (`docker compose up --build -d`).
- **정기 규칙 등록이 `INVALID_INPUT`("요청 본문을 해석할 수 없습니다")**: `dayOfWeek`는 대문자 영문
  (`SATURDAY`), `startTime`은 `"15:00"`, `startDate`는 `"2026-09-05"` 형식. Windows curl은 `--data-binary @파일`.

## 6. 실제 검증 기록

### 6.1 컴파일 (2026-09-01, 개발 PC)

`./gradlew compileJava compileTestJava` 통과. `OccurrenceGeneratorTest`(순수 단위, 10 케이스) 통과.

### 6.2 `docker compose` 전체 스택 수동 검증 (2026-09-01, 개발 PC)

`docker compose up --build -d` 후 `/actuator/health` = `UP` (→ `ddl-auto: validate`가 V5 + 엔티티
매핑을 통과). `flyway_schema_history`에 `5 | recurring | t`.
`\d recurring_rules` — CHECK 3개, `ix_recurring_rules_band`, FK 3개 확인.
`\d reservations` — `fk_reservations_recurring_rule` 추가로 FK 4개, `ix_reservations_rule` +
`ux_reservations_rule_slot`(UNIQUE partial) 확인.

`bash` 스모크 2종, 전부 관찰대로:

| 검증 | 결과 |
|---|---|
| LEADER_ONLY — 멤버가 규칙 등록 | **403 `NOT_BAND_LEADER`** |
| 밴드장 WEEKLY 등록 (토 15:00~18:00, cost 30000, note `" 정기합주 "`) | **201, occurrenceCount=8** |
| 첫 회차 `startAt` / `endAt` | **`2026-09-05T06:00:00Z` / `T09:00:00Z`** (15:00~18:00 KST) |
| 회차 status / `recurringRuleId` / `cost` / `note` | **CONFIRMED / 1 / 30000 / `"정기합주"`(trim됨)** |
| 등록 직후 합주실 `usageCount` | **8** |
| `overlaps` (겹치는 기존 일정 없음) | **0** |
| 개별 회차 1건 `DELETE /reservations/{id}` | 204, 규칙 상세 여전히 8건 (statuses = CONFIRMED + CANCELLED) |
| 규칙 `DELETE` | 204, 이후 `GET` 규칙 → **404** |
| **과거 3 / 미래 8 섞인 규칙(startDate 3주 전) 삭제 후 DB** | **과거 3건 CONFIRMED 유지, 미래 8건 CANCELLED, 행 11건 모두 존재** ← 완료 기준 ① |
| 삭제 후 합주실 `usageCount` | 11 → **3** (과거 건수) |
| `endTime <= startTime` 등록 | **400 `INVALID_RECURRING_TIME`** |

배치 연장·격주/월간 간격·타 밴드 격리·멱등성은 통합 테스트에서 CI로 검증한다.

### 6.3 CI — 자동 테스트 (2026-09-01, PR #NN)

브랜치 `phase-5-recurring` → `main` [PR #NN](https://github.com/Yekapark/bandApp/pull/NN).
[actions/runs/NN](https://github.com/Yekapark/bandApp/actions/runs/NN) — <결과 기입>

테스트 클래스:
- `recurring/RecurringRuleIntegrationTest` — **완료 기준**(규칙 삭제 시 과거 회차 보존 / 미래만 취소) +
  주간 KST 로컬 시각 변환, 격주 14일 간격, 월간 주차·요일 반복, 권한 3모드 게이팅(회차는 항상 CONFIRMED),
  겹침 경고, 개별 회차 취소 후 규칙 유지·비재생성, `usageCount` 증감, 타 밴드 격리
- `recurring/RecurringExtensionJobTest` — 뒤쪽 회차 삭제 후 연장이 그만큼 복구 + **두 번 호출 멱등**,
  `endDate` 너머로 안 만듦, 삭제된 규칙엔 아무 것도 안 함 (스케줄러 대신 서비스 직접 호출)
- `recurring/OccurrenceGeneratorTest` — 순수 단위. 주간/격주 간격, anchor 전진, `endDate` 상한,
  `exclusiveAfter` 시프트, 월간 주차 유지·5주차 없는 달 스킵·일반 케이스, `MAX_OCCURRENCES_PER_RUN` 상한,
  지평선 밖이면 빈 결과
- `reservation/ReservationApiSupport` — `recurring` 테스트가 일정 픽스처를 재사용하도록 `public`으로 상향

## 7. 알려진 이슈 / 제약

- **회차 연장 배치에 분산 락이 없다.** `WithdrawnUserPurgeJob`과 같은 제약(단일 VM 전제). 스케일아웃 시
  두 인스턴스가 동시에 돌면 `ux_reservations_rule_slot`이 중복 저장은 막지만 한쪽 트랜잭션이 롤백될 수
  있다(다음 실행이 메움). `docs/BACKLOG.md` §1.10에 기록.
- **규칙 수정 API가 없다.** 시간·요일·주기를 바꾸려면 삭제 후 재등록해야 한다(미래 회차는 취소되고
  새로 만들어진다). BUILD_PLAN Phase 5 항목에 "수정"이 없어 범위에서 뺐다.
- **타임존이 서버 고정(Asia/Seoul)이다.** 규칙마다 시간대를 저장하지 않으므로 해외 밴드는 지원하지
  않는다. 필요해지면 `recurring_rules`에 컬럼을 추가한다(도메인 모델 변경이라 승인 필요).
- **회차별 cost/note를 등록 시 개별 지정할 수 없다.** 규칙의 값이 모든 회차에 복사된다. 특정 회차만
  다르게 하려면 그 회차를 Phase 4 일정 수정 API로 고친다.
- **한 번에 만드는 회차에 상한(200)이 있다.** `startDate`를 아주 먼 과거로 잡아도 최대 200회차까지만
  만든다(응답·배치 폭주 방지). 정상 사용(지평선 8주)에서는 걸리지 않는다.
- **"연결된 정산 기록" 검증은 Phase 7 이후로 미룸.** 지금은 회차 행·상태 보존까지 검증했다(2장 불일치 3).
- Testcontainers 통합 테스트는 이 PC에서 실행 불가 — CI로만 확인.

## 8. 커밋 · CI

- 브랜치 `phase-5-recurring` → **[PR #NN](https://github.com/Yekapark/bandApp/pull/NN)** (`main` 대상)
- 커밋 (기능 단위):
  1. `feat(recurring): recurring_rules 테이블·FK 마이그레이션 (V5)`
  2. `feat(recurring): 반복 규칙 엔티티 + 회차 계산기(주간/격주/월간)`
  3. `feat(reservation): 회차 생성·취소 창구 + 벌크 usageCount`
  4. `feat(recurring): 규칙 등록·조회·삭제 API`
  5. `feat(recurring): 회차 연장 배치잡`
  6. `test(recurring): 완료 기준 통합 테스트 + 회차 계산 단위 테스트`
  7. `docs(progress): Phase 5 진행 기록`
- CI: [actions/runs/NN](https://github.com/Yekapark/bandApp/actions/runs/NN) — <결과 기입>

## 9. 다음 Phase 예고 — Phase 6 (참석 체크 · 셋리스트)

일정 생성 시 밴드 멤버 전원의 `ReservationAttendance`를 PENDING으로 생성, 본인 참석 상태 변경
(본인 것만), 일정 상세에 참석 현황·집계 포함, 셋리스트 CRUD(곡명·아티스트·참고 링크·순서).
정기 회차도 일반 일정이라 참석 체크가 그대로 붙는다 — 회차 생성 지점(`ReservationDirectoryService.createOccurrences`)에
attendance 생성 훅을 더하면 된다.
