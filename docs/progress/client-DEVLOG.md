# 클라이언트(Flutter) 개발 로그 · 이어받기 가이드

> **새 세션에서 클라이언트 작업을 이어받을 때 이 파일부터 읽는다.**
> 이 문서는 "지금 어디까지 됐고, 어떻게 이어가는지"를 담는 살아있는 문서다.
> 작업을 진행하면 아래 "현재 상태"와 "다음 할 일"을 갱신한다.
> 마지막 갱신: **2026-09-04** (C7 일정 수정·승인/거절 + 정기 일정 추가)

---

## 0. 30초 요약

- 백엔드는 Phase 0~10 완료(`docs/progress/README.md` 참조). 지금은 **Flutter 클라이언트 트랙**.
- 클라이언트 코드 위치: **`client/`** (이 저장소 안, 모노레포).
- 1단계(온보딩→홈), 2단계(캘린더·합주실·일정), 3단계(정산 N빵)는 **코드 구현 완료**,
  `flutter analyze` 에러 0, 웹 빌드 성공. 백엔드 붙인 UI end-to-end 는 아직 미검증.
- 2단계에 딸려: **백엔드 신규 엔드포인트** `GET /bands/{id}/rooms/search`(네이버 지역검색 프록시)
  + 한국어 로케일. PR #34.
- 3단계 정산: 백엔드 변경 없음(Phase 7 API 사용). 백엔드 스모크(생성/납부/재계산) 통과. PR: `feat/client-settlement`.
- 카카오 로그인 SDK 배선 완료(`kakao_flutter_sdk_user`). 앱 키만 넣으면 동작. 백엔드 `/auth/kakao` 는 이미 있었음.
- 하단 탭바 **`StatefulShellRoute` 전환 완료**(2026-09-04) — 홈·캘린더·지도 탭이 스택/스크롤을
  각자 보존하고 탭바가 항상 떠 있다. 상세·폼·정산은 루트 네비게이터에 풀스크린으로 push.
- **합주실 지도 `/map` 구현 완료**(2026-09-04) — 네이버 지도(`flutter_naver_map`). 좌표 있는 합주실
  마커 + 하단 목록. **Android/iOS 전용**이라 웹에선 목록만(가드). `NAVER_MAP_CLIENT_ID` dart-define 필요.
- **알림 설정 화면 `/settings/notifications` 구현 완료**(2026-09-03) — 푸시 on/off + "일정 시작 N분 전"
  리마인더 시점(프리셋 칩). 백엔드 `GET/PUT /api/v1/notifications/settings` 사용, 백엔드 변경 없음.
  홈 헤더 종 아이콘에서 진입. **디바이스 토큰 등록·실제 푸시 수신은 Firebase 설정 필요 → 미구현**(§7·PROBLEMS).
- **게시판 `/board` 구현 완료**(2026-09-04, C6) — 피드(커서 무한스크롤)·글 상세·작성/수정·
  첨부 업로드(R2 presigned 직접 PUT)·신고·작성자 차단. 하단 탭바에 "게시판" 탭이 실제 화면으로
  붙음. 백엔드 변경 없음(Phase 8 API). 신규 의존성 `image_picker`. 상세는 `client-06-board.md`.
- **일정 수정·승인/거절 + 정기 일정 구현 완료**(2026-09-04, C7) — 일정 상세에서 수정(PUT)·
  밴드장 승인/거절. 정기 일정 화면 `/cal/recurring`(규칙 목록·등록·삭제, 캘린더 AppBar ↻ 진입).
  백엔드 변경 없음(Phase 4·5 API). 상세는 `client-07-reservation-recurring.md`.
- 다음: (C8) 설정 나머지(밴드/계정/차단해제) → (C9) 알림 수신부(FCM)+클라 CI.

---

## 1. 어떤 파일을 순서대로 읽나

