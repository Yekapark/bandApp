# Phase 1 — 인증 (이메일 / 카카오 / JWT / 탈퇴)

## 1. 한 줄 요약

이메일·카카오로 가입/로그인하고, 짧은 수명의 접속 토큰과 갱신 토큰으로 로그인 상태를
유지하며, 앱 안에서 회원 탈퇴(카카오 연결 해제 포함)까지 가능한 인증 기반을 만들었다.

## 2. 이 Phase의 목표 (`docs/BUILD_PLAN.md` 기준)

- 이메일 회원가입 / 로그인
- 카카오 OAuth2 소셜 로그인
- JWT access/refresh 토큰 발급·갱신, refresh 토큰은 Redis 저장
- 회원 탈퇴(계정 삭제) — 소셜 계정 unlink 포함, `deletedAt` 소프트 삭제

**완료 기준**: 가입 → 로그인 → 토큰 갱신 → 탈퇴 전 과정의 통합 테스트가 통과한다.

## 3. 무엇을 만들었나

### 3.1 데이터베이스 — `src/main/resources/db/migration/V1__auth.sql`

첫 실제 테이블 `users`가 생겼다. 컬럼은 도메인 모델(`BUILD_PLAN.md` 3장)을 따르되,
아래 두 가지가 추가됐다. **둘 다 사전 승인된 항목이다.**

| 추가 | 이유 |
|---|---|
| `password_hash` | 이메일 로그인에 필요. 소셜 가입자는 비어 있음(NULL). 비밀번호는 복호화 불가능한 형태(BCrypt 해시)로만 저장하고, 원문은 어디에도 남기지 않는다. |
| (컬럼 아님) 이메일 유일성 규칙을 "탈퇴하지 않은 이메일 가입자" 안에서만 적용 | 탈퇴한 사람이 같은 이메일로 재가입할 수 있어야 하고, 같은 이메일을 쓰는 카카오 계정과 이메일 계정이 충돌 없이 공존해야 하기 때문 |

`social_provider`가 비어 있으면 "이메일 가입", `KAKAO`면 카카오 가입이다.

### 3.2 인증 공통 장치 — `src/main/java/.../common/security/`

| 파일 | 하는 일 |
|---|---|
| `SecurityConfig` | 어떤 주소가 로그인 없이 열려 있고(가입·로그인·상태점검·API문서), 어떤 주소가 토큰을 요구하는지 결정 |
| `JwtTokenProvider` | 접속 토큰(access)·갱신 토큰(refresh)을 발급하고 검증 |
| `JwtAuthenticationFilter` | 요청 헤더의 토큰을 읽어 "이 요청은 몇 번 사용자다"를 확정 |
| `RefreshTokenStore` | 갱신 토큰을 Redis에 저장/삭제. 기기(세션)별로 관리 |
| `AccessTokenBlocklist` | 탈퇴한 사용자의 접속 토큰을 만료 전이라도 즉시 막는 차단 목록 |
| `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` | 인증 실패(401)·권한 없음(403)도 다른 API와 똑같은 응답 형식으로 내려줌 |

### 3.3 사용자 도메인 — `src/main/java/.../user/`

- `entity/User` — 사용자 레코드. 상태 변경은 `withdraw()`(탈퇴), `anonymize()`(개인정보 파기)
  같은 의미 있는 메서드로만 한다.
- `service/AuthService` — 가입·로그인·카카오로그인·토큰갱신·로그아웃
- `service/UserAccountService` — 내 정보 조회, 탈퇴, 그리고 보관기간 지난 계정의 개인정보 파기
- `kakao/KakaoClient` (+ `KakaoApiClient`) — 카카오와 통신하는 유일한 창구.
  실제 호출은 `KakaoApiClient`, 테스트는 가짜 구현으로 대체
- `schedule/WithdrawnUserPurgeJob` — 매일 새벽 4시 30분, 탈퇴 후 90일 지난 계정의
  이메일·이름·카카오번호를 지운다(계정 레코드 자체는 남겨 게시글·정산 기록의 작성자 표시를 유지)
