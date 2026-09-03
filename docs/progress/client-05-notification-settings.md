# 클라이언트 5단계 — 알림 설정 화면

> 목표 독자: 코드를 직접 쓰지 않고 작업을 지시하는 사람.

## 1. 한 줄 요약

푸시 알림을 켜고 끄고, "합주 시작 몇 분 전에 알려줄지"를 고르는 **알림 설정 화면**을 붙였다.
백엔드는 이미 있던 API(`/api/v1/notifications/settings`)를 그대로 쓴다 — 서버 코드 변경 없음.

## 2. 이 단계의 목표

`docs/progress/client-DEVLOG.md` §5-4 "알림 화면". 원래 항목은 "알림 화면 + 미납 리마인더"였지만,
백엔드에는 **알림 목록(수신함) API가 없다**. 있는 건 "내 알림 설정"(푸시 on/off + 리마인더 시점)과
"디바이스 토큰 등록"뿐이다. 그래서 이번엔 **설정 부분만** 만들었다. (자세한 이유는 PROBLEMS 문서 §1)

## 3. 무엇을 만들었나

| 파일 | 역할 |
|---|---|
| `client/lib/features/notification/data/notification_models.dart` | `NotificationSetting`(pushEnabled, reminderOffsets) + JSON 파싱 |
| `client/lib/features/notification/data/notification_repository.dart` | `GET /notifications/settings` 조회, `PUT` 로 전체 교체 |
| `client/lib/features/notification/application/notification_providers.dart` | `notificationSettingProvider` (FutureProvider, 계정 단위) |
| `client/lib/features/notification/presentation/notification_settings_screen.dart` | 화면 본체 |
| `client/test/notification_setting_test.dart` | `fromJson` 파싱 3케이스(기본값·리스트·빈 배열) |
| `client/lib/routing/app_router.dart` | 라우트 `/settings/notifications` 추가(루트 네비게이터, 풀스크린) |
| `client/lib/features/home/presentation/home_screen.dart` | 홈 헤더의 종 아이콘 → 이 화면으로 이동(기존엔 "준비 중" 스낵바) |

### 화면 구성

- **푸시 알림** 스위치 — 끄면 모든 푸시를 받지 않는다. 끈 상태에서는 아래 리마인더 영역이 흐려진다.
- **리마인더 시점** 칩 — `10분 전 / 30분 전 / 1시간 전 / 3시간 전 / 6시간 전 / 1일 전` 중 다중 선택.
  서버 상한이 5개라 6개째를 누르면 "최대 5개" 안내가 뜬다. 아무것도 안 고르면 "리마인더 없음".
- **저장** 버튼 — 바뀐 게 있을 때만 활성. 누르면 `PUT` → 서버가 정리한 값으로 화면을 다시 맞춘다.
- 화면 하단에 "이 기기에서 실제 푸시를 받으려면 Firebase 설정이 필요하다"는 안내 문구(현재 상태 그대로 설명).

## 4. 어떻게 동작하나

1. 화면 진입 → `notificationSettingProvider` 가 `GET /notifications/settings` 호출.
   서버는 설정이 없으면 기본값(`pushEnabled=true`, `reminderOffsets=[60]`)을 **만들어서** 준다.
2. 받은 값을 화면 로컬 상태로 1회 복사(`_seed`). 사용자가 스위치·칩을 만지면 로컬 상태만 바뀐다.
3. 저장 → `PUT /notifications/settings` 에 `{ pushEnabled, reminderOffsets }` 전체를 보냄.
   서버가 중복 제거·정렬·범위(1~1440분)·개수(≤5) 검증을 하고 정리된 결과를 돌려준다.
   그 결과로 화면을 다시 seed 하고 provider 를 무효화한다.
4. 저장 실패(검증 400 등)는 스낵바로 메시지만 보여주고 화면 상태는 유지.

## 5. 직접 확인하는 법

### 사전 준비

- 백엔드 실행(`docker compose up -d`, `http://localhost:8080/actuator/health` = 200).
  **FCM 자격증명은 필요 없다** — 이 API 는 FCM 키 없이도 동작한다(`NotificationController` 주석에 명시).
