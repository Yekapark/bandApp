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

## 6. 실제 검증 기록 (2026-08-31)

`docker compose up --build`로 전체 스택 기동 후 수동 확인:

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
| 자동 테스트 (로컬 `./gradlew test`) | ⚠️ 미실행 — 이 PC의 Docker 문제 (Phase 0 7.1). CI로 확인 |

## 7. 알려진 이슈 / 제약

- **로컬 자동 테스트 불가**: 이 개발 PC에서 `./gradlew test`가 Testcontainers Docker 문제로
  실패한다. 검증은 `docker compose` 수동 확인 + CI에 의존한다. (Phase 0 문서 7.1과 동일)
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

- 브랜치: `phase-1-auth`
- 커밋: (푸시 후 채움)
- CI: (푸시 후 GitHub Actions 링크 채움)

## 9. 다음 Phase 예고 — Phase 2 (밴드 · 초대 · 멤버)

- 밴드 생성(생성자가 자동으로 LEADER), 초대코드 발급/재발급/무효화
- 초대코드로 참여, 초대 딥링크 + 웹 랜딩
- 멤버 목록, 자발적 탈퇴, 밴드장의 추방, **밴드장 위임**(원자적)
- 밴드 설정(`reservationPermission`) 변경
- 초대코드 입력에 Redis 레이트리밋 → 이 인프라를 로그인에도 재사용
