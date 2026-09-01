# Phase 3 — 합주실 (Room)

## 1. 한 줄 요약

밴드마다 합주 장소(합주실)를 등록·수정·삭제하고 목록으로 보는 기능을 붙였다. 주소를 넣으면
네이버 지도 지오코딩으로 좌표를 채우되, 키가 없거나 변환에 실패해도 주소만으로 등록된다.
목록은 많이 쓴 합주실이 위로 오도록 `usageCount` 내림차순으로 정렬한다. 곁들여 "내가 속한
밴드 목록"(`GET /api/v1/bands`) API도 추가했다 — 클라이언트가 밴드 스위처를 그리는 데 필요하다.

## 2. 이 Phase의 목표 (`docs/BUILD_PLAN.md` 기준)

- 합주실 등록/수정/삭제 (밴드 멤버 누구나)
- 주소 입력 시 네이버 지도 지오코딩으로 좌표 변환 후 저장
- 밴드별 합주실 목록 조회 (`usageCount` 내림차순 정렬)

**완료 기준**: 지오코딩 실패 시에도 주소만으로 등록이 가능하며(좌표 null 허용),
다른 밴드의 합주실이 조회되지 않는 테스트가 통과한다.

추가로 처리한 것: `docs/BACKLOG.md` §1.9 — "내가 속한 밴드 목록 API 없음 (`GET /api/v1/bands`),
Phase 3~4 클라이언트 작업 전에 추가 필요"를 이번에 반영했다.

## 3. 무엇을 만들었나

### 3.1 데이터베이스 — `src/main/resources/db/migration/V3__room.sql`

테이블 1개.

| 테이블 | 역할 | 핵심 |
|---|---|---|
| `rooms` | 밴드가 등록해 둔 합주 장소. 밴드별 독립 레코드(같은 물리적 합주실이라도 밴드마다 별개) | 아래 인덱스 2개, 소프트 삭제 |

- `lat`, `lng` 는 `DOUBLE PRECISION` **nullable** — 지오코딩 결과이며 없을 수 있다.
- `address` 도 nullable — 이름만 있는 장소(멤버 집 지하실 등)도 등록할 수 있어야 한다.
- `usage_count` 는 이 합주실로 등록된 일정 수. **Phase 4 일정 등록에서 증가시킨다.** 지금은 항상 0.
- **`deleted_at` 은 도메인 모델에 없던 컬럼이다.** 아래 승인 블록 참조.

인덱스:

- `ix_rooms_band_usage` — `(band_id, usage_count DESC) WHERE deleted_at IS NULL`: 목록 조회 전용.
  완료 기준의 "usageCount 내림차순 정렬"을 인덱스로 뒷받침한다.
- `ux_rooms_band_name_active` — `(band_id, name) WHERE deleted_at IS NULL`: 한 밴드 안에서 합주실
  이름은 유일. 삭제된 행은 제외하므로 지웠던 이름을 다시 쓸 수 있다.

> **도메인 모델 추가 (승인됨, 2026-09-01)**: `rooms` 에 `deleted_at` 컬럼을 추가했다.
> 원래 `BUILD_PLAN.md` §3 의 `Room` 에는 없던 필드다. 하드 삭제로 두면 Phase 4 이후 과거 일정이
> 참조하던 합주실이 사라져 정산·기록이 깨진다. 소프트 삭제로 행을 남겨 두면 과거 일정은 합주실
> 이름·주소를 계속 참조하고, 목록·조회에서는 빠진다. 지시자 승인 후 `BUILD_PLAN.md` §3 모델에도 반영했다.

### 3.2 합주실 도메인 — `src/main/java/com/yeka/bandapp/room/`

- **엔티티** `entity/Room.java` — `Band` 와 같은 스타일(연관관계 매핑 없이 `Long` FK, 정적 팩토리,
  의미 있는 상태 변경 메서드). `BaseTimeEntity` 상속으로 `createdAt` 을 얻는다.
  - `Room.create(...)` 로 생성, `room.update(...)` 로 이름·주소·연락처·메모 교체
  - `room.applyCoordinates(coords)` 는 지오코딩 성공 시에만, `room.clearCoordinates()` 는 주소가
    바뀌었는데 새 좌표를 못 얻었을 때 옛 좌표를 지우는 용도
  - `room.increaseUsage()` 는 Phase 4 예약용(현재 미사용), `room.delete(when)` 은 소프트 삭제
  - `room.belongsTo(bandId)` — 밴드 교차 접근 차단 판정에 쓴다