- 클라이언트 웹 실행:
  ```powershell
  cd E:\project\band\client
  & C:\flutter\bin\flutter.bat run -d chrome --web-port=5599
  ```

### 시나리오

1. 로그인 → 홈 우상단 **종 아이콘** 탭 → "알림 설정" 화면.
2. "푸시 알림" 스위치를 끈다 → 아래 칩 영역이 흐려짐 → **저장** 활성화 → 저장 → "저장했어요" 스낵바.
3. 다시 들어오면 스위치가 꺼진 상태로 떠야 한다(서버에 저장됨).
4. 스위치를 켜고 칩을 6개 누른다 → 6개째에서 "최대 5개" 안내.
5. 칩 3개만 남기고 저장 → 재진입 시 그 3개가 선택돼 있어야 한다.
6. (선택) 서버에서 직접 확인:
   ```bash
   curl -s http://localhost:8080/api/v1/notifications/settings -H "Authorization: Bearer <토큰>"
   ```

### 문제 해결

| 증상 | 원인 / 대응 |
|---|---|
| 화면이 "불러오지 못했어요" | 토큰 만료 → 앱이 자동 refresh 후 재시도. 계속되면 재로그인. 백엔드 500 이면 서버 로그 확인 |
| 저장이 "최대 5개" 없이 조용히 잘림 | 정상 — 서버가 개수 상한(기본 5)으로 자른 것. 프리셋은 6개지만 5개까지만 저장됨 |
| 스위치는 켜졌는데 푸시가 안 옴 | 예상된 상태. 수신부(디바이스 토큰·`firebase_messaging`) 미구현. PROBLEMS §1 참조 |

## 6. 실제 검증 기록

- `flutter test` → `notification_setting_test.dart` 3/3 통과.
- `flutter analyze` → **에러 0**. 경고는 기존 repository 들과 같은 `unawaited_return_in_try_block` 패턴 +2건.
- `flutter build web` → 성공(기존과 동일한 `flutter_secure_storage_web` WASM dry-run 경고만).
- 백엔드 붙인 end-to-end(웹에서 실제 저장/재조회)는 **이번 세션에서 미실행** — 자동화 파이프라인이
  백엔드만 돌리고(`./gradlew build`), 로컬에서 컨테이너를 안 띄운 상태로 커밋했다. §5 시나리오로 직접 확인 필요.

## 7. 알려진 이슈 / 제약

- **알림 수신함 없음**: 앱 안에서 "지난 알림 목록"을 볼 수 없다. 백엔드에 해당 테이블/엔드포인트가 없다.
- **디바이스 토큰 미등록**: `POST /notifications/device-tokens` 를 호출하는 코드가 없다.
  즉 설정을 켜도 이 기기로 푸시가 오지 않는다. `firebase_messaging` 플러그인 + `google-services.json` /
  `GoogleService-Info.plist` 배치 후 붙일 일(§DEVLOG 7-B).
- **"미납자 알림" 버튼 없음**: 정산 화면에서 미납 멤버에게 독촉 푸시를 보내는 기능은 백엔드에
  트리거 API 가 없어 미구현(PROBLEMS §3).
- 프리셋이 6개인데 서버 상한이 5개 — 6개째 선택은 막지만, 이미 5개 저장된 상태에서 다른 프리셋으로
  바꾸려면 하나 해제 후 선택해야 한다(의도된 동작).

## 8. 커밋 · CI 링크

- 브랜치: `feat/client-notification-settings`
- PR: (본문 하단 참조 — 머지 후 이 줄에 번호 기입)
- CI: 백엔드 `./gradlew build` 만 실행(클라 전용 변경이라 그대로 통과). 클라 CI 는 아직 없음(PROBLEMS §4).

## 9. 다음 단계 예고

게시판(#11·#12) — 백엔드 Phase 8(`board`) API 존재. 커서 페이징 피드 + R2 presigned 업로드.
그다음 설정 나머지(밴드 설정·계정/탈퇴)와 알림 **수신**부(FCM).
