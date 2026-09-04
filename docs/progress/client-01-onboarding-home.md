# 클라이언트 1단계 — 온보딩 흐름 ~ 밴드 홈

## 1. 한 줄 요약

Flutter 클라이언트를 `client/` 에 새로 만들고, **앱 실행 → 로그인/회원가입 → 약관 →
밴드 만들기/가입 → 밴드 홈**까지의 화면과 화면 이동을 실제 백엔드 API에 연결해 구현했다.

## 2. 이 단계의 목표

`docs/BUILD_PLAN.md` 는 "Flutter 클라이언트는 백엔드 완료 후 별도 트랙"이라고만
정하고 화면 명세는 `docs/BACKLOG.md` §2 와 목업 `example/BandScreen.dc.html` 에 있다.
1단계 범위는 사용자와 합의한 대로 **온보딩 흐름 전체와 홈 화면**까지다.
(캘린더·지도·정산·게시판·알림·멤버 관리 등은 다음 단계.)

## 3. 무엇을 만들었나

경로는 모두 `client/` 하위.

### 프로젝트 설정
- `pubspec.yaml` — 의존성: `flutter_riverpod`(상태관리), `go_router`(라우팅),
  `dio`(HTTP), `flutter_secure_storage`(토큰 보관), `google_fonts`(목업 폰트), `intl`(날짜).
  스펙에 없던 라이브러리라 목적을 함께 적어둔다 — 필요 시 조정 가능.
- `analysis_options.yaml`, `.gitignore`, `README.md`(실행법·구조·목업↔백엔드 차이).

### core (공통 기반)
- `core/config/app_config.dart` — 앱 이름(`밴듈` / 영문 `Bandule`), API 베이스 URL.
  안드로이드 에뮬레이터는 `10.0.2.2`, 그 외 `localhost:8080`. `--dart-define=API_BASE_URL=` 로 덮어쓴다.
- `core/theme/` — 목업에서 뽑은 다크 단일 테마(배경 `#0B0B0E`, 포인트 오렌지 `#FF6A2B`,
  보조 퍼플 `#A06BFF`). 폰트: 본문 Noto Sans KR, 로고 Bebas Neue, 숫자 JetBrains Mono.
- `core/network/dio_client.dart` — `Dio` 1개. 요청마다 access 토큰을 헤더에 붙이고,
  **401 이 오면 `/auth/refresh` 를 1회 호출한 뒤 원래 요청을 자동 재시도**한다.
  그래도 실패하면 "세션 종료" 신호를 쏘고, 이를 받은 로그인 상태가 로그아웃으로 바뀐다.
- `core/network/api_exception.dart` — 백엔드 공통 실패 응답(`{success:false,error:{code,message}}`)을
  화면에서 쓰기 쉬운 예외로 변환. 네트워크 끊김도 여기서 한국어 메시지로.
- `core/storage/token_storage.dart` — access/refresh 토큰을 OS 보안 저장소에 보관 + 메모리 캐시.
- `core/format/formatters.dart` — "8월 14일 (금) 19:00", "3시간", "₩ 45,000" 같은 표기.

### features/auth — 인증
- `application/auth_controller.dart` — 로그인 상태(`unknown` / `authenticated` / `unauthenticated`)를
  들고 있는 컨트롤러. 앱 시작 시 저장된 토큰으로 `GET /users/me` 를 호출해 유효성까지 확인.
- `data/` — `POST /auth/signup` · `/auth/login` · `/auth/kakao` · `/auth/logout`, `GET /users/me` 호출.
- `presentation/`
  - `splash_screen.dart` — 로고 + 로딩 바를 약 2초 보여준 뒤 세션 확인 → 홈 또는 로그인.
  - `login_screen.dart` — 이메일/비밀번호 로그인(동작), "이메일로 회원가입" 링크,
    카카오 버튼(SDK 연동 전이라 "준비 중"), 네이버 버튼(백엔드 미지원이라 비활성).
  - `terms_screen.dart` — 회원가입 STEP 1. 필수 3 + 선택 1 약관 동의. 서버 호출 없음.
  - `signup_screen.dart` — STEP 2. 이름/이메일/비밀번호 → `POST /auth/signup`.