- **저장소** `repository/RoomRepository.java` — 파생 쿼리에 소프트 삭제 조건(`DeletedAtIsNull`)을
  이름으로 박는다.
  - `findByBandIdAndDeletedAtIsNullOrderByUsageCountDescIdAsc` — 목록. `usageCount` 동률일 때
    순서가 흔들리지 않도록 `id` 오름차순을 2차 정렬 키로 둔다
  - `findByIdAndDeletedAtIsNull`, `existsByBandIdAndNameAndDeletedAtIsNull`
- **지오코딩 연동** `naver/` — 카카오 연동(`user/kakao/`)과 똑같은 4-파일 구조.
  - `GeocodingClient` (인터페이스) — 이 인터페이스가 지도 API 와의 **유일한 경계**. 나머지 코드는
    네이버를 모른다. **예외를 던지지 않고** `Optional<Coordinates>` 로만 결과를 낸다 —
    키 미설정·4xx/5xx·타임아웃·결과 0건을 모두 `Optional.empty()` 로 같게 취급한다.
    (카카오 클라이언트와 다른 유일한 지점이며, 그 이유가 javadoc 에 적혀 있다.)
  - `NaverGeocodingClient` (`@Component`) — NCP Maps Geocoding
    (`GET /map-geocode/v2/geocode?query=주소`) 호출. `spring-web` 에 이미 있는 `RestClient` 로
    connect/read 타임아웃을 걸어 쓴다(의존성 추가 없음). 응답은 `JsonNode` 로 받아
    `addresses[0].y`(위도) / `addresses[0].x`(경도)를 안전하게 꺼낸다. 실패는 전부 `WARN` 로그 + `empty`.
  - `NaverProperties` (`@ConfigurationProperties("app.naver")` record) — `apiBaseUrl`, `clientId`,
    `clientSecret`, 타임아웃. `isConfigured()` 제공.
  - `Coordinates` (record) — `(double lat, double lng)`
- **서비스** `service/RoomService.java` — 모든 메서드 첫 줄이 `accessGuard.requireActiveMember(bandId, userId)`.
  등록/수정/삭제 모두 **밴드 멤버면 누구나** 가능(밴드장 전용이 아니다).
  - 밴드 교차 접근 차단: `roomId` 로 찾은 뒤 `room.belongsTo(bandId)` 가 아니면 `ROOM_NOT_FOUND`.
    경로의 `bandId` 만 믿지 않는다
  - 지오코딩 호출 시점: 등록 시, 그리고 수정에서 **주소가 실제로 바뀐 경우에만**. 주소가 그대로면
    외부 호출을 하지 않는다
  - 지오코딩 호출 직전 계정 단위 분당 제한(`geocode:user` 버킷) — 외부 API 무료 한도를 한 계정이
    태우는 것을 막는다. 초과 시 429
  - 이름 중복은 `ROOM_NAME_DUPLICATED`(409). DB 유니크 인덱스는 최후 방어선
- **컨트롤러** `controller/RoomController.java` — `@RequestMapping("/api/v1/bands/{bandId}/rooms")`.
  수정은 `PATCH` 미지원 클라이언트 대비 `PUT`(전체 교체).

### 3.3 공통 장치 — `src/main/java/com/yeka/bandapp/common/`

- `exception/ErrorCode` — Phase 3 코드 추가: `ROOM_NOT_FOUND`(404), `ROOM_NAME_DUPLICATED`(409).
  **지오코딩 실패는 예외가 아니므로 에러코드가 없다**(좌표 없이 등록 성공).
- `ratelimit/RateLimitProperties` — `geocodePerUserPerMin` 필드 추가(기본 20). 0 이하이면 기본값 복원.

### 3.4 밴드 도메인에 추가 — `GET /api/v1/bands` (내가 속한 밴드 목록)

- `band/dto/MyBandListResponse` — `{bandCount, bands:[{id, name, myRole, memberCount, joinedAt}]}`
- `band/repository/BandMemberRepository` — `findByUserIdAndLeftAtIsNullOrderByJoinedAtAsc`(가입순),
  `countByBandIdAndLeftAtIsNull`(멤버 수). 둘 다 `ix_band_members_user_active` 부분 인덱스를 탄다.
