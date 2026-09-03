# 밴드 합주 관리 앱 — Flutter 클라이언트

`docs/BUILD_PLAN.md` 의 백엔드(Phase 0~10 완료)를 소비하는 모바일/웹 클라이언트.
디자인 기준: `example/BandScreen.dc.html` 목업 (다크 톤 + 오렌지 `#FF6A2B` / 퍼플 `#A06BFF`).

> **작업을 이어받는다면 `../docs/progress/client-DEVLOG.md` 를 먼저 읽는다.**
> 현재 진행 상태, 다음 할 일, 이 PC의 로컬 환경 함정(Flutter가 PATH에 없음 →
> `C:\flutter\bin\flutter.bat`, DB 포트 5432를 네이티브 PostgreSQL 18이 선점 등)이 정리돼 있다.

## 현재 구현 범위 (1단계)

온보딩 흐름 → 밴드 홈까지.

| 화면 | 경로 | 백엔드 |
|---|---|---|
| 스플래시 | `/` | 저장된 토큰으로 `GET /users/me` 세션 확인 |
| 로그인 | `/login` | `POST /auth/login` (이메일). 카카오/네이버 버튼은 자리만 |
| 약관 동의 | `/terms` | 클라이언트 게이트 (서버 호출 없음) |
| 회원가입 | `/signup` | `POST /auth/signup` |
| 밴드 만들기/가입 | `/band-gate` | — |
| 밴드 만들기 | `/band-gate/create` | `POST /bands` |
| 초대코드 가입 | `/band-gate/join` | `POST /bands/join` |
| 밴드 홈 | `/home` | `GET /bands`, `GET /bands/{id}/members`, `GET /bands/{id}/reservations` |

아직 없는 화면(캘린더·지도·정산·게시판·알림·멤버 관리)은 하단 탭/버튼에서
"다음 단계에서 구현" 스낵바로 안내한다.

## 실행

Flutter 3.22+ (Dart 3.4+) 필요.

```bash
cd client

# 1) 네이티브 플랫폼 폴더 생성 (lib/ 와 pubspec.yaml 은 유지됨)
flutter create . --org com.yeka --project-name bandapp_client --platforms=android,ios,web

# flutter create 가 pubspec.yaml / lib 를 건드렸다면 되돌린다
git checkout -- pubspec.yaml analysis_options.yaml lib

# 2) 의존성
flutter pub get

# 3) 백엔드 기동 (프로젝트 루트에서)
#   docker compose up   → http://localhost:8080

# 4) 앱 실행
flutter run                                   # 기본: localhost:8080 (안드로이드 에뮬은 10.0.2.2 자동)
flutter run --dart-define=API_BASE_URL=http://192.168.0.10:8080   # 실기기에서 PC 백엔드로
```

## 구조

```
lib/
  core/
    config/        AppConfig — 앱 이름, API 베이스 URL
    theme/         색상 · 타이포 · ThemeData (다크 단일)
    network/       Dio (인증 헤더 + 401 시 refresh 1회 재시도), ApiException
    storage/       TokenStorage (flutter_secure_storage)
    format/         Fmt — 날짜/시간/금액 표기
  routing/         go_router + 인증 상태 기반 redirect
  features/
    auth/          splash · login · terms · signup, AuthController(Notifier)
    band/          band_gate · create · join, myBands/currentBand providers
    reservation/   일정 모델 · 리포지토리 (홈의 "다가오는 일정"용)
    home/          home_screen + 섹션 위젯, 밴드 전환 시트
  shared/widgets/  PrimaryButton, BackLink, GradientBackground, showSoon
```

## 상태관리 · 패키지

- `flutter_riverpod` — Provider / FutureProvider / Notifier (코드젠 없음)
- `go_router` — 선언적 라우팅, `refreshListenable` 로 로그인/로그아웃 시 리다이렉트
- `dio` — HTTP. `_AuthInterceptor` 가 access 토큰 부착, 401 → `/auth/refresh` 1회 후 원요청 재시도, 그래도 실패면 세션 종료 신호
- `flutter_secure_storage` — access/refresh 토큰
- `google_fonts` — Noto Sans KR / Bebas Neue / JetBrains Mono (첫 실행 시 네트워크로 폰트 다운로드; 추후 번들 가능)

## 목업과 백엔드가 다른 부분 (백엔드 기준으로 구현)

- **초대코드**: 목업은 6자리 숫자 키패드. 백엔드는 **8자 영숫자**(혼동문자 제외) → 8자 텍스트 입력으로 구현.
- **밴드 생성**: 목업엔 장르·내 파트 선택. 백엔드 `POST /bands` 는 `name` 만 받음 → 이름만 입력.
- **멤버 파트(악기)**: 백엔드 `BandMember` 에 파트 필드 없음 → 아바타·이름·역할(밴드장/멤버)만 표시.
- **소셜 로그인**: 백엔드는 이메일 + 카카오만. 네이버/구글 엔드포인트 없음 → 네이버 버튼 비활성, 카카오는 SDK 연동 전까지 "준비 중".
- **홈 "이번 달 정산"**: 밴드 단위 정산 합계 API 가 없음(정산은 일정별) → 자리만 표시하고 값은 `—`.
