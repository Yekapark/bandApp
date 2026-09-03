# 클라이언트 4단계 — 합주실 지도 · 하단 탭바 ShellRoute

## 1. 한 줄 요약

하단 탭바를 `StatefulShellRoute` 로 바꿔 **홈·캘린더·지도**가 각자 스택을 유지하는
탭 전환이 되게 하고, **합주실 지도 화면(`/map`)** 을 네이버 지도로 만들었다.

## 2. 이 단계의 범위

`client-DEVLOG.md` §5 "다음 할 일" 1·2번.

- 하단 탭바 `ShellRoute` 전환
- 합주실 지도 `/map` — 좌표 있는 합주실 마커 + 하단 목록 (`GET /bands/{id}/rooms`)

**뺀 것**

- 마커 탭 → 목록 하이라이트 연동, 내 위치 표시, 마커 클러스터링 — 후속.
- 합주실 상세/수정 화면 — 등록 폼만 있음(2단계).
- 정산·게시판 탭의 실제 화면 — 여전히 `showSoon` 스낵바.

## 3. 무엇을 만들었나

### 하단 탭바 → `StatefulShellRoute`

| 파일 | 변경 |
|---|---|
| `lib/routing/tab_shell.dart` (신규) | `TabShell` — `StatefulNavigationShell` 을 받아 body + 하단 탭바. 브랜치 탭(홈·캘린더·지도)은 `goBranch`, 화면 없는 탭(정산·게시판)은 `showSoon`. 디자인은 기존 `_BottomTabs` 그대로 |
| `lib/routing/app_router.dart` | 홈·캘린더·지도를 `StatefulShellRoute.indexedStack` 브랜치로. 상세·폼·정산은 그대로 루트 네비게이터(풀스크린) |
| `lib/features/home/presentation/home_screen.dart` | `_BottomTabs` 클래스·`bottomNavigationBar` 제거 |
| `lib/features/home/presentation/widgets/home_sections.dart` | 홈 카드의 캘린더 이동을 `context.push` → `context.go`(브랜치 전환) |

- 효과: 캘린더/지도로 이동해도 탭바가 유지되고, 탭을 오가도 스크롤·화면 스택이 보존된다.
  같은 탭을 다시 누르면 그 브랜치 스택이 루트로 되감긴다(`initialLocation: i == current`).

### 합주실 지도 `/map`

| 파일 | 내용 |
|---|---|
| `pubspec.yaml` | `flutter_naver_map: ^1.4.4` 추가 |
| `lib/core/config/app_config.dart` | `naverMapClientId`(`NAVER_MAP_CLIENT_ID` dart-define), `naverMapEnabled` |
| `lib/main.dart` | `!kIsWeb && naverMapEnabled` 일 때만 `FlutterNaverMap().init(clientId: …)` |
| `lib/features/reservation/data/room_models.dart` | `Room` 에 `lat`/`lng`·`hasLocation` 추가 (백엔드 `RoomResponse` 는 이미 반환) |
| `lib/features/reservation/presentation/map_screen.dart` (신규) | `MapScreen` |

- **지도 사용 가능**(`!kIsWeb && naverMapEnabled`): 위쪽 3/5 에 `NaverMap`,
  좌표 있는 합주실마다 `NMarker`(캡션 = 이름). 아래 2/5 에 전체 합주실 목록.
  목록 타일 탭 → 그 합주실로 카메라 이동(`NCameraUpdate.scrollAndZoomTo`, zoom 15).
- **웹 / 키 미설정**: 지도 자리에 안내 문구, 목록만. 타일 탭은 비활성.
- 좌표 없는 합주실은 목록에 "위치 없음" 배지, 지도엔 안 찍힘(등록·선택엔 지장 없음).
- 목록 맨 아래 "＋ 새 합주실 등록" → `/cal/rooms/new`.
- 합주실이 하나도 없으면 빈 화면 + "＋ 합주실 등록".

- 상태: 기존 `roomsProvider(bandId)` 재사용(신규 provider 없음).
- 백엔드 변경 없음.

## 4. 어떻게 동작하나

```
하단 탭 "지도" ─▶ /map (셸 브랜치 2)
  ├ (네이티브+키) 지도에 마커, 아래 목록
  │     └ 목록 타일 탭 ─▶ 그 합주실로 카메라 이동
  ├ (웹/키 없음) 안내 문구 + 목록만
  └ "＋ 새 합주실 등록" ─▶ /cal/rooms/new ─(생성)─▶ 돌아오면 roomsProvider invalidate 로 갱신
```

## 5. 직접 확인하는 법

`client-DEVLOG.md` §3·§4 (Flutter PATH, DB 포트) 먼저.

### 웹 (지도 없이 목록만 — 이 PC 기본 경로)

