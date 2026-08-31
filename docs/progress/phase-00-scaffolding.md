# Phase 0 — 프로젝트 스캐폴딩

> 작성일 2026-08-31 · 커밋 `7c0bead` · CI ✅ 통과

---

## 1. 한 줄 요약

아무 기능도 없지만 **혼자 힘으로 켜지고, DB에 연결되고, 상태 점검 응답을 돌려주는**
Spring Boot 서버 뼈대를 만들었다. 이후 모든 기능이 이 위에 올라간다.

---

## 2. 이 Phase의 목표 (`docs/BUILD_PLAN.md` 기준)

- Spring Boot 3.x + Java 21 프로젝트 생성 (Gradle)
- 도메인별 패키지 구조
- `docker-compose.yml` — app / postgres / redis
- Flyway(스키마 이력 관리 도구) 설정 + 마이그레이션 폴더
- 공통 예외 처리 + 공통 응답 포맷
- API 문서 자동화(springdoc-openapi)
- GitHub Actions CI(빌드 + 테스트)

**완료 기준:** `docker compose up` 으로 앱이 뜨고, `/actuator/health` 가 200을 반환하며,
CI에서 빌드·테스트가 통과한다. → **3가지 모두 충족 확인됨.**

---

## 3. 무엇을 만들었나

### 3.1 빌드 설정

| 파일 | 설명 |
|---|---|
| `build.gradle.kts` | 의존성·빌드 규칙 정의. Spring Boot **3.4.1**, Java **toolchain 21 고정**. |
| `settings.gradle.kts` | 프로젝트 이름(`bandapp`) + JDK 자동 다운로드 플러그인(foojay). |
| `gradlew`, `gradle/wrapper/` | "Gradle Wrapper". 팀원이 Gradle을 따로 설치하지 않아도 지정된 버전(8.13)으로 빌드되게 하는 스크립트. |
| `.gitattributes` | 윈도우/리눅스 줄바꿈 차이로 `gradlew`가 깨지는 것 방지. |

**Java toolchain 21 고정이 중요한 이유:** 이 PC에는 Java 17만 깔려 있다. toolchain 설정과
foojay 플러그인 덕분에 빌드 시 Gradle이 **Java 21을 알아서 내려받아** 사용한다. 개발자가
JDK를 수동 설치·관리할 필요가 없다.

**주요 의존성(라이브러리) 목록:**

- `spring-boot-starter-web` — HTTP API 서버 기능
- `spring-boot-starter-data-jpa` — DB를 자바 객체로 다루는 ORM
- `spring-boot-starter-validation` — 요청 값 검증(@NotNull 등)
- `spring-boot-starter-actuator` — 상태 점검(`/actuator/health`) 등 운영 엔드포인트
- `spring-boot-starter-data-redis` — Redis 연결(리프레시 토큰·레이트리밋용, 실제 사용은 Phase 1~2)
- `flyway-core` + `flyway-database-postgresql` — DB 스키마 버전 관리
- `postgresql` — PostgreSQL 드라이버
- `springdoc-openapi-starter-webmvc-ui` — 코드에서 API 문서(Swagger UI) 자동 생성
- `lombok` — 반복 코드(게터 등) 자동 생성. **사용 범위는 `CLAUDE.md` 규칙으로 제한**
- 테스트: `spring-boot-starter-test`, `testcontainers`(테스트용 DB를 도커로 잠깐 띄움)

### 3.2 패키지(폴더) 구조 — `src/main/java/com/yeka/bandapp/`

```
BandappApplication.java     ← 앱 시작점
common/                     ← 모든 도메인이 공유하는 공통 코드
  response/ApiResponse, ErrorPayload
  exception/ErrorCode, BusinessException, GlobalExceptionHandler
  config/OpenApiConfig
user/  band/  room/  reservation/  settlement/
board/  notification/  plan/     ← 도메인별 폴더 (지금은 설명용 package-info.java만)
```

**왜 도메인별로 나누나:** 나중에 코드가 커져도 "밴드 관련 코드는 `band/` 안에만" 처럼
경계가 유지된다. 도메인끼리는 서로의 내부를 직접 안 건드리고 서비스 계층을 통해서만 호출한다
(`DESIGN.md` 4.5 "모듈형 모놀리스").

### 3.3 공통 응답 포맷 & 예외 처리

모든 API 응답을 **같은 모양의 JSON**으로 통일한다.

- 성공: `{ "success": true, "data": {...}, "error": null }`
- 실패: `{ "success": false, "data": null, "error": { "code": "...", "message": "...", "fieldErrors": [...] } }`

관련 파일:

| 파일 | 역할 |
|---|---|
| `ApiResponse` | 위 성공/실패 포맷을 표현하는 껍데기 |
| `ErrorPayload` | 실패 시 `error` 부분(코드·메시지·필드별 오류) |
| `ErrorCode` | 오류 종류 목록(enum). 지금은 `INVALID_INPUT`, `INTERNAL_ERROR` 2개. Phase마다 추가됨 |
| `BusinessException` | "도메인 규칙 위반"을 나타내는 예외. 예: 이후 "만료된 초대코드" |
| `GlobalExceptionHandler` | 앱 어디서든 예외가 나면 여기서 잡아 위 실패 포맷으로 변환. 예상 못한 오류는 로그로 남기고 500 반환 |

### 3.4 설정 파일 — `src/main/resources/`

| 파일 | 내용 |
|---|---|
| `application.yml` | 기본 설정. DB·Redis 주소는 **환경변수로 주입**(기본값은 localhost). `ddl-auto: validate` = 앱이 테이블을 함부로 못 바꾸고, 스키마와 코드가 안 맞으면 기동 실패 |
| `application-local.yml` | 로컬에서 직접 실행할 때. 상태 점검에 상세정보 노출 |
| `application-docker.yml` | 도커 컨테이너로 실행할 때. compose가 이 프로파일을 켬 |
| `db/migration/README.md` | 스키마 변경 파일 규칙(`V1__auth.sql` 형식) 안내. **실제 테이블은 Phase 1부터 추가** |

### 3.5 컨테이너 실행 환경

| 파일 | 내용 |
|---|---|
| `Dockerfile` | 앱을 이미지로 굽는 2단계 레시피. 1단계에서 JDK로 빌드 → 2단계는 가벼운 JRE에 결과물만 복사. 보안상 비루트 사용자로 실행, 자체 상태 점검(HEALTHCHECK) 포함 |
| `.dockerignore` | 이미지에 안 넣을 것들(문서·예제·빌드 캐시) |
| `docker-compose.yml` | 세 컨테이너를 한 번에 실행: `postgres:16` + `redis:7` + `app`. postgres/redis가 **정상(healthy)** 이 된 뒤에야 app을 띄운다 |
| `.env.example` | compose가 읽는 변수 샘플(DB 이름·계정·비밀번호). `cp .env.example .env` 로 사용 |

### 3.6 테스트 — `src/test/java/com/yeka/bandapp/`

| 파일 | 역할 |
|---|---|
| `support/IntegrationTestSupport` | 테스트용 PostgreSQL·Redis를 **도커로 잠깐 띄워** 진짜 DB에 붙여 검증하는 공통 토대(Testcontainers) |
| `BandappApplicationTests` | ① 앱 설정이 정상적으로 조립되는지 ② `/actuator/health` 가 200 + `"status":"UP"` 인지 확인. **이게 곧 Phase 0 완료 기준** |

### 3.7 CI — `.github/workflows/ci.yml`

`main` 브랜치로 push 되거나 PR이 열리면 GitHub이 자동으로:
리눅스 → Java 21 설치 → `./gradlew build` (컴파일 + 위 테스트 실행). 하나라도 실패하면 빨간불.

### 3.8 기타

- `.gitignore` — 빌드 산출물·`.env`·크래시 로그 등 git에 안 올림
- `README.md` — 실행/빌드 명령 3줄 요약
- `CLAUDE.md` 갱신 — 엔티티 Lombok 허용/금지 어노테이션, 상태 변경은 의미 있는 메서드로,
  DTO는 record 등 코딩 규칙 추가

---

## 4. 어떻게 동작하나

```
docker compose up
  └ postgres 컨테이너 뜸 → "healthy" 될 때까지 대기
  └ redis 컨테이너 뜸 → "healthy" 될 때까지 대기
  └ app 컨테이너: Dockerfile로 빌드된 jar 실행
       └ application-docker.yml + 환경변수로 DB/Redis 주소 인식
       └ Flyway 실행 (지금은 마이그레이션 없음 → 그냥 통과)
       └ JPA가 스키마 검증 (지금은 엔티티 없음 → 통과)
       └ 8080 포트로 HTTP 수신 시작
  └ 브라우저/curl 로 http://localhost:8080/actuator/health 요청
       └ DB·Redis·디스크 상태를 모아 {"status":"UP"} 200 응답
```

---

## 5. 직접 확인하는 법

### 사전 준비

- **Docker Desktop 실행** (필수). 시스템 트레이의 고래 아이콘이 초록/실행 상태여야 함.

### 방법 A — 전체 스택 실행 (권장, 가장 확실)

프로젝트 폴더(`C:\band\bandApp`)에서 터미널을 열고:

```bash
cp .env.example .env          # 최초 1회만. 이후 .env 의 JWT_SECRET 에 32자 이상 값을 채운다
                              # (Phase 1 머지 이후로 JWT_SECRET 없이는 앱이 기동하지 않는다)
docker compose up --build -d  # 빌드 + 3개 컨테이너 백그라운드 실행 (첫 실행은 몇 분)

docker compose ps             # 세 개 모두 STATUS가 "healthy" 인지 확인
curl http://localhost:8080/actuator/health
```

