# 클라이언트 C9 — 알림 수신부(FCM) + 클라이언트 CI

## 1. 한 줄 요약

FCM **디바이스 토큰 등록·포그라운드 수신**을 배선했다(설정 파일 없으면 카카오 SDK 처럼
조용히 no-op). 그리고 **클라이언트 CI**(GitHub Actions: `flutter analyze` + `flutter test`)를
추가했다. 백엔드 변경 없음(Phase 9 `POST/DELETE /notifications/device-tokens` 사용).

## 2. 이 단계의 목표

- `client-DEVLOG.md` §5 C9: 알림 수신부(FCM), 클라이언트 CI.
- 백엔드는 Phase 9(`phase-09-notification-batch.md`)에서 디바이스 토큰·트리거·배치가 완성돼 있다.
  클라이언트는 토큰을 만들어 등록하는 부분만 없던 상태.

## 3. 무엇을 만들었나

| 파일 | 역할 |
|---|---|
| `pubspec.yaml` | `firebase_core: ^3.6.0`, `firebase_messaging: ^15.1.3` (승인받고 추가) |
| `features/notification/data/push_service.dart` | `PushService` — Firebase 초기화 시도 → 실패 시 비활성. 성공 시 권한 요청 → `getToken` → `POST /notifications/device-tokens` → `onTokenRefresh`·`onMessage` 구독. `scaffoldMessengerKey`(전역 SnackBar) |
| `features/notification/data/notification_repository.dart` | `registerDeviceToken()`, `unregisterDeviceToken()` |
| `app.dart` | `MaterialApp.router` 에 `scaffoldMessengerKey` 연결. `ref.listen(authControllerProvider…)` 로 로그인 시 `push.start()`, 로그아웃 시 `push.stop()`(토큰 해제) |
| `.github/workflows/client-ci.yml` | `client/**` 변경 시 `flutter pub get` → `flutter analyze --no-fatal-warnings --no-fatal-infos` → `flutter test` (Flutter 3.47.2 stable) |

### 설정 없이도 안전한 이유

- `Firebase.initializeApp()` 는 `google-services.json`(Android) / `GoogleService-Info.plist`(iOS) /
  웹 `firebase_options` 가 없으면 예외를 던진다 → `PushService._ensureFirebase` 가 잡아서
  `_available = false` 로 두고 조용히 끝낸다.
- 웹은 VAPID 키가 없으면 `getToken()` 이 던진다 → 역시 catch → no-op.
- 즉 **설정 파일을 넣기 전까지 푸시는 완전히 비활성**이고 앱의 나머지는 정상이다.
  (카카오 SDK·네이버 지도와 동일한 "키 없으면 꺼짐" 패턴)

### CI 가 `--no-fatal-warnings --no-fatal-infos` 인 이유

기존 클라이언트 코드에 `unawaited_return_in_try_block` 등 **경고 계열 lint 가 이미 다수** 있어
(`client-DEVLOG.md` §검증) 순정 `flutter analyze` 는 exit 1 이다. 우리 기준은
"analyze **에러** 0" 이므로 CI 도 에러에서만 실패하게 한다. info/warning 정리는 별도 정리 작업(§5-9).

## 4. 어떻게 동작하나

1. 로그인 성공 → `authControllerProvider.status == authenticated` → `app.dart` 리스너가 `push.start()`.
2. `start()`: Firebase 초기화 → 알림 권한 요청 → `getToken()` →
   `POST /notifications/device-tokens {token, platform}` (platform: `WEB`/`IOS`/`ANDROID`).
3. `onTokenRefresh` → 갱신 토큰 재등록. `onMessage`(앱이 켜져 있을 때 도착) →
   `scaffoldMessengerKey` 로 알림 제목/본문을 SnackBar 표시.
4. 로그아웃 → `push.stop()` → `DELETE /notifications/device-tokens?token=…`.

