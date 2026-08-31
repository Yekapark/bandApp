# Phase 2 — 밴드 · 초대 · 멤버

## 1. 한 줄 요약

밴드를 만들고(생성자가 밴드장), 초대코드·초대 링크로 멤버를 받아들이고, 멤버를
내보내거나(자발적 탈퇴·밴드장 추방) 밴드장을 다른 사람에게 넘기고(위임), 일정 등록
권한 모드를 설정하는 기능을 붙였다. 초대코드 입력과 인증 요청에는 Redis 기반 분당 제한을 걸었다.

## 2. 이 Phase의 목표 (`docs/BUILD_PLAN.md` 기준)

- 밴드 생성 (생성자가 자동으로 LEADER)
- 초대코드 발급/재발급/무효화 (밴드장만), 초대코드로 밴드 참여
- 초대 딥링크 — 코드가 담긴 링크 발급, 앱 미설치 시 스토어로 유도하는 웹 랜딩 페이지
  (Universal Link / App Link 대응)
- 멤버 목록 조회, 자발적 탈퇴, 밴드장의 멤버 추방
- 밴드장 위임 — 기존 LEADER는 MEMBER로 강등, 대상은 LEADER로 승격 (원자적)
- 밴드 설정 변경 API (`reservationPermission`)
- 초대코드 입력 시도에 Redis 기반 레이트리밋 (계정/IP 기준 분당 제한)

**완료 기준**: 만료·사용완료·revoked 코드가 각각 올바르게 거부되고, 권한 없는 사용자의
밴드 설정 변경이 403으로 차단되며, 위임 후 밴드에 LEADER가 정확히 한 명 남는 테스트가 통과한다.

추가로 처리한 것: `docs/BACKLOG.md` §1.8 — "초대코드 레이트리밋 인프라를 만들 때
`/api/v1/auth/**` 4개 엔드포인트도 함께 넣는다"를 이번에 반영했다.

## 3. 무엇을 만들었나

### 3.1 데이터베이스 — `src/main/resources/db/migration/V2__band.sql`

테이블 3개.

| 테이블 | 역할 | 핵심 제약 |
|---|---|---|
| `bands` | 밴드. `leader_id`는 현재 밴드장 캐시, `reservation_permission` 기본 `LEADER_ONLY` | `reservation_permission` CHECK |
| `band_members` | 참여 이력. 탈퇴/추방은 `left_at` 기록(소프트), 재가입은 새 행 | 아래 부분 유니크 인덱스 2개 |
| `band_invites` | 초대코드. 재발급 시 기존 코드 `revoked` 처리 | `code` 전역 유니크 |

`band_members`의 두 불변식은 **DB 부분 유니크 인덱스**로 강제한다(서비스 코드 버그가 있어도 깨지지 않게):

- `ux_band_members_active` — `(band_id, user_id) WHERE left_at IS NULL`: 한 밴드에 활성 멤버십 하나
- `ux_band_members_single_leader` — `(band_id) WHERE left_at IS NULL AND role = 'LEADER'`: 밴드당 활성 밴드장 정확히 하나

> **도메인 모델 추가 (승인됨, 2026-09-01)**: `band_invites`에 `created_at` 컬럼을 추가했다.
> 원래 `BUILD_PLAN.md` §3의 `BandInvite`에는 없던 필드로, "활성 코드 최신순 조회"와 감사 로그
> 용도다. 지시자 승인 후 `BUILD_PLAN.md` §3 모델에도 반영했다.

### 3.2 밴드 도메인 — `src/main/java/com/yeka/bandapp/band/`

- **엔티티** `entity/` — `Band`(→ `BaseTimeEntity` 상속으로 `createdAt`), `BandMember`(시간 필드가
  `joinedAt`이라 `BaseTimeEntity` 미상속), `BandInvite`, 그리고 enum `ReservationPermission`,
  `BandMemberRole`. 상태 변경은 setter가 아니라 의미 있는 메서드로만
  (`band.handOverLeadership(id)`, `member.demoteToMember()`, `invite.revoke()`).