| 순서 | 파일 | 왜 |
|---|---|---|
| 1 | **이 파일** (`docs/progress/client-DEVLOG.md`) | 현재 상태·다음 할 일·로컬 환경 함정 |
| 2 | `docs/progress/client-01-onboarding-home.md` | 1단계에서 만든 것 상세, 목업↔백엔드 차이 표 |
| 2b | `docs/progress/client-02-calendar-reservation.md` | 2단계(캘린더·합주실·일정 등록·상세) 상세, 뺀 것/열린 결정 |
| 2c | `docs/progress/client-03-settlement.md` | 3단계(정산 N빵 화면) 상세 |
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

### 구현 완료 (2단계: 캘린더 → 일정 등록 → 일정 상세) — 상세는 `client-02-calendar-reservation.md`

| 화면 | 라우트 | 파일 | 백엔드 |
|---|---|---|---|
| 예약 캘린더 | `/cal` | `features/reservation/presentation/calendar_screen.dart` | `GET /bands/{id}/reservations?from&to` |
| 일정 등록 폼 | `/cal/new?date=` | `.../reservation_form_screen.dart` | `POST /bands/{id}/reservations` (겹침은 `overlaps` 경고) |
| 합주실 등록 폼 | `/cal/rooms/new` | `.../room_form_screen.dart` | `POST /bands/{id}/rooms`, `GET /bands/{id}/rooms/search` |
| 합주실 선택 시트 | (모달) | `.../widgets/room_picker_sheet.dart` | `GET /bands/{id}/rooms` |
| 일정 상세 | `/reservations/:rid` | `.../reservation_detail_screen.dart` | `GET/DELETE …/{rid}`, `PUT …/attendances/{uid}`, `POST/DELETE …/setlist` |
| 합주실 지도 | `/map` (탭) | `.../map_screen.dart` | `GET /bands/{id}/rooms` (`lat`/`lng` 사용) |

- 상태: `features/reservation/application/calendar_providers.dart`
  (`calendarMonthProvider`, `monthReservationsProvider`, `roomsProvider`, `reservationDetailProvider`).
- 홈의 `showSoon` 안내 중 캘린더/일정 관련은 실제 라우트로 교체(지도·정산·게시판은 유지).
- **합주실 주소 검색**: 등록 폼 주소 칸이 네이버 지역검색과 연결(디바운스 → `rooms/search` →
  후보 탭 시 자동 입력). `place_models.dart`, `room_repository.searchPlaces`.
  **백엔드에 `GET /bands/{id}/rooms/search` 엔드포인트를 새로 만들었다**(네이버 지역검색 프록시,
  `phase-03-room.md` §8.2). `NAVER_SEARCH_CLIENT_ID/SECRET` 환경변수 필요(개발자센터 앱, NCP 지도 키와 별개).
- **한국어 로케일**: `app.dart`에 `flutter_localizations`+`locale: ko` → 날짜/시간 피커 등 한국어.
  `intl`을 `^0.20.2`로 올림.
- **뺀 것**: 정기(반복) 일정 — 백엔드에 생성 API 없음. 일정 수정/승인/거절 UI. 캘린더 주간 뷰.

### 구현 완료 (3단계: 정산 N빵) — 상세는 `client-03-settlement.md`

| 화면 | 라우트 | 파일 | 백엔드 |
|---|---|---|---|
| 일정 정산 | `/reservations/:rid/settlement` | `features/settlement/presentation/settlement_screen.dart` | `GET/POST …/settlement`, `POST …/settlement/recalculate`, `PUT …/settlement/shares/{uid}` |

- 상태: `features/settlement/application/settlement_providers.dart` (`settlementProvider` family, 없으면 null).
- 정산 없으면 생성 폼(총액·분배방식), 있으면 현황(1인당·진행바·납부 체크리스트·재계산).
  본인 몫만 셀프 체크. 생성·재계산은 등록자/밴드장만.
- 일정 상세 화면에서 "정산 (N빵) 보기 · 만들기" 링크로 진입.
- 백엔드 변경 없음 — Phase 7 API 그대로.

