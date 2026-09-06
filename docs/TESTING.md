# 테스터에게 앱 전달하기

> 다른 사람 폰에서 밴듈을 써보게 하는 방법. 정식 배포([DEPLOY.md](DEPLOY.md))와는 별개로,
> **서버 없이 오늘 당장** 시작할 수 있는 길을 함께 적는다.
> 마지막 갱신: **2026-09-06**

---

## 0. 준비 상태 — 서버가 살아 있다 (2026-09-06)

**https://api.bandule.com 이 운영 중이다.** 예전에 적어 뒀던 "터널로 임시 노출" 은 이제 필요 없다.
테스터에게 줄 것은 **그 주소가 박힌 APK 하나**뿐이다.

> 예전 방식(Cloudflare Tunnel)은 §6 에 남겨 뒀다 — 서버가 없거나 내려간 동안에만 쓴다.

---

## 1. APK 만들기

```bash
cd client
flutter build apk --release   --dart-define-from-file=dart_defines.json   --dart-define=API_BASE_URL=https://api.bandule.com
```

결과물: `client/build/app/outputs/flutter-apk/app-release.apk`

**빠뜨리면 안 되는 것**

| | |
|---|---|
| `--dart-define-from-file=dart_defines.json` | 카카오 앱 키가 여기 있다. **빠뜨리면 카카오 로그인이 "앱 키 미설정" 으로 막힌다** — 실제로 겪었다 |
| `--dart-define=API_BASE_URL=...` | 앱 기본값은 `localhost` 다. **안 넣으면 테스터 폰에서 아무 데도 못 붙는다** |

두 값은 빌드하는 순간 앱에 구워진다. 나중에 바꾸려면 다시 빌드해야 한다.

**`--release` 인 이유** — 디버그 빌드는 280MB 가 넘어 배포에 부담이다. 릴리스는 훨씬 작고 빠르다.
서명은 아직 디버그 키로 하고 있어서(`build.gradle.kts` 의 `release` 블록) 스토어에는 못 올리지만,
직접 배포에는 문제가 없다. 디버그 키를 계속 쓰므로 **카카오 키 해시도 그대로**다.

> 릴리스 빌드는 평문 HTTP 를 허용하지 않는다. 로컬 백엔드(`http://10.0.2.2:8080`)에 붙이려면
> `--debug` 로 빌드해야 한다 — 평문 허용은 디버그 매니페스트에만 있다.

## 2. 잘 만들어졌는지 확인

릴리스 빌드는 Dart 코드가 기계어로 컴파일돼 `libapp.so` 안에 들어간다. **`aapt dump strings`
로는 안 보인다**(안드로이드 리소스만 훑기 때문에 0건으로 나온다 — 실제로 한 번 속았다).
APK 를 열어 그 파일을 직접 봐야 한다.

```bash
python -c "
import zipfile
d = zipfile.ZipFile('client/build/app/outputs/flutter-apk/app-release.apk').read('lib/arm64-v8a/libapp.so')
for p in (b'api.bandule.com', b'localhost:8080', b'10.0.2.2'):
    print(p.decode().ljust(20), 'FOUND' if p in d else 'not found')
"
```

`api.bandule.com` 이 **FOUND**, `localhost`·`10.0.2.2` 가 **not found** 면 제대로 박힌 것이다.

패키지명과 카카오 키 해시는 이렇게 본다:

```bash
AAPT="$LOCALAPPDATA/Android/sdk/build-tools/36.1.0/aapt2.exe"
"$AAPT" dump packagename client/build/app/outputs/flutter-apk/app-release.apk
"$LOCALAPPDATA/Android/sdk/build-tools/36.1.0/apksigner.bat" verify --print-certs   client/build/app/outputs/flutter-apk/app-release.apk | grep "SHA-1"
```

SHA-1 을 base64 로 바꾼 값이 카카오 콘솔에 등록된 키 해시와 같아야 한다.
디버그 키로 서명하는 동안은 `ahCJ5a5dXyiPh3x9ksny6yMbjzk=` 로 일정하다.

그래도 **폰에 깔아서 로그인까지 되는지 보는 게 가장 확실하다.**

## 3. 테스터에게 전달하기

### Firebase App Distribution (권장)

이미 Firebase 프로젝트가 있으므로 **추가 비용·심사가 없다.** 테스터는 메일 초대를 받고
링크로 설치한다. 새 버전을 올리면 알림도 간다.

```bash
npm install -g firebase-tools
firebase login
firebase appdistribution:distribute \
  client/build/app/outputs/flutter-apk/app-debug.apk \
  --app <Firebase 콘솔의 Android 앱 ID> \
  --testers "tester1@example.com,tester2@example.com" \
  --release-notes "첫 테스트 빌드"
```

앱 ID 는 Firebase 콘솔 > 프로젝트 설정 > 내 앱 에서 `1:숫자:android:문자열` 형태로 보인다.

### 그냥 APK 파일 보내기

카톡·드라이브로 보내도 된다. 테스터가 **"출처를 알 수 없는 앱 설치 허용"** 을 켜야 하고,
버전 관리가 안 돼서 누가 어떤 빌드를 쓰는지 모르게 된다. 한두 명이면 이걸로도 충분하다.

### Google Play 내부 테스트

가장 매끄럽지만 개발자 등록 $25 + 패키지명·릴리스 서명·스토어 등록이 먼저다
([LAUNCH_CHECKLIST.md](LAUNCH_CHECKLIST.md) 9~10단계). **지금 단계에서는 과하다.**

---

## 4. 테스터에게 미리 알려줄 것

- **이메일로 가입하라고 안내한다.** 카카오 로그인은 개발자 콘솔의 앱 상태에 따라
  등록된 팀원만 될 수 있다(확인 필요). 이메일 가입은 무조건 된다
- 밴드가 없으면 아무것도 안 보인다 → **초대코드를 먼저 주거나**, 직접 밴드를 만들게 한다
- 푸시 알림 권한 요청이 뜨면 허용해야 알림이 온다
- 아직 안 되는 것: 네이버 로그인(준비 중), 정기 일정 상세/수정, 캘린더 주간 뷰
  ([client-SCREENS.md](progress/client-SCREENS.md) §4)
- 서버가 상시 떠 있으므로 **내 PC 를 꺼도 테스터는 계속 쓸 수 있다**(§6 의 터널을 쓸 때만 예외)
- **배포 중에는 40초쯤 "서버가 잠시 응답하지 않아요" 가 뜬다.** 앱을 새 버전으로
  갈아 끼우며 재기동하는 시간이다 — 잠시 뒤 다시 누르면 된다

---

## 5. 테스트가 끝나면

- **운영 DB 에 테스터 계정·밴드가 그대로 쌓인다.** 정식 오픈 전에 한 번 비운다
  (`bandule backup` 으로 백업 먼저 → `deploy/backup/pg-restore.sh` 절차 참고)
- 받은 피드백은 [progress/NEXT.md](progress/NEXT.md) 에 적는다

## 6. 서버가 없거나 내려갔을 때 — Cloudflare Tunnel

내 PC 의 도커를 공개 HTTPS 로 잠깐 뚫는 방법이다. **평소에는 쓸 일이 없다.**

```bash
cloudflared tunnel --url http://localhost:8080
```

나온 주소를 `API_BASE_URL` 에 넣어 빌드한다. **내 PC 가 켜져 있어야만** 테스터가 쓸 수 있고,
주소는 실행할 때마다 바뀐다.