- `controller/AuthController`, `controller/UserController` — 아래 API

### 3.4 API 목록

| 메서드 · 경로 | 인증 | 설명 |
|---|---|---|
| `POST /api/v1/auth/signup` | 불필요 | 이메일 가입. 성공 시 바로 토큰 발급(자동 로그인) |
| `POST /api/v1/auth/login` | 불필요 | 이메일 로그인 |
| `POST /api/v1/auth/kakao` | 불필요 | 카카오 로그인. 앱이 카카오 SDK로 받은 토큰을 넘김 |
| `POST /api/v1/auth/refresh` | 불필요 | 갱신 토큰으로 새 토큰 한 쌍을 받음 |
| `POST /api/v1/auth/logout` | 불필요 | 갱신 토큰을 무효화 |
| `GET /api/v1/users/me` | 필요 | 내 정보 |
| `POST /api/v1/users/me/withdraw` | 필요 | 회원 탈퇴 (이메일 계정은 비밀번호 재확인) |

### 3.5 설정값 — `application.yml`의 `app` 블록

- `app.jwt.secret` — 토큰 서명 키. **운영에서는 반드시 환경변수로 주입**하며 기본값이 없다
  (없으면 앱이 기동에 실패하도록 일부러 그렇게 두었다).
- `app.jwt.access-token-ttl` = 30분, `refresh-token-ttl` = 14일
- `app.kakao.app-id`, `app.kakao.admin-key` — 비어 있어도 앱은 뜨고, 이때 카카오 로그인만
  "설정 안 됨(503)"으로 응답한다.
- `app.withdrawal.retention-days` = 90 — 탈퇴 후 개인정보 보관 기간

### 3.6 의존성 추가 (`build.gradle.kts`)

- `spring-boot-starter-security` — 인증/인가 프레임워크
- `io.jsonwebtoken:jjwt-*` (0.12.6) — JWT 토큰 라이브러리
- `spring-security-test` — 테스트용
- `spring-boot-configuration-processor` — 설정값 자동완성(빌드에만 영향, 실행 산출물엔 미포함)

## 4. 어떻게 동작하나

### 로그인 상태 유지 (토큰 2종)

- **접속 토큰(access)**: 모든 API 요청에 붙인다. 수명 30분. 서버에 저장하지 않고
  서명만으로 검증한다(빠름).
- **갱신 토큰(refresh)**: 접속 토큰이 만료되면 이걸로 새 토큰을 받는다. 수명 14일.
  Redis에 저장되며, 기기마다 하나씩 관리된다.

### 갱신 토큰 회전 (탈취 대비)

`refresh`를 호출하면 **쓰던 갱신 토큰은 즉시 폐기되고 새것이 나온다.** 그래서 누가
갱신 토큰을 훔쳐 써도 진짜 사용자가 한 번 갱신하는 순간 훔친 토큰은 무효가 된다.
이미 폐기된 토큰이 다시 들어오면 "탈취 정황"으로 보고 그 사용자의 **모든 기기 세션을 끊는다.**

### 탈퇴

1. 이메일 계정이면 비밀번호를 한 번 더 확인한다.
2. `deletedAt`을 찍어 즉시 로그인·API 사용을 차단한다(접속 토큰도 차단 목록에 올려 즉시 무효).
3. 모든 기기의 갱신 토큰을 Redis에서 지운다.
4. 카카오 계정이면 카카오에 "연결 해제"를 요청한다.
   - **카카오 연결 해제가 실패해도 탈퇴 자체는 성공시킨다.** 카카오 장애 때문에 탈퇴가
     막히면 앱스토어 심사에서 거절되기 때문이다. 실패는 로그로 남긴다.
5. 90일 뒤 새벽 배치가 이메일·이름·카카오번호를 삭제한다.

## 5. 직접 확인하는 법

### 사전 준비