### 구현 완료 (4단계: 합주실 지도 + 탭바 ShellRoute) — 상세는 `client-04-room-map.md`

| 화면 | 라우트 | 파일 | 백엔드 |
|---|---|---|---|
| 합주실 지도 | `/map` (탭) | `features/reservation/presentation/map_screen.dart` | `GET /bands/{id}/rooms` |

- 하단 탭바를 `StatefulShellRoute.indexedStack` 로 전환(`routing/tab_shell.dart`).
  브랜치: **홈 · 캘린더 · 지도**. 정산·게시판 탭은 화면이 없어 `showSoon` 스낵바.
  탭 전환 시 각 브랜치의 스택·스크롤 위치가 보존되고 탭바가 항상 떠 있다.
- 지도: `flutter_naver_map`(네이버 지도, **Android/iOS 전용**). 좌표(`lat`/`lng`) 있는
  합주실만 마커, 하단에 전체 목록(탭 시 카메라 이동). "＋ 새 합주실 등록" 버튼.
- **웹 / 키 미설정**: 지도 자리에 안내 문구 + 목록만. `AppConfig.naverMapEnabled` 로 분기.
- 클라이언트 `Room` 모델에 `lat`/`lng`·`hasLocation` 추가(백엔드 `RoomResponse` 는 이미 반환 중).
- 백엔드 변경 없음.

#### 네이버 지도를 실제로 켜려면 (사용자 작업)