- `band/service/BandService.listMine(userId)` — 활성 멤버십 → 밴드 조회 → 밴드별 멤버 수.
  탈퇴한 밴드는 애초에 안 나온다.
- `band/controller/BandController` — `@GetMapping`(경로 변수 없음) 추가.

### 3.5 API 목록

인증 필요(Bearer). `{bandId}` `{roomId}` 는 경로 변수. 모든 엔드포인트가 밴드 멤버십을 검증한다.

| 메서드 · 경로 | 설명 | 권한 |
|---|---|---|
| `GET /api/v1/bands` | 내가 속한 밴드 목록 (가입순, 내 역할·멤버 수 포함) | 인증 사용자 |
| `POST /api/v1/bands/{bandId}/rooms` | 합주실 등록 (→ 201) | 밴드 멤버 |
| `GET /api/v1/bands/{bandId}/rooms` | 합주실 목록 (`usageCount` 내림차순) | 밴드 멤버 |
| `GET /api/v1/bands/{bandId}/rooms/{roomId}` | 합주실 상세 | 밴드 멤버 |
| `PUT /api/v1/bands/{bandId}/rooms/{roomId}` | 합주실 수정 (전체 교체) | 밴드 멤버 |
| `DELETE /api/v1/bands/{bandId}/rooms/{roomId}` | 합주실 삭제 (→ 204, 소프트) | 밴드 멤버 |

등록/수정 요청 본문: `{"name": 필수, "address": 선택, "phone": 선택, "memo": 선택}`.

### 3.6 설정값 — `application.yml` 의 `app` 블록

```yaml
app:
  naver:
    api-base-url: https://maps.apigw.ntruss.com   # NCP 콘솔 안내 주소로 코드 수정 없이 교체 가능
    client-id:     ${NAVER_MAP_CLIENT_ID:}        # 비우면 지오코딩만 건너뛰고 좌표 없이 등록된다
    client-secret: ${NAVER_MAP_CLIENT_SECRET:}
    connect-timeout: PT2S
    read-timeout:    PT3S
  ratelimit:
    geocode-per-user-per-min: ${RL_GEOCODE_USER:20}   # 합주실 등록/수정 시 지오코딩 호출 계정당 분당 상한
```

`docker-compose.yml` 의 app 서비스에 `NAVER_MAP_CLIENT_ID` / `NAVER_MAP_CLIENT_SECRET` 를 전달하도록
추가했고, `.env.example` 에 두 변수와 "비우면 좌표 없이 주소만 저장된다"는 설명을 넣었다.
카카오와 같은 "선택 외부 서비스, 없으면 해당 기능만 degrade" 패턴이다.

## 4. 어떻게 동작하나

### 지오코딩 실패가 등록을 막지 않는다 (완료 기준 ①)

합주실 등록 흐름은 **① 멤버십 검증 → ② 이름 중복 검사 → ③ `Room` 생성 → ④ 주소가 있으면
지오코딩 시도 → ⑤ 저장** 이다. ④ 는 성공하면 좌표를 채우고, 실패하면(키 미설정, 네이버 4xx/5xx,
타임아웃, "그런 주소 없음") 그냥 넘어간다. `GeocodingClient` 가 예외를 던지지 않고 `Optional.empty()`
하나로만 실패를 표현하기 때문에, 서비스는 "좌표를 얻었는가"만 보면 된다. 그래서 좌표가 비어도
등록은 항상 201 이고 응답의 `lat`/`lng` 만 `null` 이다.

단, 지오코딩 **호출 자체가 레이트리밋에 걸리면** 429 로 등록이 거부된다. 좌표 없이 조용히 저장하면
사용자가 "왜 지도에 안 뜨지"를 알 수 없어서, 남용 상황만큼은 명시적으로 알린다.

### 다른 밴드의 합주실은 보이지 않는다 (완료 기준 ②)

두 겹으로 막는다.

1. **목록·등록**: `RoomService` 의 모든 메서드가 `BandAccessGuard.requireActiveMember(bandId, userId)`
   로 시작한다. 그 밴드의 활성 멤버가 아니면(비멤버든, 밴드가 없든) 똑같이 `403 NOT_BAND_MEMBER` —
   존재 여부를 알려주지 않는다.