1. Docker Desktop 실행
2. `.env` 파일에 `JWT_SECRET` 한 줄이 있어야 한다(없으면 앱이 안 뜬다).
   `.env.example`을 복사해서 쓰면 이미 들어 있다:
   ```bash
   cp .env.example .env
   ```
   (카카오 로그인까지 실제로 확인하려면 `KAKAO_APP_ID`, `KAKAO_ADMIN_KEY`도 채운다.
    안 채워도 이메일 로그인 전 과정은 확인된다.)

### 방법 A — 전체 스택 실행 후 수동 확인 (권장)

```bash
docker compose up --build -d
# 앱이 뜰 때까지 20~40초. 아래가 200 UP 이면 준비 완료
curl -s http://localhost:8080/actuator/health
```

그다음 순서대로:

```bash
B=http://localhost:8080

# 1. 가입 → 201, accessToken/refreshToken 이 담겨 나온다
curl -s -X POST $B/api/v1/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"demo@band.app","password":"pw12345678","name":"데모"}'

# 위 응답의 accessToken 값을 넣어서:
curl -s $B/api/v1/users/me -H "Authorization: Bearer <accessToken>"     # → 200, 내 정보
curl -s $B/api/v1/users/me                                             # → 401, 공통 에러 형식

# refreshToken 값을 넣어서:
curl -s -X POST $B/api/v1/auth/refresh -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refreshToken>"}'                               # → 200, 새 토큰
# 같은 refreshToken 을 한 번 더 → 401 (회전 확인)

# 새로 받은 accessToken 으로:
curl -s -X POST $B/api/v1/users/me/withdraw -H "Authorization: Bearer <newAccessToken>" \
  -H 'Content-Type: application/json' -d '{"password":"pw12345678"}'   # → 204
curl -s -X POST $B/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"demo@band.app","password":"pw12345678"}'              # → 401 (탈퇴 확인)

# 회귀 확인 (Phase 0 기능이 안 깨졌는지)
curl -s -o /dev/null -w '%{http_code}\n' $B/v3/api-docs               # → 200
```

기대 결과 요약: **201 → 200 → 401 → 200 → 401 → 204 → 401**, api-docs 200.

끝나면 `docker compose down`.

### 방법 B — 자동 테스트 (CI에서 확인)

`./gradlew test`는 **이 개발 PC에서는 Docker 환경 문제로 실패한다**(Phase 0 문서 7.1).
자동 테스트 통과 여부는 GitHub Actions(CI)에서 확인한다. 커밋을 푸시하면 자동 실행된다.

### 문제 해결

| 증상 | 원인 / 해결 |
|---|---|
| 앱 컨테이너가 바로 죽음, 로그에 `JWT secret` 관련 오류 | `.env`에 `JWT_SECRET`이 없음. `cp .env.example .env` |
| 카카오 로그인이 항상 503 | `KAKAO_APP_ID` / `KAKAO_ADMIN_KEY` 미설정. 의도된 동작 |
| `docker compose up`이 포트 충돌 | 8080/5432/6379를 쓰는 다른 프로세스 종료 |

## 6. 실제 검증 기록

### 6.1 최초 구현 PC — `docker compose up --build` 수동 확인 (2026-08-31)

| 확인 항목 | 결과 |
|---|---|
| 앱 기동, `/actuator/health` | ✅ 200 UP (db/redis 모두 UP) |
| Flyway `V1__auth.sql` 적용 | ✅ `Successfully applied 1 migration ... now at version v1` |
| `ddl-auto: validate` (엔티티↔DDL 일치) | ✅ 통과 (불일치 시 기동 실패) |
| 가입 → 201 + 토큰, `newUser=true` | ✅ |
| 토큰으로 `/users/me` → 200 / 토큰 없이 → 401 (공통 형식) | ✅ |
| 토큰 갱신 → 200, 이전 refresh 재사용 → 401 | ✅ |
| 탈퇴 → 204 / 탈퇴 후 로그인 → 401 / 탈퇴 전 access → 401 `ACCOUNT_WITHDRAWN` | ✅ |
| 카카오 로그인 (키 미설정) → 503 `KAKAO_NOT_CONFIGURED` | ✅ |
| 중복 이메일 → 409 / 짧은 비밀번호 → 400 + 필드 오류 / 잘못된 JSON → 400 | ✅ |
| 회귀: `/v3/api-docs` | ✅ 200 |
| 부팅 로그에 임의 보안 비밀번호 출력 없음 | ✅ (자동설정 제외 적용됨) |