**기대 결과:**

```json
{"status":"UP","groups":["liveness","readiness"],"components":{
  "db":{"status":"UP","details":{"database":"PostgreSQL"}},
  "redis":{"status":"UP","details":{"version":"7.x"}},
  "diskSpace":{"status":"UP"}, "ping":{"status":"UP"} }}
```

브라우저로도 확인:

- API 문서(Swagger UI): <http://localhost:8080/swagger-ui.html>
- API 명세(JSON): <http://localhost:8080/v3/api-docs>

**정리(종료):**

```bash
docker compose down -v        # 컨테이너 + 테스트 데이터 볼륨까지 삭제
```

### 방법 B — 빌드 & 테스트만

```bash
./gradlew build               # 컴파일 + 통합 테스트 (내부적으로 Docker로 임시 DB 사용)
```

> ⚠️ **이 PC에서는 방법 B의 테스트 단계가 실패한다** (아래 7번 참조). 로컬에서는 방법 A로,
> 테스트 통과 여부는 CI(아래)로 확인한다.

### 방법 C — CI 결과 확인

`main` 에 push 될 때마다 자동 실행된다.

- 웹: GitHub 저장소 → **Actions** 탭 → 최신 "CI" 실행이 초록 체크인지 확인
- 터미널(`gh` 설치 시): `gh run list --repo Yekapark/bandApp`
  / `gh run view --log-failed <실행ID>` (실패 시 로그만)

---

## 6. 실제 검증 기록 (2026-08-31)

| 확인 항목 | 명령 | 결과 |
|---|---|---|
| 컴파일 + jar 빌드 | `./gradlew build -x test` | ✅ BUILD SUCCESSFUL (JDK 21 자동 다운로드됨) |
| 테스트 코드 컴파일 | `./gradlew compileTestJava` | ✅ 통과 |
| 전체 스택 기동 | `docker compose up --build` | ✅ app/postgres/redis 모두 healthy |
| 상태 점검 | `curl /actuator/health` | ✅ 200, `status: UP` (db=PostgreSQL, redis=7.4.11) |
| API 문서 | `curl /v3/api-docs`, `/swagger-ui.html` | ✅ 둘 다 200 |
| 통합 테스트 (로컬) | `./gradlew test` | ⚠️ 실패 — 이 PC의 Docker API 문제 (7번) |
| 통합 테스트 (CI) | GitHub Actions `./gradlew build` | ✅ **통과** (run `33366279024`, 1분 51초) |

---

## 7. 알려진 이슈 / 제약

### 7.1 이 개발 PC에서 `./gradlew test` 가 실패한다

- 증상: 테스트가 "Could not find a valid Docker environment" 로 실패
- 원인: 이 PC의 Docker 엔진이 테스트 라이브러리(Testcontainers)의 API 호출에 HTTP 400을
  돌려준다. `docker` 명령과 `docker compose` 자체는 정상. **코드/설정 문제 아님.**
- 대응: 로컬 검증은 `docker compose` (방법 A)로, 테스트 통과 여부는 **CI로 확인**.
  실제로 CI(리눅스)에서는 문제없이 통과함.

### 7.2 CI 경고 (동작에는 영향 없음)

- `actions/setup-java@v4` 가 deprecated → 다음에 워크플로 수정 시 `@v5` 로 올리면 됨
- 일부 액션이 Node 20 대신 24로 강제 실행됨 (GitHub 측 변경, 조치 불필요)

### 7.3 아직 없는 것 (의도된 것)

- 로그인·회원가입 등 실제 기능 → Phase 1부터
- DB 테이블 → 각 Phase가 자기 Flyway 마이그레이션으로 추가
- Spring Security → Phase 1에서 도입 (지금 넣으면 health가 잠겨서 뒤로 미룸)

---

## 8. 커밋 · CI

- 커밋: `7c0bead` "Phase 0: 프로젝트 스캐폴딩" — `Yekapark/bandApp` `main`
- CI: GitHub Actions run `33366279024` — ✅ 통과 (build job 1분 51초)

---

## 9. 다음 Phase 예고 — Phase 1 (인증)

- 이메일 회원가입 / 로그인
- 카카오 소셜 로그인(OAuth2)
- JWT 토큰(접속용 access + 갱신용 refresh, refresh는 Redis 저장)
- 회원 탈퇴 (카카오 연결 해제 포함, 소프트 삭제) — 앱스토어 심사 필수 요건
- 첫 DB 마이그레이션 `V1__auth.sql` 등장
- 완료 기준: 가입 → 로그인 → 토큰 갱신 → 탈퇴 전 과정 통합 테스트 통과