2. **상세·수정·삭제**: `roomId` 로 합주실을 찾은 뒤 `room.belongsTo(bandId)` 를 확인한다. 다른 밴드의
   `roomId` 를 자기 밴드 경로(`/api/v1/bands/{내 밴드}/rooms/{남의 room}`)에 끼워 넣어도
   `404 ROOM_NOT_FOUND` 다.

### 수정 시 주소가 바뀐 경우에만 재지오코딩

`PUT` 은 전체 교체다. 서비스는 새 주소와 기존 주소를 비교해서, **다를 때만** 지오코딩을 다시 호출한다
(이름만 바꾸면 외부 호출 0회). 주소가 바뀌었는데 새 좌표를 못 얻으면 옛 주소의 좌표가 남지 않도록
`lat`/`lng` 를 비운다.

### 소프트 삭제

`DELETE` 는 행을 지우지 않고 `deleted_at` 에 시각을 찍는다. 목록·상세 쿼리는 전부 `deleted_at IS NULL`
조건이라 삭제된 합주실은 사라진다. 이름 유니크 인덱스도 `WHERE deleted_at IS NULL` 이라, 지웠던
이름으로 다시 등록할 수 있다. Phase 4 이후 과거 일정은 삭제된 합주실 행을 그대로 참조한다.

### "내가 속한 밴드 목록"

`GET /api/v1/bands` 는 `band_members` 에서 내 활성 행(`left_at IS NULL`)을 가입순으로 읽고, 각 행의
`band_id` 로 밴드와 멤버 수를 채운다. 탈퇴/추방된 밴드는 활성 행이 없으니 자동으로 빠진다.
응답의 `myRole` 은 그 밴드에서의 내 역할(`LEADER`/`MEMBER`)이다.

## 5. 직접 확인하는 법

### 사전 준비

Phase 2 와 동일. `.env` 에 `JWT_SECRET`(32자 이상)이 있어야 앱이 뜬다. Docker Desktop 필요.
`NAVER_MAP_CLIENT_ID`/`SECRET` 은 **비워 둬도 된다** — 그 경우 좌표 없이 주소만 저장된다.

### 방법 A — 전체 스택 실행 후 수동 확인 (권장)

```bash
cd band
docker compose up --build -d
curl -s http://localhost:8080/actuator/health          # {"status":"UP"}
docker compose logs app | grep -i "V3"                  # Migrating ... to version "3 - room" / Successfully applied
docker compose exec postgres psql -U bandapp -d bandapp -c '\d rooms'   # 인덱스 2개(ix_rooms_band_usage, ux_rooms_band_name_active) 확인
```

> **주의(Windows Git Bash)**: `curl -d` 에 한글이 들어가면 셸 인코딩 때문에 `INVALID_INPUT` 이 날 수
> 있다. 아래 예시는 이름·주소를 ASCII 로 쓴다.

```bash
B=http://localhost:8080
# 가입 3명 (리더 / 멤버 / 낯선이) — 각 응답의 data.tokens.accessToken 사용
curl -s -XPOST $B/api/v1/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"lead@band.app","password":"pw12345678","name":"Leader"}'
# 밴드 생성 → data.id 가 <BID>, 초대코드 발급 → data.code, 멤버가 join

# 1. 합주실 등록 (NAVER 키 미설정) → 201, lat/lng = null       ← 완료 기준 ①
curl -s -XPOST $B/api/v1/bands/<BID>/rooms -H "Authorization: Bearer <LEAD>" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Sound Box A","address":"Seoul Mapo-gu Wausan-ro 123","phone":"02-1111-2222"}'

# 2. 멤버도 등록 가능 → 201
curl -s -XPOST $B/api/v1/bands/<BID>/rooms -H "Authorization: Bearer <MEMBER>" \
  -H 'Content-Type: application/json' -d '{"name":"Basement 9"}' -o /dev/null -w '%{http_code}\n'

# 3. 낯선이가 목록 조회 → 403 NOT_BAND_MEMBER                    ← 완료 기준 ②
curl -s $B/api/v1/bands/<BID>/rooms -H "Authorization: Bearer <STRANGER>"

# 4. 다른 밴드의 roomId 를 자기 밴드 경로로 조회 → 404 ROOM_NOT_FOUND   ← 완료 기준 ②
curl -s $B/api/v1/bands/<다른 BID>/rooms/<이 room id> -H "Authorization: Bearer <다른 밴드장>"

# 5. 수정(PUT) → 200 / 삭제(DELETE) → 204 / 삭제 후 조회 → 404 / 같은 이름 재등록 → 201
# 6. 이름 중복 → 409 ROOM_NAME_DUPLICATED
# 7. 내가 속한 밴드 목록
curl -s $B/api/v1/bands -H "Authorization: Bearer <LEAD>"       # bandCount, bands[].myRole

# 8. (선택) .env 에 NAVER_MAP_CLIENT_ID/SECRET 을 채우고 docker compose up -d 로 재기동하면
#    같은 등록에서 lat/lng 이 실제 좌표로 채워진다.
```

