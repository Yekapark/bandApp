# 클라이언트(Flutter) 개발 로그 · 이어받기 가이드

> **새 세션에서 클라이언트 작업을 이어받을 때 이 파일부터 읽는다.**
> 이 문서는 "지금 어디까지 됐고, 어떻게 이어가는지"를 담는 살아있는 문서다.
> 작업을 진행하면 아래 "현재 상태"와 "다음 할 일"을 갱신한다.
> 마지막 갱신: **2026-09-03**

---

## 0. 30초 요약

- 백엔드는 Phase 0~10 완료(`docs/progress/README.md` 참조). 지금은 **Flutter 클라이언트 트랙**.
- 클라이언트 코드 위치: **`client/`** (이 저장소 안, 모노레포).
- 1단계(온보딩 흐름 → 밴드 홈)는 **코드 구현 완료**, `flutter analyze` 에러 0, 웹 빌드 성공,
  `flutter run -d chrome` 로 로그인 화면까지 실렌더 확인함.
- 다음: 예약 캘린더 화면 → 일정 등록 폼 → 일정 상세(참석 체크).

---

## 1. 어떤 파일을 순서대로 읽나

| 순서 | 파일 | 왜 |
|---|---|---|
| 1 | **이 파일** (`docs/progress/client-DEVLOG.md`) | 현재 상태·다음 할 일·로컬 환경 함정 |
| 2 | `docs/progress/client-01-onboarding-home.md` | 1단계에서 만든 것 상세, 목업↔백엔드 차이 표 |
| 3 | `client/README.md` | 실행법, 폴더 구조, 상태관리/패키지 선택 |
| 4 | `docs/BACKLOG.md` §2 (286~318줄) | 전체 화면 정의 13개 (사람이 준 요구사항 원문) |
| 5 | `example/BandScreen.dc.html` | 화면 목업(디자인 기준). 색/폰트/레이아웃 |
| 6 | `docs/BUILD_PLAN.md` §3 도메인 모델 | 응답 필드 확인용 |
| 참고 | `src/main/java/.../*Controller.java`, `.../dto/*.java` | API 엔드포인트·JSON 형태 실제 소스가 정답 |

디자인(목업)과 백엔드 구현이 다르면 **백엔드 기준**. 발견한 불일치는
`client-01-onboarding-home.md` §7 표에 계속 추가한다.

---

## 2. 현재 상태 (2026-09-03)

### 구현 완료 (1단계: 온보딩 → 홈)

| 화면 | 라우트 | 파일 | 백엔드 |
|---|---|---|---|
| 스플래시 | `/` | `features/auth/presentation/splash_screen.dart` | `GET /users/me` (토큰 있으면) |
| 로그인 | `/login` | `.../login_screen.dart` | `POST /auth/login` |
| 약관 동의 | `/terms` | `.../terms_screen.dart` | 없음 (클라 게이트) |
| 회원가입 | `/signup` | `.../signup_screen.dart` | `POST /auth/signup` |
| 밴드 만들기/가입 | `/band-gate` | `features/band/presentation/band_gate_screen.dart` | — |
| 밴드 만들기 | `/band-gate/create` | `.../create_band_screen.dart` | `POST /bands` |
| 초대코드 가입 | `/band-gate/join` | `.../join_band_screen.dart` | `POST /bands/join` |
| 밴드 홈 | `/home` | `features/home/presentation/home_screen.dart` | `GET /bands`, `/bands/{id}/members`, `/bands/{id}/reservations` |

- 라우팅: `lib/routing/app_router.dart` — go_router + 로그인 상태 기반 redirect.
- 네트워크: `lib/core/network/dio_client.dart` — 토큰 자동 부착, 401→refresh 1회 재시도.
- 상태관리: flutter_riverpod (코드젠 없음). 인증 상태: `features/auth/application/auth_controller.dart`.
- 아직 없는 화면(캘린더·지도·정산·게시판·알림·멤버관리)은 하단 탭/버튼에서
  `shared/widgets/soon.dart` 의 스낵바로 안내.

### 검증 결과

- `flutter analyze` → **에러 0**, info/warning 103개(주로 `prefer_const`, `require_trailing_commas`,
  `withOpacity` deprecated). 기능 영향 없음. 정리는 선택.
- `flutter build web` → 성공. (`flutter_secure_storage_web` 가 WASM 미지원이라 "Wasm dry run failed"
  경고는 뜨지만 기본 JS 빌드는 정상.)
- `flutter run -d chrome --web-port=5599` → 로그인 화면까지 실렌더 확인(2026-09-03).
- 백엔드 붙여서 로그인/가입/밴드생성 **end-to-end 는 아직 미검증** (아래 3-C 참조).

### 커밋 상태

- `client/` 및 이 진행 문서들은 **아직 커밋 안 됨** (2026-09-03 기준). 첫 커밋 시 포함 대상:
  `client/lib/**`, `client/pubspec.yaml`, `client/analysis_options.yaml`, `client/README.md`,
  `client/.gitignore`, `docs/progress/client-*.md`, `docs/progress/README.md`(수정), `CLAUDE.md`(수정).
- `client/android`, `client/web`, `client/windows`, `client/.metadata` 는 `flutter create` 로
  생성한 것. `client/.gitignore` 가 플랫폼 폴더를 무시하도록 해둠 — 커밋할지는 팀 결정.

---

## 3. 로컬 환경 함정 (이 PC 기준 — 세션마다 다시 부딪힘)

### A. Flutter 가 PATH 에 없음

- 설치 위치: **`C:\flutter\bin`** (Flutter 3.47.2 / Dart 3.13.2, stable).
- `flutter` / `dart` 명령이 PATH 에 없어서 **전체 경로로 호출**해야 한다:
  `& C:\flutter\bin\flutter.bat <명령>` (PowerShell).