- **저장소** `repository/` — Spring Data 파생 쿼리 + 두 개의 조건부 대량 UPDATE:
  - `BandInviteRepository.revokeActiveByBandId` — 밴드의 활성 코드 일괄 `revoked`
  - `BandInviteRepository.tryConsume` — `used_count`를 `WHERE ... used_count < max_uses` 조건과 함께
    1 증가. 동시 참여에서 `maxUses`를 정확히 지키기 위한 원자적 처리(0행 반환 = 거부/경합 패배)
- **서비스** `service/`
  - `BandAccessGuard` — "이 사용자가 이 밴드의 활성 멤버인가 / 밴드장인가"를 한곳에서 검증.
    모든 밴드 API가 여기를 거쳐 타 밴드 데이터 접근을 막는다. 밴드가 없든 비멤버든 똑같이
    `NOT_BAND_MEMBER`(403) — 존재 여부를 비멤버에게 알리지 않는다.
  - `BandService` — 생성/조회/설정 변경
  - `BandMemberService` — 목록/탈퇴/추방/위임. 위임은 "기존 밴드장 강등 → flush → 대상 승격"
    순서로, `ux_band_members_single_leader`에 걸리지 않게 한 트랜잭션에서 처리
  - `BandInviteService` — 발급/재발급/무효화/현재 코드 조회/참여. 참여 시 계정·IP 레이트리밋
  - `InviteCodeGenerator` — 8자, `A–Z`에서 `I·O` 제외 + `2–9` (총 32자). 혼동 문자(`0/O`, `1/I`) 배제
- **`DeeplinkProperties`** — `app.deeplink.*`. 초대 링크·랜딩·검증 파일에 쓰이는 공개 주소와 앱 식별자
- **컨트롤러** `controller/` — 아래 API 목록 참조. `InviteLandingController`만 무인증(`@RestController`지만 HTML/JSON 응답)

### 3.3 공통 장치 — `src/main/java/com/yeka/bandapp/common/`

- `ratelimit/RedisRateLimiter` — Redis 고정 윈도우(1분) 카운터. `tryAcquire(bucket, key, limit)` /
  초과 시 429를 던지는 `check(...)`. 버킷은 대상 종류, 키는 userId 또는 IP.
- `ratelimit/RateLimitProperties` — `app.ratelimit.*` (초대참여 user/IP, 인증 IP)
- `ratelimit/AuthRateLimitInterceptor` + `config/WebConfig` — `/api/v1/auth/**`의 POST에
  엔드포인트별 IP 분당 제한 (BACKLOG §1.8: 무차별 대입·이메일 열거·카카오 API 남용 완화)
- `web/ClientIp` — `X-Forwarded-For` 첫 홉 우선(운영은 Nginx 뒤), 없으면 소켓 주소.
  레이트리밋 키 용도로만 쓴다(인가 판단엔 안 씀)
- `exception/ErrorCode` — Phase 2 코드 추가: `TOO_MANY_REQUESTS`, `BAND_NOT_FOUND`,
  `NOT_BAND_MEMBER`, `NOT_BAND_LEADER`, `MEMBER_NOT_FOUND`, `LEADER_MUST_DELEGATE_BEFORE_LEAVING`,
  `CANNOT_KICK_SELF`, `CANNOT_DELEGATE_TO_SELF`, `INVITE_NOT_FOUND`, `INVITE_EXPIRED`,
  `INVITE_REVOKED`, `INVITE_EXHAUSTED`, `ALREADY_BAND_MEMBER`

### 3.4 사용자 도메인에 추가 — `user/service/UserDirectoryService`

다른 도메인이 사용자 정보를 볼 때 쓰는 읽기 전용 창구(도메인 간 참조는 저장소가 아니라
서비스를 통한다는 컨벤션). `existsActive(userId)`, `summariesOf(userIds)`(멤버 목록의 이름 표시용,
탈퇴/익명화 사용자도 "탈퇴한 사용자"로 포함).

### 3.5 API 목록