```powershell
cd E:\project\band ; docker compose up -d
cd E:\project\band\client
& C:\flutter\bin\flutter.bat run -d chrome --web-port=5599
```

1. 로그인 → 홈. 하단 "지도" 탭.
2. 합주실이 없으면 "＋ 합주실 등록" → 이름·주소 넣고 저장 → 지도 탭 목록에 나타남.
3. 지도 영역에 "지도는 모바일 앱에서만 표시됩니다" 안내, 그 아래 목록.
4. 홈·캘린더·지도 탭을 오가며 스크롤 위치가 유지되는지, 탭바가 항상 떠 있는지 확인.

### 네이티브 (실제 지도 — 키 필요, 미검증)

1. NCP 콘솔 → Maps → Application 등록(패키지명 `com.yeka.bandapp_client`) → Client ID.
2. Windows 개발자 모드 ON (`start ms-settings:developers`).
3. ```powershell
   & C:\flutter\bin\flutter.bat run -d <android-device> `
     --dart-define=NAVER_MAP_CLIENT_ID=<Client ID>
   ```
4. 주소가 지오코딩된 합주실이 마커로 뜨고, 목록 탭 시 카메라가 이동하는지 확인.

### 문제 해결

- **지도 영역이 비어있고 안내 문구만**: 웹이거나 `NAVER_MAP_CLIENT_ID` 미설정. 정상.
- **네이티브에서 지도가 회색**: Client ID 오류거나 콘솔에 패키지명/번들ID 미등록.
  `FlutterNaverMap().init` 의 `onAuthFailed` 로그 확인(현재 미구현 — 필요 시 추가).
- **마커가 하나도 없음**: 합주실에 좌표가 없음(주소 지오코딩 실패/미설정). 목록엔 "위치 없음".
  백엔드 `NAVER_*` 지오코딩 키가 있어야 등록 시 좌표가 채워진다.
- **탭 전환 시 화면이 초기화됨**: `StatefulShellRoute` 가 아니라 `context.push` 로 들어온 경우.
  홈 카드는 `context.go` 여야 브랜치 전환된다.

## 6. 검증 결과

- `flutter analyze` → **에러 0.** 신규 파일 경고 0. 전체 info 127(기존 스타일 부채와 동일:
  `require_trailing_commas`·`prefer_const`·`withOpacity` deprecated). `dart format` 적용.
- `flutter build web` → **성공.** `flutter_naver_map`(네이티브 전용)이 있어도 웹 빌드는 통과
  (Dart API 는 웹 컴파일 세이프, `NaverMap` 위젯은 `_mapAvailable` 가드로 웹에서 미생성).
  `flutter_secure_storage_web` WASM dry-run 경고는 기존과 동일.
- **미검증**: 네이티브(Android/iOS) 실행 — 이 PC 개발자 모드/디바이스 없음 + 지도 Client ID 없음.
  백엔드 붙인 목록 로드·좌표 표시 end-to-end 도 미검증(1~3단계와 동일).

## 7. 알려진 이슈 / 제약

| 항목 | 목업 | 실제 구현 |
|---|---|---|
| 지도 | 지도 위 마커 + 하단 리스트 카드 | 동일하나 **네이티브 전용**. 웹은 목록만 |
| 마커 ↔ 목록 연동 | 마커 탭 시 카드 스크롤 | 목록 → 지도(카메라 이동)만. 반대 방향 미구현 |
| 내 위치 / 길찾기 | 있을 수 있음 | 없음 |
| 합주실 상세 | 마커/카드 탭 → 상세 | 상세 화면 없음(등록 폼만). 목록 탭은 카메라 이동만 |
| `flutter_naver_map` | — | pub get 시 "Developer Mode" 경고(웹 빌드엔 영향 없음), 네이티브 빌드엔 필요 |

## 8. 열린 결정 / 확인 필요

- **네이버 지도 Client ID**: NCP 콘솔에서 발급 필요(2단계 주소검색 `NAVER_SEARCH_*` 와 별개).
  없으면 네이티브에서도 지도가 인증 실패한다.
- 지도가 네이티브 전용이라, 클라이언트 검증을 계속 웹으로 할지 / 네이티브 검증 환경을
  갖출지(개발자 모드 + 에뮬레이터/기기) 결정 필요.
- `onAuthFailed` 처리(스낵바 등) 추가 여부 — 지금은 콜백 미전달.

## 9. 커밋 · CI 링크

- 브랜치: `feat/client-settlement` (3단계와 같은 브랜치에 누적). 커밋 예정:
  `feat(client): 하단 탭바 ShellRoute + 합주실 지도(네이버 지도)`.
- 백엔드 변경 없음 → 백엔드 CI 무관. 클라이언트 CI(`flutter analyze`)는 아직 없음(DEVLOG §5-8).
