# 다른 PC 에서 이어서 개발하기

> 저장소만 받아서는 **빌드가 안 되거나, 되더라도 로그인·지도·푸시가 죽는다.**
> 필요한 파일이 일부러 git 에 안 들어가 있기 때문이다(비밀값이거나 PC 마다 다르다).
> 마지막 갱신: **2026-09-08**

---

## 1. 옮길 파일

USB·개인 클라우드 등으로 직접 옮긴다. **메일·메신저·채팅에 붙여넣지 않는다.**

### 반드시 필요 — 없으면 기능이 죽는다

| 파일 | 없으면 | 비밀 |
|---|---|---|
| `client/dart_defines.json` | **카카오 로그인·지도가 막힌다** | 🔒 |
| `client/android/local.properties` 의 `kakao.appKey` 줄 | 카카오 로그인 리다이렉트가 깨진다 | 🔒 |
| `client/android/app/src/dev/google-services.json` | 개발 빌드에서 **푸시가 안 온다** | |
| `client/android/app/src/prod/google-services.json` | 운영 빌드가 **flavor 를 못 골라 빌드가 멈춘다** | |
| `.env` | 로컬 백엔드가 안 뜬다 (`docker compose up`) | 🔒 |

> `local.properties` 는 **통째로 옮기지 않는다.** `sdk.dir`·`flutter.sdk` 가 이 PC 의 설치
> 경로라 새 PC 에서는 틀린 값이다. 새 PC 에서 `flutter run` 을 한 번 돌리면 자동으로
> 만들어지고, 거기에 **`kakao.appKey=...` 한 줄만 손으로 추가**한다.

### 배포·서버 작업까지 할 거라면

| 파일 | 쓰임 |
|---|---|
| `~/.ssh/bandule_deploy` (와 `.pub`) | 서버 접속 🔒 |
| `.env.prod` | 운영 설정 🔒 |
| `secrets/` 폴더 | 개발용 FCM 서비스 계정 키 🔒 |

### 스토어에 올릴 거라면 — **잃어버리면 되돌릴 수 없다**

| 파일 | |
|---|---|
| `client/android/bandule-release.jks` | 릴리스 서명 키 🔒 |
| `client/android/key.properties` | 그 키의 비밀번호 🔒 |

> **이 둘을 잃어버리면 이 앱은 영원히 업데이트할 수 없다.** 새 PC 로 옮기는 김에
> 안전한 곳에 백업도 해 둔다. 없어도 빌드는 되지만 디버그 키로 서명되어
> 스토어에 올릴 수 없다.

---

## 2. 새 PC 에 깔 것

| | 확인 |
|---|---|
| Flutter SDK | `flutter doctor` |
| Android SDK + 빌드 도구 | Android Studio 를 깔면 같이 온다 |
| Java 21 | `java -version` (백엔드 빌드용) |
| Docker Desktop | 로컬 백엔드·테스트에 필요 |
| Python 3 | 릴리스 스크립트·약관 페이지 빌드 |
| Node.js + `npm i -g firebase-tools` | 테스터 배포. 깔고 `firebase login` (로그인 상태는 PC 마다 따로다) |
| Git | |

---

## 3. 옮긴 뒤 확인

```bash
cd C:\band\bandApp\client
flutter pub get
flutter analyze --no-fatal-warnings --no-fatal-infos lib
flutter test
```

**개발 빌드로 실행** — `--flavor dev` 를 빠뜨리면 빌드가 멈춘다:

```powershell
cd C:\band\bandApp; docker compose up -d
& "$env:LOCALAPPDATA\Android\sdk\platform-tools\adb.exe" -s <기기ID> reverse tcp:8080 tcp:8080
cd C:\band\bandApp\client; flutter run -d <기기ID> --flavor dev --dart-define-from-file=dart_defines.json
```

기기 ID 는 `adb devices` 로 본다.

**되는지 볼 것** — 여기까지 되면 옮기기가 끝난 것이다.

- [ ] 카카오 로그인 (`dart_defines.json` + `local.properties` 의 `kakao.appKey`)
- [ ] 합주실 지도 표시 (같은 카카오 키)
- [ ] 로그인 후 푸시 토큰 등록 (`google-services.json`)

**백엔드**:

```bash
cd C:\band\bandApp
./gradlew build          # Docker 가 떠 있어야 통합 테스트가 돈다
```

---

## 4. 먼저 읽을 문서

| | |
|---|---|
| [progress/NEXT.md](progress/NEXT.md) | **여기부터.** 지금 상태, 서버 정보, 걸려 있는 일 |
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | **겪은 문제와 해결.** 같은 함정을 두 번 밟지 않으려면 훑는다 |
| [OPERATIONS.md](OPERATIONS.md) | DB 접속(HeidiSQL 포함), 통계·쿠폰·신고 확인 쿼리 |
| [LAUNCH_CHECKLIST.md](LAUNCH_CHECKLIST.md) | 출시까지 남은 순서 |
| [TESTING.md](TESTING.md) | 테스터에게 빌드 보내는 법 |
| [progress/client-DEVLOG.md](progress/client-DEVLOG.md) | 클라이언트 작업 이력 |

> **Claude 세션은 PC 를 따라오지 않는다.** 대화 기록도, `~/.claude` 의 메모리도 이 PC 에만 있다.
> 그래서 이어받는 데 필요한 것은 전부 **git 에 들어 있는 문서**로 남긴다 — 새 PC 에서는
> 저장소를 받고 위 문서를 읽으면 된다. `CLAUDE.md` 는 세션마다 자동으로 읽히므로
> 규칙(문제를 고치면 TROUBLESHOOTING 에 적는다 등)은 따로 챙길 필요가 없다.

---

## 5. 자주 막히는 곳

| 증상 | 원인 |
|---|---|
| 빌드가 flavor 어쩌고 하며 멈춘다 | `--flavor dev` 를 빠뜨렸다 |
| 카카오 로그인이 "앱 키 미설정" | `--dart-define-from-file=dart_defines.json` 을 빠뜨렸다 |
| 카카오가 `keyHash validation failed` | 새 PC 의 디버그 키가 달라서다. 앱 실행 시 로그에 찍히는 키 해시를 카카오 콘솔에 **추가**한다 |
| 푸시가 안 온다 | `google-services.json` 이 없거나 flavor 가 어긋났다 |
| 통합 테스트가 전부 죽는다 | Docker 가 안 떠 있다 |
| 지도가 안 뜬다 | x86_64 에뮬레이터다. 카카오맵은 실기기(ARM)만 된다 |