백그라운드/종료 상태 알림 표시는 OS 가 처리한다(별도 백그라운드 핸들러는 설정 붙일 때 추가).

## 5. 직접 확인하는 법

```powershell
cd C:\band\bandApp\client
& C:\src\flutter\bin\flutter.bat pub get
& C:\src\flutter\bin\flutter.bat analyze --no-fatal-warnings --no-fatal-infos   # exit 0
& C:\src\flutter\bin\flutter.bat test                                          # 16개 통과
& C:\src\flutter\bin\flutter.bat build web                                     # √ Built build\web
```

- 설정 파일이 없는 현재 상태에서 앱을 실행하면 콘솔에 `PushService: Firebase 미설정 — 푸시 비활성화`
  가 한 번 찍히고 그 외에는 평소와 같다.
- CI: PR 을 올리면 **Client CI** 워크플로가 `client/**` 변경에만 돈다.

### 실제 푸시를 켜려면 (사용자 작업)

`client-DEVLOG.md` §7-(B) 참조. 요약:

1. Firebase 콘솔에서 앱 등록 → `google-services.json`(Android, `client/android/app/`),
   `GoogleService-Info.plist`(iOS, `client/ios/Runner/`).
2. Android: `client/android/build.gradle.kts` 에 `com.google.gms.google-services` 플러그인,
   `app/build.gradle.kts` 에 적용. (FlutterFire 표준 절차 / `flutterfire configure`)
3. 웹: `flutterfire configure` 로 `firebase_options.dart` 생성 후 `main.dart` 에서
   `Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform)` 로 바꾸고,
   `web/` 에 `firebase-messaging-sw.js` + VAPID 키 설정.
4. 백엔드 `.env` 에 `FCM_CREDENTIALS_*` (§3-C, §7-A).

## 6. 검증 결과

- `flutter analyze` → **에러 0**(순정은 exit 1 — 기존 경고 때문. CI 는 `--no-fatal-*` 로 에러만 검사).
- `flutter test` → **16개 통과**(C9 에서 테스트 추가 없음 — PushService 는 Firebase 네이티브 의존이라 단위 테스트 대상 아님).
- `flutter build web` → JS 빌드 성공(firebase_messaging_web 포함).
- 실제 토큰 등록·푸시 수신은 **미검증**(Firebase 설정 파일 없음).

## 7. 알려진 이슈 / 제약

| 항목 | 상태 |
|---|---|
| 실제 푸시 | Firebase 설정 파일 필요 → 현재 no-op |
| 백그라운드 메시지 핸들러 | 미등록(OS 기본 표시에 의존). 설정 붙일 때 top-level `@pragma('vm:entry-point')` 핸들러 추가 |
| 알림 클릭 → 딥링크 | `onMessageOpenedApp` 미처리(해당 일정/게시글로 이동) — 설정 후 추가 |
| 로컬 알림 표시 | 포그라운드는 SnackBar 로만. `flutter_local_notifications` 는 미도입 |
| 정산 "미납자 알림" 버튼 | 백엔드에 미납 독촉 트리거 API 없음 (`client-PROBLEMS-2026-09-03.md` §3) — 그대로 |

## 8. 커밋 · CI

- 커밋: `feat(client): FCM 디바이스 토큰 등록·포그라운드 수신 배선 + 클라이언트 CI` (branch `feat/client-remaining`)
- 신규 의존성: `firebase_core`, `firebase_messaging`.
- 신규 워크플로: `.github/workflows/client-ci.yml`.

## 9. 다음 단계 예고

클라이언트 요구 화면 13개가 모두 ✅/🟡 상태가 됐다. 남은 것은 **정리·검증**:
end-to-end 수동 검증(백엔드 붙여 전 화면), analyze info 정리, 영상 재생·셋리스트 재정렬 등
개별 보강, 그리고 Firebase/카카오/네이버 키를 넣은 실제 통합 테스트.