### 6.2 두 번째 PC — 코드 리뷰 + 정적 검증 (2026-08-31)

`phase-1-auth` 브랜치를 받아 전체 코드를 리뷰하고, Docker 없이 가능한 범위를 확인했다.
발견 사항과 수정은 아래 §7.1 참조.

| 확인 항목 | 명령 | 결과 |
|---|---|---|
| 본문·테스트 컴파일 | `./gradlew compileJava compileTestJava` | ✅ BUILD SUCCESSFUL, 경고 없음 |
| deprecated API 사용 여부 | 컴파일러 `-Xlint` + jar 바이트코드 확인 | ✅ 없음 (`SimpleClientHttpRequestFactory`의 `int` 타임아웃 오버로드는 Spring 6.2.1 기준 deprecated 아님) |
| 스프링 없는 순수 단위 테스트 | `./gradlew test --tests '*JwtTokenProviderTest'` | ✅ 통과 |
| 커밋된 비밀값 | 브랜치 diff 전체 스캔 | ✅ 없음 (`.env.example`은 플레이스홀더, `KAKAO_*` 빈 값) |

### 6.3 두 번째 PC — `docker compose` 전체 스택 수동 검증 (2026-08-31, Docker Desktop 29.7.2 설치 후)

`docker compose up --build`로 app+postgres+redis 기동 후 §5 방법 A 시나리오 실행:

| 확인 항목 | 결과 |
|---|---|
| 앱 기동, `/actuator/health` | ✅ 200 UP (db/redis 모두 UP) |
| Flyway `V1__auth.sql` | ✅ `Successfully applied 1 migration ... now at version v1` |
| `ddl-auto: validate` | ✅ 통과 (기동 성공 = 엔티티↔DDL 일치) |
| 완료 기준 전 과정 (가입→내정보→로그인→갱신→이전refresh재사용→탈퇴→탈퇴후로그인→탈퇴전access) | ✅ **`201 200 401 200 200 401 204 401 401`** (기대치와 정확히 일치) |
| 이전 refresh 재사용 → 401 `REFRESH_TOKEN_INVALID` | ✅ |
| 탈퇴 전 access → 401 `ACCOUNT_WITHDRAWN` | ✅ |
| 카카오 로그인 (키 미설정) → 503 `KAKAO_NOT_CONFIGURED` | ✅ |
| 회귀: `/v3/api-docs` | ✅ 200 |
| **리뷰 수정 R1**: 동일 이메일 동시 가입 10건 | ✅ 정확히 1건 201, 9건 409, 500 없음. 앱 로그에 `처리되지 않은 예외` 없음 (DB 경합이 실제로 발생했고 — `ux_users_email_active` 위반 — 새 catch 블록이 409로 변환) |
| **리뷰 수정 R2**: 탈퇴(소셜 아님) | ✅ 204, `deletedAt` 기록됨 |

### 6.4 CI — 자동 테스트 (2026-08-31, PR #1)

`phase-1-auth` → `main` PR 을 열어 GitHub Actions 실행.

| 시도 | 결과 | 원인 / 조치 |
|---|---|---|
| 1차 (`963afad`) | ❌ 31개 중 19개 실패 | 통합 테스트 전부 `ConnectException`(Postgres). §7.1 R7 참조 |
| 2차 (`9dd16c7`) | ❌ 동일 | Testcontainers 1.21.3 되돌려도 동일 → 버전 문제 아님을 확인 |
| 3차 (`82b2890`) | ✅ **BUILD SUCCESSFUL 45초, 31개 전부 통과** | 싱글턴 컨테이너로 전환 (R7) |