### features/band — 밴드
- `application/band_providers.dart` — 내가 속한 밴드 목록(`GET /bands`), 현재 선택된 밴드.
- `data/` — `GET /bands`, `POST /bands`, `POST /bands/join`, `GET /bands/{id}/members`.
- `presentation/`
  - `band_gate_screen.dart` — 밴드 미소속 상태. "밴드 만들기 / 밴드 가입하기" 선택.
  - `create_band_screen.dart` — 밴드 이름만 입력(백엔드가 이름만 받음) → `POST /bands`.
  - `join_band_screen.dart` — **초대코드 8자**(영숫자, 대문자 자동) 입력 → `POST /bands/join`.

### features/reservation, features/home — 홈
- `reservation/data/` — 일정 목록(`GET /bands/{id}/reservations?from&to`) 모델·호출.
- `home/application/home_providers.dart` — 앞으로 60일간의 일정, 그중 가장 가까운 1건.
- `home/presentation/home_screen.dart` + `widgets/` — 밴드 홈:
  상단 밴드명(탭하면 밴드 전환 시트) · 알림/멤버 버튼, 멤버 아바타 가로 목록,
  "다음 합주" 카드, 요약 2칸(정산 자리 + 예정 합주 횟수), "다가오는 일정" 리스트,
  하단 탭바(홈만 활성, 나머지는 "다음 단계" 안내).

### routing
- `routing/app_router.dart` — `go_router`. 로그인 상태에 따라 자동 리다이렉트:
  미로그인 → `/login`, 로그인됨 → `/home`, 부팅 확인 전 → 스플래시.
  홈에서 밴드가 하나도 없으면 `/band-gate` 로 보낸다.

## 4. 어떻게 동작하나

```
스플래시(/)
  └ 저장된 토큰 있음? ──예──▶ GET /users/me 성공 ──▶ 홈(/home)
  └ 없음/실패 ─────────────────────────────────▶ 로그인(/login)

로그인(/login)
  ├ 이메일 로그인 성공 ─────────────────────────▶ 홈
  └ "이메일로 회원가입" ─▶ 약관(/terms) ─▶ 가입(/signup) ─▶ 홈

홈(/home)
  └ GET /bands 결과가 0개 ─────────────────────▶ 밴드 게이트(/band-gate)
        ├ 밴드 만들기(/band-gate/create) ─ POST /bands ─▶ 홈
        └ 초대코드 가입(/band-gate/join) ─ POST /bands/join ─▶ 홈
```

토큰이 만료되면 사용자 눈에 띄지 않게 `/auth/refresh` 로 갱신하고 요청을 다시 보낸다.
리프레시까지 실패하면 자동으로 로그인 화면으로 빠진다.

## 5. 직접 확인하는 법

### 사전 준비
1. Flutter 설치 (3.22 이상 권장). `flutter doctor` 로 확인.
2. 백엔드 기동 — 프로젝트 루트에서:
   ```bash
   docker compose up
   ```
   `http://localhost:8080/actuator/health` 가 200이면 준비 완료.

### 실행
```bash
cd client
flutter create . --org com.yeka --project-name bandapp_client --platforms=android,ios,web
git checkout -- pubspec.yaml analysis_options.yaml lib   # flutter create 가 덮었으면 되돌림
flutter pub get
flutter run            # 크롬/에뮬레이터/기기 택1
```
실기기에서 PC의 백엔드를 쓰려면:
`flutter run --dart-define=API_BASE_URL=http://<PC-IP>:8080`

### 기대 결과 (수동 시나리오)
1. 앱 실행 → 로고 + 로딩 바 2초 → 로그인 화면.
2. "이메일로 회원가입" → 약관 필수 3개 체크 → "동의하고 계속하기" →
   이름/이메일/비밀번호(8자+) 입력 → "가입하고 시작하기".