1. **NCP 콘솔**(https://console.ncloud.com) → AI·NAVER API → **Maps** → Application 등록.
   Android 패키지명 `com.yeka.bandapp_client`, iOS 번들 ID 등록. → **Client ID** 복사.
   (2단계 주소검색용 `NAVER_SEARCH_*` 는 네이버 개발자센터 앱으로 별개.)
2. 실행 시 dart-define: `--dart-define=NAVER_MAP_CLIENT_ID=<Client ID>`.
3. Android 네이티브 빌드는 개발자 모드 필요(§3-D). 웹은 어차피 지도 미지원 → 목록만.

### 구현 완료 (C7: 일정 수정·승인/거절 + 정기 일정) — 상세는 `client-07-reservation-recurring.md`

| 화면 | 라우트 | 파일 | 백엔드 |
|---|---|---|---|
| 일정 수정 | `/reservations/:rid/edit` | `features/reservation/presentation/reservation_form_screen.dart`(수정 모드 겸용) | `PUT /bands/{id}/reservations/{rid}` |
| 승인/거절 | (일정 상세 내 버튼) | `.../reservation_detail_screen.dart` | `POST …/{rid}/approve`·`/reject` (밴드장·PENDING) |
| 정기 일정 목록 | `/cal/recurring` | `features/recurring/presentation/recurring_list_screen.dart` | `GET/DELETE /bands/{id}/recurring-rules[/{ruleId}]` |
| 정기 일정 등록 | `/cal/recurring/new` | `.../recurring_form_screen.dart` | `POST /bands/{id}/recurring-rules` |

- 상태: `features/recurring/application/recurring_providers.dart` (`recurringRulesProvider` family).
- 진입: 캘린더 AppBar 의 ↻(repeat) 아이콘 → 정기 일정 목록.
- 개별 회차 수정·취소는 일반 일정 상세를 그대로 사용(규칙 유지). 규칙 수정은 백엔드 미제공 → 삭제 후 재등록.
- **뺀 것**: 정기 규칙 상세(회차 목록) 화면, 규칙 수정 UI, 셋리스트 재정렬 UI.

### 구현 완료 (C6: 게시판) — 상세는 `client-06-board.md`

| 화면 | 라우트 | 파일 | 백엔드 |
|---|---|---|---|
| 게시판 피드 | `/board` (탭) | `features/board/presentation/board_screen.dart` | `GET /bands/{id}/posts?cursor&limit` |
| 게시글 상세 | `/board/:postId` | `.../post_detail_screen.dart` | `GET/DELETE …/posts/{id}`, `POST /reports`, `POST /users/me/blocks` |
| 글 작성/수정 | `/board/new`, `/board/:postId/edit` | `.../post_compose_screen.dart` | `POST/PUT …/posts`, `POST …/media/upload-url` → R2 PUT → `…/media/{id}/complete`, `DELETE …/media/{id}` |

- 상태: `features/board/application/board_providers.dart` (`boardFeedProvider` — `loadMore`/`refresh`, `postDetailProvider` family).
- 첨부는 백엔드를 지나지 않는다 — presigned PUT URL로 R2에 직접 올린다(인터셉터 없는 별도 Dio).
- 하단 탭 "게시판"이 `showSoon` → 실제 브랜치(인덱스 3)로 승격. 남은 `showSoon`은 "정산" 뿐.
- **뺀 것**: 영상 인앱 재생(타일로만 표시), 개별 첨부(MEDIA) 신고 UI, 차단 해제 화면(C8).

### 구현 완료 (카카오 로그인 SDK)

| 파일 | 역할 |
|---|---|
| `pubspec.yaml` | `kakao_flutter_sdk_user: ^1.9.6` (설치된 건 1.10.0) |
| `lib/core/config/app_config.dart` | `kakaoNativeAppKey`(`KAKAO_NATIVE_APP_KEY`), `kakaoJavaScriptAppKey`(`KAKAO_JS_APP_KEY`) dart-define, `kakaoEnabled` |
| `lib/main.dart` | `kakaoEnabled` 일 때만 `KakaoSdk.init(...)` |
| `lib/features/auth/data/kakao_sdk.dart` | `fetchKakaoAccessToken()` — 카톡 설치 시 앱 전환, 아니면 계정 로그인. access token 반환 |
| `lib/features/auth/presentation/login_screen.dart` | "카카오로 계속하기" → `_kakao()` → 토큰 획득 → `AuthController.loginKakao(token)` → 백엔드 `POST /auth/kakao` |
| `android/app/src/main/AndroidManifest.xml` | 카카오 리다이렉트 activity (`kakao${KAKAO_APP_KEY}://oauth`) |
| `android/app/build.gradle.kts` | `local.properties` 의 `kakao.appKey` → manifestPlaceholder |

- 백엔드 변경 없음 — `POST /auth/kakao {accessToken}` 는 이전부터 존재(`AuthController.kakao`, `AuthService.kakaoLogin`).
- **앱 키 미설정이면** 카카오 버튼은 "준비 중" 스낵바만 뜬다. 로그인/이메일 흐름엔 영향 없음.
- 검증: `flutter pub get` OK(23 deps), `flutter analyze` 에러 0 (기존 info/warning 수준 그대로).
  실제 카카오 로그인 end-to-end 는 **앱 키 필요 → 미검증**.

#### 카카오 로그인을 실제로 켜려면 (사용자 작업)

1. **카카오 개발자센터**(https://developers.kakao.com) → 내 애플리케이션 → 앱 생성.
2. **앱 키** 탭에서 복사:
   - 웹 테스트 → **JavaScript 키**
   - Android/iOS 빌드 → **네이티브 앱 키**
3. **플랫폼 등록**:
   - 웹: 앱 → 플랫폼 → Web → 사이트 도메인에 `http://localhost:<포트>` 추가
     (`flutter run -d chrome` 은 포트가 매번 바뀌므로 `--web-port=5599` 고정 권장).
   - Android: 패키지명 `com.yeka.bandapp_client` + 키 해시(`keytool` 로 디버그 키스토어에서 추출).
4. **카카오 로그인 활성화**: 앱 → 카카오 로그인 → 활성화 ON, Redirect URI 는 SDK 가 자동 처리(웹은 도메인만).
   동의항목에서 **닉네임**(필수), **카카오계정(이메일)** 은 선택으로 켜면 백엔드가 이메일까지 받아옴.
5. **백엔드 `.env`** 에 카카오 키 (`app.kakao.app-id`, `app.kakao.admin-key` → `KAKAO_APP_ID`,
   `KAKAO_ADMIN_KEY`). 안 넣으면 `/auth/kakao` 가 503 `KAKAO_NOT_CONFIGURED`.
   `app-id` 는 앱 기본 정보의 **앱 ID(숫자)**, `admin-key` 는 어드민 키.
6. **클라이언트 실행**:
   ```powershell
   & C:\flutter\bin\flutter.bat run -d chrome --web-port=5599 `
     --dart-define=KAKAO_JS_APP_KEY=<자바스크립트 키>
   # 네이티브: --dart-define=KAKAO_NATIVE_APP_KEY=<네이티브 키>
   #   + client/android/local.properties 에  kakao.appKey=<네이티브 키>  한 줄 추가
   ```
- Android 네이티브 빌드는 여전히 개발자 모드 필요(§3-D).

- 라우팅: `lib/routing/app_router.dart` — go_router + 로그인 상태 기반 redirect.
  홈(`/home`)·캘린더(`/cal`)·지도(`/map`)는 `StatefulShellRoute.indexedStack`의 브랜치,
  탭바 UI는 `lib/routing/tab_shell.dart`(`TabShell`). 화면 없는 탭(정산·게시판)은
  브랜치 없이 `showSoon` 스낵바. 홈 내부에서 캘린더로 갈 땐 `context.go`(브랜치 전환).
- 네트워크: `lib/core/network/dio_client.dart` — 토큰 자동 부착, 401→refresh 1회 재시도.
- 상태관리: flutter_riverpod (코드젠 없음). 인증 상태: `features/auth/application/auth_controller.dart`.
- 아직 없는 화면(캘린더·지도·정산·게시판·알림·멤버관리)은 하단 탭/버튼에서
  `shared/widgets/soon.dart` 의 스낵바로 안내.

### 검증 결과

- `flutter analyze` → **에러 0**. 경고 15개(전부 `unawaited_return_in_try_block` — 기존
  repository 들과 동일한 `return unwrap(...)` 패턴), info 95개(`prefer_const`,
  `require_trailing_commas`, `withOpacity` deprecated). 기능 영향 없음. `dart format` 적용.
- `flutter build web` → 성공. (`flutter_secure_storage_web` 가 WASM 미지원이라 "Wasm dry run failed"
  경고는 뜨지만 기본 JS 빌드는 정상.)
- 1단계: `flutter run -d chrome` 로 로그인 화면까지 실렌더 확인(2026-09-03).
- 백엔드 붙여서 **end-to-end 는 아직 미검증** (1·2단계 모두. 아래 3-C 참조).

### 커밋 상태

- 1단계(`client/` 스캐폴딩~홈)는 커밋됨: `5a50dfb feat(client): Flutter 클라이언트 스캐폴딩 + 온보딩~밴드 홈`.
- 2단계(캘린더·일정) 변경분은 **아직 커밋 안 됨**. 대상:
  `client/lib/features/reservation/**`(신규), `client/lib/features/home/**`,
  `client/lib/routing/app_router.dart`, `client/lib/core/format/formatters.dart`,
  `docs/progress/client-02-*.md`, `docs/progress/client-DEVLOG.md`, `docs/progress/README.md`.
- `client/android`, `client/web`, `client/windows` 는 `.gitignore` 로 제외 상태 — 커밋할지는 팀 결정.

---

## 3. 로컬 환경 함정 (이 PC 기준 — 세션마다 다시 부딪힘)

### A. Flutter 경로 · 저장소 경로가 PC 마다 다름

- **PC #1**: 저장소 `E:\project\band`, Flutter `C:\flutter\bin`.
- **PC #2** (박장언, 2026-09-04~): 저장소 `C:\band\bandApp`, Flutter **`C:\src\flutter\bin`**
  (Flutter 3.47.2 / Dart 3.13.2, stable). Git Bash PATH 에는 `/c/src/flutter/bin/flutter` 로 잡힌다.
- 어느 쪽이든 PATH 등록이 없으면 **전체 경로로 호출**: `& C:\src\flutter\bin\flutter.bat <명령>`
  (PowerShell) 또는 `/c/src/flutter/bin/flutter <명령>` (Git Bash).
- `client/android`·`client/web`·`client/windows` 는 `.gitignore` 제외 → 새 PC/클론에서는
  없다. `flutter build web` 전에 `flutter create . --platforms web,android,windows` 로
  스캐폴딩을 한 번 생성한다(gitignore 라 커밋 안 됨). `flutter create` 가 만드는
  `test/widget_test.dart`(기본 카운터 테스트)는 삭제한다 — `MyApp` 참조라 테스트가 깨진다.

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
- **FCM 자격증명 함정 (2026-09-03 확인)**: `docker compose up --build` 로 app 을 재빌드하면
  기동 실패한다 — `.env` 의 `FCM_CREDENTIALS_HOST_PATH` 가 가리키는 JSON 파일이 저장소에 없어
  compose 가 `/run/secrets/fcm-credentials.json` 를 **디렉터리로** 마운트, `FcmPushSender` 생성 실패.
  우회: `.env` 에서 `FCM_CREDENTIALS_PATH=` (빈 값) 로 두면 FCM 을 건너뛰고 정상 기동.
  (실제 Firebase 서비스계정 JSON 을 저장소 루트에 두면 정식 해결.)
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

수동 확인 시나리오는 1단계 `client-01-onboarding-home.md` §5, 2단계 `client-02-calendar-reservation.md` §5.

---

## 5. 다음 할 일 (우선순위)

- ~~예약 캘린더 `/cal`~~ ✅ 2단계
- ~~일정 등록 폼 + 합주실 목록/등록(+주소 검색)~~ ✅ 2단계 (반복 설정 제외 — 백엔드 API 없음)
- ~~일정 상세 (참석 체크 · 멤버별 현황 · 셋리스트)~~ ✅ 2단계
- ~~정산 화면 (1인당 금액 · 납부 체크리스트 · 재계산)~~ ✅ 3단계 (`/reservations/:rid/settlement`)

1. ~~하단 탭바를 실제 화면으로 연결 (`ShellRoute` 로 전환)~~ ✅ 2026-09-04
   (`StatefulShellRoute.indexedStack`, `tab_shell.dart`).
2. ~~**합주실 지도** `/map` — 좌표 있는 합주실 마커 + 하단 목록~~ ✅ 2026-09-04
   네이버 지도(`flutter_naver_map`), Android/iOS 전용. 웹은 목록만. `NAVER_MAP_CLIENT_ID`
   dart-define + (네이티브) 개발자 모드 필요. 상세는 `client-04-room-map.md`.
3. ~~카카오 로그인 SDK 연동~~ ✅ 배선 완료. 앱 키 넣고 end-to-end 검증만 남음(위 "실제로 켜려면").
4. ~~알림 **설정** 화면 (`/settings/notifications`)~~ ✅ 2026-09-03 — 푸시 on/off + 리마인더 시점.
   상세는 `client-05-notification-settings.md`. **남은 것**: 디바이스 토큰 등록(FCM),
   실제 푸시 수신 처리, 정산 화면의 "미납자 알림" 버튼(백엔드에 미납 독촉 트리거 API 없음 — PROBLEMS §3).
5. ~~**게시판**(#11·#12)~~ ✅ 2026-09-04 (C6). 남은 것: 영상 인앱 재생, 차단 해제 화면(C8).
6. ~~정기 일정 규칙 등록/목록/삭제 + 일정 수정(PUT)·밴드장 승인/거절 UI~~ ✅ 2026-09-04 (C7).
   상세는 `client-07-reservation-recurring.md`.
7. **(C8, 다음)** 설정 나머지 — 밴드 설정(권한 모드·밴드장 위임·멤버 추방·밴드 나가기), 계정(내 정보·탈퇴), 차단 해제.
8. **(C9)** 알림 수신부(FCM: `firebase_messaging` + `POST /notifications/device-tokens`),
   클라이언트 CI(`flutter analyze` + `flutter test` GitHub Actions).
9. (정리) analyze info 줄이기, 폰트 번들(google_fonts 런타임 다운로드 대신), 날짜/시간 피커 다크 스타일.

## 6. 열린 결정 / 확인 필요

- ~~**정기(반복) 일정**~~ (해결): 백엔드 Phase 5 에 `POST/GET/DELETE /bands/{id}/recurring-rules`
  가 이미 있다(회차는 등록 시 8주분 자동 생성, 개별 회차 수정·취소는 일반 일정 API). C7 에서 붙인다.
- 홈 "이번 달 정산" 카드: 밴드 단위 정산 합계 API 없음 → 현재 값 `—`. 집계 엔드포인트를
  백엔드에 추가할지, 화면에서 일정별로 합산할지 결정 필요.
- 초대코드 UI: 백엔드는 8자 영숫자인데 목업은 6자리 숫자 키패드 → 현재 8자 텍스트 입력으로 구현.
- 일정 등록 폼의 "예약 방법": 목업은 전화/카톡 등 태그 선택인데 백엔드엔 `note` 자유 텍스트만 → 메모 한 칸으로 구현.
- **합주실 주소 검색**(해결): `GET /bands/{id}/rooms/search` 추가함. 다만 실제 결과가 뜨려면
  `NAVER_SEARCH_CLIENT_ID/SECRET`(네이버 개발자센터 앱) 를 `.env` 에 넣고 재기동해야 한다 —
  현재 로컬엔 미설정이라 항상 빈 목록. 사용자가 크리덴셜 보유 중이라 함(2026-09-03).

---

## 7. FCM 자격증명 파일 어디서 받나

두 종류가 있고 용도가 다르다. **지금 카카오 로그인 단계에선 둘 다 불필요** — 알림(§5-4) 갈 때 필요.

### (A) 백엔드용 — Firebase Admin SDK 서비스 계정 키 (JSON 1개)

`.env` 의 `FCM_CREDENTIALS_HOST_PATH` / `FCM_CREDENTIALS_PATH` 가 가리키는 그 파일 (§3-C 함정).

1. https://console.firebase.google.com → 프로젝트 선택(없으면 생성).
2. 좌측 상단 **⚙️ → 프로젝트 설정**.
3. **서비스 계정** 탭 → "Firebase Admin SDK" 섹션 → **새 비공개 키 생성** 버튼 → 확인 → JSON 자동 다운로드.
4. 그 JSON 을 저장소 루트에 두고(예: `E:\project\band\fcm-credentials.json`, `.gitignore` 확인),
   `.env` 에 `FCM_CREDENTIALS_HOST_PATH=./fcm-credentials.json` (또는 절대경로) 지정 후
   `docker compose up -d` 재기동. 이러면 §3-C 의 "빈 값으로 우회" 안 해도 됨.

> 이 키는 서버가 FCM 으로 푸시를 **보낼** 때 쓰는 마스터 자격증명. 절대 클라이언트/저장소에 커밋 금지.

### (B) 클라이언트용 — 앱 등록 설정 파일 (Flutter 푸시 수신)

같은 Firebase 콘솔 → 프로젝트 설정 → **일반** 탭 → "내 앱" 에서 앱 추가:

- **Android**: Android 앱 추가(패키지명 `com.yeka.bandapp_client`) → `google-services.json` 다운로드
  → `client/android/app/` 에 배치.
- **iOS**: iOS 앱 추가 → `GoogleService-Info.plist` → `client/ios/Runner/` 에 배치.
- 그 다음 `flutterfire configure` 또는 `firebase_messaging` 플러그인 수동 설정. (알림 화면 작업 시 진행)