인증 필요(Bearer). `{bandId}`는 경로 변수.

| 메서드 · 경로 | 설명 | 권한 |
|---|---|---|
| `POST /api/v1/bands` | 밴드 생성 (→ 201, 생성자 LEADER) | 인증 사용자 |
| `GET /api/v1/bands/{bandId}` | 밴드 조회 | 밴드 멤버 |
| `PUT /api/v1/bands/{bandId}/settings` | `reservationPermission` 변경 | 밴드장 |
| `POST /api/v1/bands/{bandId}/leader` | 밴드장 위임 (`{"newLeaderUserId":N}`) | 밴드장 |
| `GET /api/v1/bands/{bandId}/members` | 멤버 목록 (가입순, 역할·이름 포함) | 밴드 멤버 |
| `POST /api/v1/bands/{bandId}/members/leave` | 자발적 탈퇴 (→ 204) | 밴드 멤버(밴드장은 409) |
| `DELETE /api/v1/bands/{bandId}/members/{targetUserId}` | 추방 (→ 204) | 밴드장 |
| `POST /api/v1/bands/{bandId}/invites` | 초대코드 발급/재발급 (→ 201, 본문 선택 `maxUses`·`ttlDays`) | 밴드장 |
| `GET /api/v1/bands/{bandId}/invites/current` | 현재 활성 코드 조회 | 밴드장 |
| `DELETE /api/v1/bands/{bandId}/invites/current` | 현재 코드 무효화 (→ 204) | 밴드장 |
| `POST /api/v1/bands/join` | 코드로 참여 (`{"code":"..."}`) | 인증 사용자 · **레이트리밋** |
| `GET /invite/{code}` | 초대 랜딩 페이지 (HTML) | **무인증** |
| `GET /.well-known/apple-app-site-association` | iOS Universal Link 검증 | **무인증** |
| `GET /.well-known/assetlinks.json` | Android App Link 검증 | **무인증** |

### 3.6 설정값 — `application.yml`의 `app` 블록

```yaml
app:
  deeplink:
    base-url:        http://localhost:8080     # 초대 링크·랜딩 공개 주소 (운영은 실도메인)
    scheme:          bandapp                    # 앱 커스텀 스킴 (bandapp://invite/CODE)
    ios-app-id:      ""                         # 예: TEAMID.com.yeka.bandapp (AASA 검증)
    ios-app-store-url / android-play-store-url  # 미설치 폴백 링크
    android-package: com.yeka.bandapp
    android-sha256-cert-fingerprints: ""        # 쉼표 구분, assetlinks.json 검증
  ratelimit:
    invite-join-per-user-per-min: 10
    invite-join-per-ip-per-min:   20
    auth-per-ip-per-min:          20            # /api/v1/auth/** 엔드포인트별 IP 분당
```

## 4. 어떻게 동작하나

### 밴드장은 항상 정확히 한 명

`bands.leader_id`는 빠른 조회용 캐시일 뿐이고, 진실은 `band_members`의 활성 `LEADER` 행이다.
위임은 한 트랜잭션에서 **① 기존 밴드장 → `MEMBER` 강등 후 즉시 flush → ② 대상 → `LEADER` 승격 후 flush
→ ③ `bands.leader_id` 갱신** 순서로 처리한다. 순서를 지키지 않으면 순간적으로 밴드장이 둘이 되어
`ux_band_members_single_leader` 유니크 인덱스에 걸린다. 밴드장은 위임 전에는 탈퇴할 수 없다(409) —
밴드에 밴드장이 사라지는 상태를 막는다.

### 초대코드 참여의 거부 사유 분기

코드로 참여할 때 `revoked → INVITE_REVOKED(410)`, `만료 → INVITE_EXPIRED(410)`,
`소진 → INVITE_EXHAUSTED(409)`, `이미 멤버 → ALREADY_BAND_MEMBER(409)`,
`없는 코드 → INVITE_NOT_FOUND(404)`로 각각 다르게 응답한다. 사용 횟수 증가는 엔티티 setter가 아니라
저장소의 조건부 UPDATE(`tryConsume`)로 하기 때문에, 두 사람이 `maxUses:1` 코드에 동시에 들어와도
정확히 한 명만 성공한다(PostgreSQL READ COMMITTED에서 두 번째 UPDATE는 첫 커밋 후 조건을 다시 평가).