정리: `docker compose down -v`

### 방법 B — 자동 테스트

`./gradlew test`. 이 개발 PC 에서는 Testcontainers 가 안 떠서(메모리: 로컬 Testcontainers/Docker 이슈)
`main` 대상 PR 에서 CI 가 돌려야 최종 pass/fail 이 나온다. 컨테이너 없이 로컬에서 돌릴 수 있는 것:

```bash
./gradlew test --tests 'com.yeka.bandapp.room.naver.NaverGeocodingParseTest'
./gradlew compileJava compileTestJava
```

### 문제 해결

- **합주실 등록이 `INVALID_INPUT`**: 위 "주의" 참조 — 셸 인코딩. 이름/주소를 ASCII 로.
- **등록은 되는데 `lat`/`lng` 이 계속 null**: `NAVER_MAP_CLIENT_ID`/`SECRET` 미설정이 정상 동작이다.
  값을 넣고 앱을 재기동하면 그때부터 채워진다. 넣었는데도 null 이면 `docker compose logs app | grep -i naver`
  로 `WARN` 로그(키 오류/도메인 변경 등)를 확인한다.
- **등록이 갑자기 429**: 지오코딩 레이트리밋(계정당 분당 20회 기본). 1분 기다리거나 컨테이너 재시작.
- **`ddl-auto validate` 실패로 기동 불가**: `V3__room.sql` 과 `Room` 엔티티 매핑 불일치. 마이그레이션을 고친다(엔티티 아님).

## 6. 실제 검증 기록

### 6.1 순수 단위 테스트 (2026-09-01, 개발 PC)

```
./gradlew test --tests 'com.yeka.bandapp.room.naver.NaverGeocodingParseTest' --rerun-tasks
BUILD SUCCESSFUL — 4 tests
```

`./gradlew compileJava compileTestJava` 통과.

### 6.2 `docker compose` 전체 스택 수동 검증 (2026-09-01, 개발 PC, Docker 29.3.1 / Compose v5.1.0)

`docker compose up --build -d` 후 `/actuator/health` = `UP`. Flyway `V3 room` = `Successfully applied`.
`\d rooms` 로 인덱스 2개 + FK 2개 확인. 앱 로그에 WARN/ERROR/Exception 없음.

시나리오 결과 (기대 = 실제):

| 검증 | 결과 |
|---|---|
| **NAVER 키 미설정 상태로 합주실 등록** | **201, `lat`=`null` `lng`=`null`, `address` 보존** ← 완료 기준 ① |
| 초대로 들어온 멤버가 합주실 등록 | 201 |
| **낯선이가 합주실 목록 조회** | **403 `NOT_BAND_MEMBER`** ← 완료 기준 ② |
| **다른 밴드의 roomId 를 자기 밴드 경로로 조회** | **404 `ROOM_NOT_FOUND`** ← 완료 기준 ② |
| 멤버가 목록 조회 | 200, `roomCount`=2 |
| 합주실 수정 (PUT, 이름 변경) | 200 |
| 이름 중복 등록 | 409 `ROOM_NAME_DUPLICATED` |
| 삭제 → 상세 조회 → 같은 이름 재등록 | 204 / 404 / 201 |
| `GET /api/v1/bands` (밴드장) | `bandCount`=1, `bands[0].myRole`=`LEADER`, `memberCount`=1 |

