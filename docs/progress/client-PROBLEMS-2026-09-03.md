# 클라이언트 작업 중 발견한 문제점 — 2026-09-03

> 이 세션에서 (1) 이전 세션의 미커밋 작업(탭바·지도·카카오 SDK)을 정리해 커밋하고
> (2) 알림 설정 화면을 새로 붙이면서 발견한 것들. 집에서 이어받을 때 참고.
> 브랜치: `feat/client-notification-settings` → PR/머지 완료.

---

## 1. 알림 "수신"이 아직 아무것도 안 된다 (설정만 됨)

- 이번에 만든 건 **알림 설정 화면**뿐이다: 푸시 on/off + "N분 전" 리마인더 시점.
  백엔드 `GET/PUT /api/v1/notifications/settings` 만 사용.
- 백엔드에 **알림 목록(수신함) 테이블/엔드포인트가 없다.** 앱에서 "지난 알림"을 못 본다.
  → 필요하면 백엔드에 `notification` 저장 + `GET /notifications` 를 새로 만들어야 함(스펙 논의 필요).
- **디바이스 토큰 등록 코드가 없다.** `POST /notifications/device-tokens` 를 아무도 안 부른다.
  설정을 켜도 이 기기로 FCM 푸시가 오지 않는다.
  → 할 일: `firebase_messaging` 플러그인 추가 + `client/android/app/google-services.json`,
    `client/ios/Runner/GoogleService-Info.plist` 배치(§client-DEVLOG.md 7-B) + 앱 시작 시 토큰 등록.
- 백엔드 발송 쪽도 `.env` 의 Firebase Admin SDK JSON 이 있어야 실제로 나간다(§client-DEVLOG.md 7-A, 3-C 함정).

## 2. [보안] 카카오 앱 키가 소스에 하드코딩돼 있었다 — 이번에 비움

- 이전 세션이 커밋하지 않고 남긴 `client/lib/core/config/app_config.dart` 에
  **실제로 보이는 카카오 네이티브 앱 키 / JavaScript 키가 `defaultValue` 로 박혀 있었다:**
  - `kakaoNativeAppKey` 기본값 `210fa438...`
  - `kakaoJavaScriptAppKey` 기본값 `5dc7d534...`
- 이 상태로 머지하면 저장소 히스토리에 키가 남는다. 그리고 `kakaoEnabled` 가 항상 true 가 되어
  "앱 키 미설정 시 준비 중 스낵바"라는 문서상 동작과도 어긋난다.
- **조치**: 두 기본값을 빈 문자열로 바꿔 커밋했다. 이제 실행 시
  `--dart-define=KAKAO_NATIVE_APP_KEY=... --dart-define=KAKAO_JS_APP_KEY=...` 로 주입해야 카카오가 켜진다.
- **집에서 할 일**:
  1. 그 키들이 살아있는 카카오 앱의 것이면 **카카오 개발자센터에서 재발급(rotate)** 을 권장.
     (git 히스토리에 안 올라가긴 했지만 이전 세션 로그/작업물에 노출됐을 수 있음.)
  2. 실행 스크립트나 IDE run configuration 에 dart-define 을 넣어두거나,
     `client/` 에 `.gitignore` 된 로컬 설정 파일 방식으로 관리.

## 3. 정산 화면의 "미납자 알림" 버튼은 못 만든다 (백엔드 API 없음)

- DEVLOG §5-4 에 "정산 화면의 '미납자 알림' 버튼 포함"이라고 적혀 있는데,
  백엔드에 **미납 멤버에게 독촉 푸시를 트리거하는 엔드포인트가 없다.**
  Phase 9 알림은 이벤트(새 일정·승인·정산 생성·취소)와 배치(리마인더·참석 독촉)만 있고,
  "정산 미납 독촉"은 수동 트리거가 없다.
- → 백엔드에 `POST .../settlement/remind-unpaid` 같은 걸 추가할지 결정 필요. 지금은 미구현으로 둠.

## 4. 클라이언트 CI 가 없다

- `.github/workflows/ci.yml` 은 백엔드 `./gradlew build` 만 돌린다.
  `client/**` 만 바뀐 PR 은 Flutter 쪽 검증 없이 초록불이 뜬다(그래서 이 PR 도 CI 는 통과함).
- → `flutter analyze` + `flutter test` 를 도는 job 을 추가하는 게 좋다.
  주의: 로컬 Flutter 가 **3.47.2 (Dart 3.13.2)** 로 특이 버전이라(§client-DEVLOG.md 3-A),
  `subosito/flutter-action` 에서 같은 버전이 안 잡힐 수 있음 — `channel: stable` 최신으로 맞추고
  로컬도 거기에 맞추는 편이 재현성에 낫다.

## 5. 백엔드 붙인 end-to-end 는 이번에도 미검증

- 1~5단계 화면 모두 `flutter analyze` 통과 + 웹 빌드 성공까지만 확인됨.
  실제로 로그인해서 저장/재조회가 도는지는 아직 아무도 안 돌려봤다(컨테이너 미기동 상태로 커밋).
- 알림 설정 화면 확인 시나리오는 `client-05-notification-settings.md` §5.

## 6. 네이티브 설정 파일이 저장소에 없다

- `client/.gitignore` 가 `android/`, `web/`, `windows/`, `local.properties` 를 제외한다.
- 그래서 카카오 redirect activity(`AndroidManifest.xml`), 키 해시, `google-services.json`,
  네이버 지도 네이티브 키 설정 등이 **저장소에 없다.** 새 PC 에서 네이티브 빌드하려면 다시 만들어야 함.
- → 팀 결정 필요: `client/android` 를 커밋할지(키는 dart-define/`local.properties` 로 분리),
  아니면 셋업 절차를 `client/README.md` 에 정리해둘지.

## 7. 참고 — 이번 세션에서 정리한 미커밋 작업

이전 세션이 구현·문서화만 하고 커밋 안 한 걸 `feat(client): 하단 탭바 … + 카카오 로그인 SDK 배선`
커밋으로 올렸다. 코드 자체는 `flutter analyze` 에러 0 으로 멀쩡했음. 내용:
- `routing/tab_shell.dart` — `StatefulShellRoute.indexedStack` 탭바(홈·캘린더·지도)
- `features/reservation/presentation/map_screen.dart` — 네이버 지도(Android/iOS 전용, 웹은 목록만)
- `features/auth/data/kakao_sdk.dart` — 카카오 로그인 토큰 획득
- `Room` 모델에 `lat`/`lng` 추가, `main.dart` 에 SDK 초기화 가드