### 초대 딥링크

발급/조회 응답의 `link`는 `{base-url}/invite/{code}` 형태다. 이 링크를 열면:

- **앱 설치됨**: OS가 `/.well-known/` 검증 파일(iOS AASA / Android assetlinks)을 확인하고 앱을 직접 연다.
- **미설치 / 데스크톱 브라우저**: `GET /invite/{code}`가 HTML 랜딩 페이지를 반환한다. 페이지 스크립트가
  `bandapp://invite/CODE`로 앱 실행을 시도하고, 안 열리면 UA에 따라 App Store / Google Play로 보낸다.
  코드는 랜딩에도 크게 표시돼 수동 입력이 가능하다.

이 앱은 링크 라우팅 자체를 구현하지 않는다 — OS의 몫이다. 백엔드는 검증 파일 제공과 폴백 페이지만 담당한다.

### 레이트리밋

Redis에 `ratelimit:{bucket}:{key}:{분}` 카운터를 두고 1분마다 새 윈도우로 넘어간다.

- **초대 참여**: `invite-join:user`(userId 키)와 `invite-join:ip`(IP 키) 둘 다 검사 — 한 계정의 폭주와
  한 IP에서 여러 계정을 돌리는 폭주를 모두 막는다.
- **인증**(`/api/v1/auth/**`): MVC 인터셉터가 POST 요청에 대해 `auth:{경로}` 버킷 + IP 키로 검사.
  엔드포인트별로 예산이 분리돼 `/login` 폭주가 `/signup`을 막지 않는다.

초과하면 공통 포맷의 `429 TOO_MANY_REQUESTS`.

## 5. 직접 확인하는 법

### 사전 준비

Phase 1과 동일. `.env`에 `JWT_SECRET`(32자 이상)이 있어야 앱이 뜬다. Docker Desktop 필요.

### 방법 A — 전체 스택 실행 후 수동 확인 (권장)

```bash
cd bandApp
docker compose up --build -d
# 앱이 뜰 때까지 20~40초. 200 이면 준비 완료
curl -s http://localhost:8080/actuator/health
```

> **주의(Windows Git Bash)**: `curl -d`에 한글이 들어가면 셸이 UTF-8이 아닌 인코딩으로 보내
> `INVALID_INPUT`(요청 본문 해석 불가)이 난다. 아래 예시는 이름을 ASCII로 쓴다.

```bash
B=http://localhost:8080
# 1. 사용자 3명 가입 (리더 / 멤버 / 낯선이). 각 응답의 data.tokens.accessToken 사용
curl -s -XPOST $B/api/v1/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"lead@band.app","password":"pw12345678","name":"Leader"}'

# 2. 밴드 생성 → 201, data.id 가 bandId
curl -s -XPOST $B/api/v1/bands -H "Authorization: Bearer <LEAD>" \
  -H 'Content-Type: application/json' -d '{"name":"Rose Motel"}'

# 3. 초대코드 발급 → 201, data.code / data.link
curl -s -XPOST $B/api/v1/bands/<BID>/invites -H "Authorization: Bearer <LEAD>"

# 4. 멤버가 코드로 참여 → 200
curl -s -XPOST $B/api/v1/bands/join -H "Authorization: Bearer <MEMBER>" \
  -H 'Content-Type: application/json' -d '{"code":"<CODE>"}'

# 5. 낯선이가 밴드 조회 → 403 NOT_BAND_MEMBER
curl -s $B/api/v1/bands/<BID> -H "Authorization: Bearer <STRANGER>"

# 6. 멤버가 설정 변경 시도 → 403 NOT_BAND_LEADER
curl -s -XPUT $B/api/v1/bands/<BID>/settings -H "Authorization: Bearer <MEMBER>" \
  -H 'Content-Type: application/json' -d '{"reservationPermission":"ANYONE"}'

# 7. 밴드장이 멤버에게 위임 → 200. 이후 멤버 목록에서 LEADER 는 정확히 한 명
curl -s -XPOST $B/api/v1/bands/<BID>/leader -H "Authorization: Bearer <LEAD>" \
  -H 'Content-Type: application/json' -d '{"newLeaderUserId":<MEMBER_ID>}'
curl -s $B/api/v1/bands/<BID>/members -H "Authorization: Bearer <MEMBER>"

# 8. 재발급 후 옛 코드로 참여 → 410 INVITE_REVOKED
# 9. maxUses:1 코드로 두 번째 참여 → 409 INVITE_EXHAUSTED
# 10. 랜딩 / 검증 파일
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/invite/<CODE>   # 200
curl -s http://localhost:8080/.well-known/apple-app-site-association
curl -s http://localhost:8080/.well-known/assetlinks.json
```