- Git Bash 쪽 PATH 에는 아예 안 잡힌다 → PowerShell 로 실행.
- 영구 등록하려면 사용자가 직접: `setx PATH "%PATH%;C:\flutter\bin"` (새 터미널부터 적용).

### B. 포트 5432 충돌 — DB 접속 시 반드시 주의

- **Windows 네이티브 PostgreSQL 18 서비스(`postgresql-x64-18`)가 상시 실행** 중이고
  `0.0.0.0:5432` 를 선점한다. 그래서 호스트에서 `127.0.0.1:5432` 로 붙으면
  **Docker 의 앱 DB 가 아니라 네이티브 PG 로 연결된다** (네이티브 PG 의 `bandapp` 롤은
  비번이 달라서 "password authentication failed" 발생 — HeidiSQL 삽질의 원인).
- 앱 데이터가 있는 **Docker Postgres** 접속 정보:
  - `docker-compose.yml` 을 `"5432:5432"` → `"5433:5432"` 로 바꾸고 `docker compose up -d` 하거나
    (권장, 비파괴적 / app 컨테이너는 내부적으로 `postgres:5432` 를 써서 영향 없음),
  - 또는 네이티브 PG 서비스를 중지(`services.msc`, 관리자 권한).
  - **DB 크리덴셜** (`.env` 기준): host `127.0.0.1`, port `5433`(위 remap 시), user `bandapp`,
    password `CHANGE_ME_local_only`, database `bandapp`.
  - 스키마·데이터는 `app` 컨테이너가 Flyway 로 만든다. `docker compose up` 으로 app 까지 떠야
    테이블(현재 7개)이 보인다.
- HeidiSQL: 세션 Settings 탭의 **Library** 드롭다운에서 `libpq-*.dll` 선택 필요.
  비어 있으면 HeidiSQL 최신 버전으로 업데이트(구버전은 `scram-sha-256` 인증도 미지원).

### C. Docker 상태

- `docker compose up` 은 프로젝트 루트(`E:\project\band`)에서. `.env` 필요(`JWT_SECRET` 필수).
- 2026-09-03 시점: `band-app-1`, `band-postgres-1`, `band-redis-1` 모두 `Up (healthy)` 였음.
  세션 이어받으면 `docker ps` 로 재확인.
- Flutter 웹에서 백엔드 호출은 CORS 를 타는데, 백엔드 `app.cors.allowed-origins` 가
  `http://localhost:*` 라 `localhost:5599` 등에서 바로 붙는다(설정 OK).
- **안드로이드 에뮬레이터**는 호스트 localhost 가 `10.0.2.2` → `AppConfig.apiBaseUrl` 이 자동 분기함.
  실기기는 `flutter run --dart-define=API_BASE_URL=http://<PC-IP>:8080`.

### D. Windows 네이티브 빌드 / 플러그인

- `flutter_secure_storage` 등 네이티브 플러그인 빌드에 **개발자 모드** 필요:
  `start ms-settings:developers` → ON. 웹만 돌릴 거면 불필요.

---

## 4. 실행 순서 (요약)

```powershell
# 1) 백엔드
cd E:\project\band
docker compose up -d
# http://localhost:8080/actuator/health -> 200 확인

# 2) 클라이언트 (웹)
cd E:\project\band\client
& C:\flutter\bin\flutter.bat pub get
& C:\flutter\bin\flutter.bat run -d chrome
#   r=핫리로드, R=핫리스타트, q=종료
```

수동 확인 시나리오는 `docs/progress/client-01-onboarding-home.md` §5.

---

## 5. 다음 할 일 (우선순위)

1. **예약 캘린더** `/cal` — 월간 뷰, 일정 있는 날 점 표시, 날짜 탭 시 하단 일정 리스트.
   `GET /bands/{id}/reservations?from&to` (이미 `reservation_repository.dart` 에 있음).
2. **일정 등록 폼** — 합주실 선택 + 날짜/시간 + 비용 + 외부 예약 메모 + 반복 설정.
   `POST /bands/{id}/reservations`. 응답의 `overlaps`(겹침 경고)를 등록 후 안내로 표시(등록은 성공시킴).
   합주실 목록 API(`GET /bands/{id}/rooms`) 화면도 필요 → 합주실 등록 폼도 같이.
3. **일정 상세** — 참석 체크(참석/불참/미정), 멤버별 참석 현황·집계, 셋리스트.
   `GET /bands/{id}/reservations/{rid}` (상세엔 attendance·setlist 포함), `PUT .../attendance`.
4. 하단 탭바를 실제 화면으로 연결 (`ShellRoute` 로 전환 검토).
5. 카카오 로그인 SDK 연동 (`kakao_flutter_sdk` 추가 + 네이티브 설정). `AuthController.loginKakao` 자리는 있음.
6. (정리) analyze info/warning 줄이기, 폰트 번들(google_fonts 런타임 다운로드 대신).
7. (검토) 클라이언트 CI — `flutter analyze` + `flutter test`.

## 6. 열린 결정 / 확인 필요

- `client/` 플랫폼 폴더(android/web/windows) 커밋 여부 — 현재 .gitignore 로 제외.
- 홈 "이번 달 정산" 카드: 밴드 단위 정산 합계 API 없음 → 현재 값 `—`. 집계 엔드포인트를
  백엔드에 추가할지, 화면에서 일정별로 합산할지 결정 필요.
- 초대코드 UI: 백엔드는 8자 영숫자인데 목업은 6자리 숫자 키패드 → 현재 8자 텍스트 입력으로 구현.