3차 통과로 **완료 기준 충족**: `AuthLifecycleIntegrationTest.full_lifecycle` (가입 201 →
내정보 200 → 로그인 200 → 갱신 200 → 이전 refresh 재사용 401 → 탈퇴 204 → 탈퇴후 로그인
401 → 탈퇴전 access 401) 을 포함한 통합 테스트 8종 + 단위 테스트가 CI 에서 초록불.

> 참고: 로컬 `./gradlew test`(Testcontainers)는 이 개발 PC(Windows + Docker Desktop 29.x,
> containerd 이미지 스토어 ON)에서 named pipe `/info` HTTP 400 으로 여전히 실행 불가하다.
> Docker Desktop 설정에서 "Use containerd for pulling and storing images" 를 끄거나
> "Allow the default Docker socket to be used" 를 켜면 해소된다. `docker compose`·CI 는 정상.

## 7. 알려진 이슈 / 제약

### 7.1 코드 리뷰 결과 (2026-08-31, 두 번째 PC)

수정한 것:

| 커밋 | 내용 |
|---|---|
| `fix(auth): 동시 가입 경합 시 500 대신 409` | `signup`의 존재 선검사와 부분 유니크 인덱스 사이 경합에서 `DataIntegrityViolationException`이 공통 핸들러에 걸려 500이 되던 것을 `EMAIL_ALREADY_REGISTERED`(409)로 변환. 동시 8건 통합 테스트 추가. **§6.3에서 실스택 동시 10건으로 검증 완료.** |
| `refactor(auth): 카카오 unlink 를 트랜잭션 커밋 이후로 이동` | `withdraw()`가 `@Transactional` 안에서 외부 HTTP(최대 5s)를 호출해 DB 커넥션을 붙잡던 것을 `afterCommit`으로 이동. 탈퇴 성공 의미론은 그대로. |
| `docs: Phase 0 문서의 프로젝트 경로 수정` | `E:\project\band` → `C:\band\bandApp`. |
| **R7** `fix(test): 싱글턴 컨테이너로 전환` | `IntegrationTestSupport`가 `@Testcontainers`+`@Container`로 컨테이너를 **클래스마다 start/stop** 했는데, 스프링은 `ApplicationContext`를 **클래스 간에 캐시**한다. 첫 통합 테스트 클래스가 끝나며 컨테이너를 내리면, 캐시된 컨텍스트를 재사용하는 이후 클래스는 죽은 포트를 가리켜 커넥션이 거부됐다(Phase 0은 통합 테스트 클래스가 하나뿐이라 안 드러남 / CI 1차·2차 실패의 원인). 컨테이너를 `static` 블록에서 한 번만 띄우고 JVM 수명 동안 재사용하도록 변경. `@ServiceConnection` → `spring.datasource.*` 명시 주입. |

검토했으나 **의도된 설계로 판단해 두는 것**:

- **`refresh` 재사용 탐지 시 전 기기 세션 종료**: 오래된 refresh 토큰을 가진 누구나 해당
  사용자 세션을 끊을 수 있는 DoS 여지가 있으나, OAuth 2.0 BCP 권고를 따른 트레이드오프다(§4).
- **탈퇴 시 Redis 쓰기가 트랜잭션 롤백에 연동되지 않음**: 롤백 시 access 차단 목록이 최대
  30분 남지만, "탈퇴 안 된 사용자를 잠그는" 안전 방향으로만 실패한다.
- **`/api/v1/auth/**`는 `permitAll`이라 이 경로에서는 access 차단 목록을 검사하지 않음**:
  탈퇴 시 `refreshTokenStore.removeAll()`로 refresh가 이미 무효화되므로 실제 우회 경로는 없다.