`usageCount` 내림차순 정렬과 "주소 안 바뀌면 재지오코딩 안 함", "주소 바뀌고 변환 실패 시 옛 좌표
제거"는 통합 테스트(`RoomIntegrationTest`, `FakeGeocodingClient` 의 호출 횟수로 검증)에서 다룬다 —
`docker compose` 로는 usageCount 를 올릴 수단(Phase 4 예약)이 아직 없기 때문이다.

### 6.3 CI — 자동 테스트

_(PR 생성 후 채운다.)_ 브랜치 `phase-3-room` → `main` PR. GitHub Actions `build` 잡이
Testcontainers 통합 테스트를 돌린다.

테스트 클래스:
- `RoomIntegrationTest` — 완료 기준 2건 + 지오코딩 성공/실패/미호출, 재지오코딩 조건, 소프트 삭제,
  이름 중복, 멤버 권한
- `MyBandListIntegrationTest` — 내가 속한 밴드만·역할·멤버 수, 탈퇴 시 목록에서 제외
- `NaverGeocodingParseTest` — 네이버 응답 파싱 (컨테이너 불필요 단위 테스트)

## 7. 알려진 이슈 / 제약

- **`rooms.deleted_at` 은 도메인 모델에 없던 컬럼이다** → **승인 완료** (2026-09-01), `BUILD_PLAN.md` §3 에 반영.
- **지오코딩 정확도는 검증하지 않는다.** 네이버가 돌려준 첫 번째 주소 후보의 좌표를 그대로 쓴다.
  잘못된 주소를 넣으면 엉뚱한 좌표가 붙을 수 있다. 클라이언트에서 지도 핀을 사용자가 확인·보정하는
  UX 가 필요하다(Flutter 트랙).
- **지오코딩 레이트리밋도 고정 윈도우**라 윈도우 경계에서 짧게 최대 2배까지 통과할 수 있다
  (Phase 2 의 레이트리밋과 같은 한계). 무료 한도 보호 목적엔 충분하다.
- **`api-base-url` 은 배포 전 확인 필요.** NCP 가 지오코딩 게이트웨이 도메인을 옮긴 이력이 있어
  (`naveropenapi.apigw.ntruss.com` → `maps.apigw.ntruss.com`), 실제 키를 발급할 때 콘솔이 안내하는
  주소로 `app.naver.api-base-url` 을 맞춰야 한다. 코드 수정 없이 설정으로 바꿀 수 있다.
- **합주실 등록에 밴드 단위 개수 제한이 없다.** 멤버가 합주실을 무한 생성할 수 있다(초대 참여
  레이트리밋과 달리 등록 자체엔 제한 없음). 남용 신호가 보이면 Phase 8 의 레이트리밋 인프라로 추가.
- **`GET /api/v1/bands` 의 멤버 수는 밴드마다 count 쿼리를 한 번씩 돈다.** 한 사람이 속한 밴드 수가
  많지 않아 실질 문제는 없으나, 필요해지면 group-by 한 방으로 바꿀 수 있다.
- Testcontainers 통합 테스트는 이 PC 에서 실행 불가 — CI 로만 확인.

## 8. 커밋 · CI

- 브랜치 `phase-3-room` → **PR (main 대상)**
- 커밋 (기능 단위):
  1. `feat(room): 합주실 도메인 모델 + V3 마이그레이션`
  2. `feat(room): 네이버 지오코딩 클라이언트 (실패 시 좌표 null)`
  3. `feat(room): 합주실 등록·수정·삭제·목록 API`
  4. `feat(band): 내가 속한 밴드 목록 API (BACKLOG §1.9)`
  5. `test(room): Phase 3 통합·단위 테스트 + 진행 기록`
- CI: _(PR 링크·run 링크를 여기 채운다)_

## 9. 다음 Phase 예고 — Phase 4 (일정 등록)

`Band.reservationPermission` 에 따른 권한 분기와 초기 status 결정, 밴드장의 승인/거절
(`APPROVAL_REQUIRED` 모드), 일정 수정/취소, 기간별 목록 조회(캘린더용). 일정 등록 시 해당 Room 의
`usageCount` 증가. **겹침 경고** — 등록/수정 응답에 같은 밴드의 겹치는 일정 목록을 포함하되,
저장은 정상 수행하고 이를 이유로 거부하지 않는다.