정리: `docker compose down -v`

### 방법 B — 자동 테스트 (CI에서 확인)

`./gradlew test`. 이 개발 PC에서는 Testcontainers가 안 떠서(메모리 참조: `local-gradlew-test-blocked`)
`main` 대상 PR에서 CI가 돌려야 최종 pass/fail이 나온다. 순수 단위 테스트만 로컬 실행 가능:

```bash
./gradlew test --tests 'com.yeka.bandapp.band.service.InviteCodeGeneratorTest'
```

### 문제 해결

- **가입이 `INVALID_INPUT`**: 위 "주의" 참조 — 셸 인코딩. 이름을 ASCII로.
- **밴드 API가 전부 401**: `Authorization: Bearer <accessToken>` 헤더 누락. access 토큰 유효기간 30분.
- **참여가 갑자기 429**: 레이트리밋 정상 동작. 1분 기다리거나 컨테이너 재시작(Redis 초기화).
- **`ddl-auto validate` 실패로 기동 불가**: `V2__band.sql`과 엔티티 매핑 불일치. 마이그레이션을 고친다(엔티티 아님).

## 6. 실제 검증 기록

### 6.1 순수 단위 테스트 (2026-09-01, 개발 PC)

```
./gradlew test --tests 'com.yeka.bandapp.band.service.InviteCodeGeneratorTest'
BUILD SUCCESSFUL — 3 tests
```

`./gradlew compileJava compileTestJava` 통과.

### 6.2 `docker compose` 전체 스택 수동 검증 (2026-09-01, 개발 PC, Docker Desktop 29.7.2)

`docker compose up --build -d` 후 `/actuator/health` = `UP`. Flyway `V2 band` = success.
`\d band_members`로 부분 유니크 인덱스 2개 + CHECK + FK 확인. 앱 로그에 WARN/ERROR/Exception 없음.

시나리오 스크립트 결과 (기대 = 실제):

| 검증 | 결과 |
|---|---|
| 멤버 2명 코드로 참여 | 200 / 200 |
| 이미 멤버인데 재참여 | 409 `ALREADY_BAND_MEMBER` |
| 낯선이가 밴드 조회 | 403 `NOT_BAND_MEMBER` |
| 멤버가 설정 변경 | 403 `NOT_BAND_LEADER` |
| 밴드장이 설정 변경 | 200, 값 반영 |
| 밴드장이 위임 전 탈퇴 | 409 `LEADER_MUST_DELEGATE_BEFORE_LEAVING` |
| 위임 → 역할 스왑 | `[(Leader, MEMBER), (Mem1, LEADER), (Mem2, MEMBER)]`, **활성 LEADER 수 = 1** |
| 위임 후 옛 밴드장 설정 변경 / 새 밴드장 설정 변경 | 403 / 200 |
| 새 밴드장이 멤버 추방 → 추방된 멤버 조회 | 204 / 403 |
| 재발급 후 옛 코드로 참여 | 410 `INVITE_REVOKED` |
| `maxUses:1` 소진 후 참여 | 409 `INVITE_EXHAUSTED` |
| 없는 코드로 참여 | 404 `INVITE_NOT_FOUND` |
| 랜딩 `/invite/{code}` / 잘못된 코드 | 200 / 404 |
| `apple-app-site-association` / `assetlinks.json` | JSON 정상 (`appID`, `package_name`, 지문 반영) |
| 참여 22회 연타 (분당 20 제한) | 22번째 = 429 |