- **`POST /users/me/withdraw`** (`DELETE` 아님): `TestRestTemplate`이 본문 있는 DELETE를
  지원하지 않아 완료 기준 테스트가 깨지기 때문. 컨트롤러 주석에 근거 있음.

### 7.2 그 밖의 제약

- **로컬 `./gradlew test`(Testcontainers) 실행 불가 — 이 개발 PC 한정**: Windows + Docker Desktop
  29.x 에서 containerd 이미지 스토어가 켜져 있으면 named pipe `/info` 호출이 HTTP 400 이라
  Testcontainers 가 Docker 환경을 못 찾는다(`docker`·`docker compose` CLI 는 정상, CI 도 정상).
  Docker Desktop 설정에서 containerd 이미지 스토어를 끄거나 default socket 허용을 켜면 해소된다.
  Testcontainers 1.21.3 로 올려도, docker-java 를 3.5.1 로 강제해도 이 400 은 그대로였다
  (라이브러리가 아니라 Docker Desktop 소켓 설정 문제). 검증은 `docker compose` 수동(§6.3) + CI(§6.4).
- **카카오 연결 해제 재시도 없음**: 탈퇴 시 카카오 unlink가 실패하면 로그만 남기고 넘어간다.
  자동 재시도 큐는 배치 인프라가 생기는 Phase 9에서 붙인다. 그때까지는 실패 로그를 보고
  수동 대응한다.
- **계정 연동(account linking) 미지원**: 같은 이메일로 이메일 가입과 카카오 가입을 각각 하면
  별개 계정이 된다. 하나로 합치는 기능은 후속 과제(`BACKLOG.md`).
- **로그인 무차별 대입 방어(레이트리밋) 없음**: Phase 2가 초대코드용 레이트리밋 인프라를
  만들 때 로그인에도 함께 적용한다.
- **`purge` 배치는 단일 서버 전제**: 서버를 여러 대로 늘리면 배치가 중복 실행될 수 있어
  분산 락이 필요하다.
- **남은 사람 작업**은 `docs/progress/phase-01-TODO.md` 참조.

## 8. 커밋 · CI

- 브랜치: `phase-1-auth` → `main` (PR [#1](https://github.com/Yekapark/bandApp/pull/1))
- 구현 커밋: `3aba760` (Security+JWT 기반) · `299cc09` (User 도메인 + `V1__auth.sql`) ·
  `4d5095e` (이메일·카카오 로그인, 토큰 갱신, 탈퇴, 파기 배치) · `a35bc5b` (테스트) · `9207b7f` (문서)
- 리뷰·검증 커밋: `1de539e` (동시 가입 409) · `c648e2f` (unlink afterCommit) ·
  `d062510` (Phase 0 문서 경로) · `48122aa` (리뷰 기록) · `5756345` (`bin/` gitignore) ·
  `033e818` (BUILD_PLAN에 passwordHash) · `7fddfab` (compose 수동 검증) ·
  `7a3b7d2`→`9dd16c7` (Testcontainers 1.21.3 시도 후 되돌림) · **`82b2890` (싱글턴 컨테이너, R7 — CI 통과의 핵심)**
- CI: ✅ **통과** — [Actions run 33402109933](https://github.com/Yekapark/bandApp/actions/runs/33402109933)
  (`82b2890`, `BUILD SUCCESSFUL in 45s`, 31개 테스트 전부 통과). 자동 테스트 통과 판정은 이 결과로 한다.

## 9. 다음 Phase 예고 — Phase 2 (밴드 · 초대 · 멤버)

- 밴드 생성(생성자가 자동으로 LEADER), 초대코드 발급/재발급/무효화
- 초대코드로 참여, 초대 딥링크 + 웹 랜딩
- 멤버 목록, 자발적 탈퇴, 밴드장의 추방, **밴드장 위임**(원자적)
- 밴드 설정(`reservationPermission`) 변경
- 초대코드 입력에 Redis 레이트리밋 → 이 인프라를 로그인에도 재사용