3. 가입 직후 밴드가 없으므로 밴드 게이트로 이동.
4. "밴드 만들기" → 이름 입력 → "밴드 만들기" → **홈**에 그 밴드가 보임.
   또는 다른 계정에서 만든 초대코드로 "밴드 가입하기".
5. 홈에서 밴드명을 탭하면 밴드 전환 시트가 뜬다(여러 밴드 소속 시).
6. 앱을 껐다 켜면 스플래시 후 바로 홈(토큰 유지).
7. 하단 캘린더/지도/정산/게시판 탭은 "다음 단계에서 구현됩니다" 스낵바.

### 문제 해결
- **로그인 시 "서버에 연결하지 못했습니다"**: 백엔드가 안 떠 있거나, 안드로이드
  실기기인데 `localhost` 를 봄. `--dart-define=API_BASE_URL=` 로 PC IP 지정.
- **폰트가 기본체로 보임**: `google_fonts` 가 첫 실행 때 폰트를 내려받는다. 오프라인이면
  시스템 폰트로 폴백. (추후 폰트 파일 번들 예정.)
- **`flutter create` 후 빌드 깨짐**: `git checkout -- pubspec.yaml lib` 로 되돌리고 다시 `flutter pub get`.

## 6. 실제 검증 기록

- 이 PC에 Flutter SDK가 설치돼 있지 않아(`flutter`/`dart` 명령 없음) **정적 분석·빌드·
  런타임 검증은 수행하지 못했다.** 코드는 백엔드 컨트롤러/DTO(실제 소스)를 보고
  응답 형태에 맞춰 작성했다.
- 다음 작업자/사용자가 위 "직접 확인하는 법"으로 `flutter analyze` → `flutter run`
  순서로 검증 필요.

## 7. 알려진 이슈 / 제약

목업(`example/BandScreen.dc.html`)과 백엔드 구현이 다른 지점은 **백엔드 기준**으로 맞췄다:

| 항목 | 목업 | 실제 구현 |
|---|---|---|
| 초대코드 | 6자리 숫자 키패드 | 8자 영숫자 텍스트 입력 (백엔드 스펙) |
| 밴드 생성 | 장르·내 파트 선택 | 이름만 (`POST /bands` 가 name만 받음) |
| 멤버 파트(악기) | 표시 | 백엔드에 필드 없음 → 이름·역할만 표시 |
| 소셜 로그인 | 카카오/네이버/구글 | 이메일만 동작. 카카오=준비 중, 네이버=비활성 |
| 홈 "이번 달 정산" | 금액·미납 인원 | 밴드 단위 합계 API 없음 → 값 `—` 자리표시 |

그 외:
- 카카오 로그인은 `kakao_flutter_sdk` 추가 + 네이티브 설정이 필요해 이번 범위에서 제외.
  `AuthController.loginKakao(accessToken)` 자리는 만들어 뒀다.
- 네이티브 플랫폼 폴더(android/ios/web)는 커밋하지 않았다. `flutter create .` 로 생성.
- 스플래시는 최소 2초 고정. 세션 확인이 그보다 오래 걸리면 그만큼 더 걸린다.

## 8. 커밋 · CI 링크

- 커밋: (이 문서와 함께 커밋 예정)
- CI: 클라이언트용 워크플로 없음 — 다음 단계에서 `flutter analyze` / `flutter test` 추가 검토.

## 9. 다음 단계 예고

- 예약 캘린더(`/cal`) — 월간 뷰, 일정 있는 날 표시, 날짜별 리스트.
- 일정 등록 폼 — 합주실 선택 + 날짜/시간 + 비용 + 외부 예약 메모, 겹침 경고 표시.
- 일정 상세 — 참석 체크(RSVP), 멤버별 참석 현황, 셋리스트.
- 카카오 로그인 SDK 연동.