만료 코드 거부(`INVITE_EXPIRED`)는 과거 만료 시각을 API로 만들 수 없어 통합 테스트
(`BandInviteIntegrationTest.expired_code_is_rejected_as_expired`, 저장소로 과거 만료 코드 주입)에서 검증한다.

### 6.3 CI — 자동 테스트 (2026-09-01, PR #16)

GitHub Actions `build` 잡: `./gradlew build --no-daemon` → `> Task :test` 실행,
`BUILD SUCCESSFUL in 43s`, `:check`/`:build` 통과.
[actions/runs/33408692987](https://github.com/Yekapark/bandApp/actions/runs/33408692987)

테스트 클래스: `BandMemberIntegrationTest` · `BandInviteIntegrationTest` ·
`InviteDeepLinkIntegrationTest` · `AuthRateLimitIntegrationTest` · `InviteCodeGeneratorTest`(단위).

## 7. 알려진 이슈 / 제약

- ~~`band_invites.created_at`은 도메인 모델에 없던 컬럼이다~~ → **승인 완료** (2026-09-01), `BUILD_PLAN.md` §3에 반영.
- **레이트리밋은 고정 윈도우**라 윈도우 경계에서 짧게 최대 2배까지 통과할 수 있다. 무차별 대입·열거를
  늦추는 목적엔 충분하다. 슬라이딩 로그가 필요하면 `RedisRateLimiter`만 교체하면 된다.
- **`X-Forwarded-For` 신뢰**: 현재는 헤더 첫 홉을 그대로 IP로 쓴다. 운영에서 Nginx가 이 헤더를
  재작성한다는 전제이며, 신뢰 프록시 화이트리스트는 Phase 11(배포)에서 넣는다. 레이트리밋 키 전용이라
  위조되어도 인가에는 영향 없다.
- **밴드 삭제/보관 없음**: 밴드장이 위임 없이 마지막 멤버로 남으면 밴드가 "밴드장 1인" 상태로 유지된다.
  빈 밴드 정리 정책은 이번 범위 밖.
- **초대 링크 도메인 미확정**: `app.deeplink.base-url` 기본값은 `localhost:8080`. 실도메인·앱 스토어
  ID·서명 지문은 배포 전 채운다(`phase-02-TODO` 없이 BACKLOG/배포 체크리스트에서 관리).
- Testcontainers 통합 테스트는 이 PC에서 실행 불가 — CI로만 확인(메모리: `local-gradlew-test-blocked`).

## 8. 커밋 · CI

- 브랜치 `phase-2-band` → **PR #16** (`main` 대상)
- 커밋 (기능 단위):
  1. `feat(band): 밴드/멤버/초대 도메인 모델 + V2 마이그레이션`
  2. `feat(ratelimit): Redis 고정 윈도우 레이트리밋 + 인증 엔드포인트 적용`
  3. `feat(band): 밴드 생성·조회·설정 + 멤버 목록·탈퇴·추방·밴드장 위임`
  4. `feat(band): 초대코드 발급·재발급·무효화·참여 + 초대 딥링크`
  5. `test(band): Phase 2 통합·단위 테스트 + 진행 기록`
- CI: [actions/runs/33408692987](https://github.com/Yekapark/bandApp/actions/runs/33408692987) — pass

## 9. 다음 Phase 예고 — Phase 3 (합주실 / Room)

밴드별 합주실 등록/수정/삭제, 주소 → 좌표(네이버 지오코딩, 실패 시 좌표 null 허용),
`usageCount` 내림차순 목록. 타 밴드 합주실 미노출.
