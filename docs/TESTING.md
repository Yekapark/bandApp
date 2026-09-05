# 테스터에게 앱 전달하기

> 다른 사람 폰에서 밴듈을 써보게 하는 방법. 정식 배포([DEPLOY.md](DEPLOY.md))와는 별개로,
> **서버 없이 오늘 당장** 시작할 수 있는 길을 함께 적는다.
> 마지막 갱신: **2026-09-06**

---

## 0. 지금 왜 그냥은 안 되는가

지금 폰에 깔린 앱은 서버 주소가 `http://localhost:8080` 이다. `adb reverse` 로 USB 를 통해
내 PC 로 넘겨주고 있어서 도는 것이라, **USB 를 뽑으면 그 폰에서도 안 된다.**
테스터 폰에는 USB 가 없다. 그래서 **인터넷에서 닿는 서버 주소**가 먼저 필요하다.

필요한 것은 세 가지다.

| | |
|---|---|
| ① 인터넷에서 닿는 백엔드 | 아래 A안(터널) 또는 B안(진짜 서버) |
| ② 그 주소가 박힌 APK | 빌드할 때 `--dart-define` 으로 넣는다 |
| ③ 테스터에게 APK 전달 | Firebase App Distribution 권장 |

---

## 1. 백엔드를 인터넷에 노출하기

### A안 — Cloudflare Tunnel (서버 없이, 오늘 가능) ⭐ 먼저 이걸로 시작

내 PC 의 도커를 그대로 두고 공개 HTTPS 주소만 하나 뚫는다. 무료이고 설정이 거의 없다.

```bash
winget install --id Cloudflare.cloudflared
cloudflared tunnel --url http://localhost:8080
```

실행하면 `https://<임의문자열>.trycloudflare.com` 주소가 뜬다. 그게 서버 주소다.

**알고 있어야 할 것**

- **내 PC 가 켜져 있고 도커가 떠 있어야만** 테스터가 쓸 수 있다. PC 를 끄면 앱이 먹통이 된다
- 주소는 실행할 때마다 바뀐다. 고정하려면 Cloudflare 계정에 터널을 만들어
  `test-api.bandule.com` 에 붙인다(`api.bandule.com` 은 나중에 진짜 서버용으로 남겨 둔다)
- **개발용 DB 에 테스터 데이터가 섞인다.** 테스트가 끝나면 정리할 것을 염두에 둔다
- 인터넷에 열리는 것이므로, `.env` 의 `JWT_SECRET`·`DB_PASSWORD` 가 예시값이면 바꾼다

### B안 — 진짜 서버 (정식)

[LAUNCH_CHECKLIST.md](LAUNCH_CHECKLIST.md) 2~5단계. 서버를 잡고 `api.bandule.com` 을 붙인다.
한 번 해두면 PC 를 꺼도 테스터가 계속 쓸 수 있다. **결국 가야 할 길이다.**

---

## 2. 그 주소가 박힌 APK 만들기

앱의 기본값은 로컬이라, **빌드할 때 주소를 넣지 않으면 테스터 폰에서 아무 데도 못 붙는다.**

```bash
cd client
flutter build apk --debug \
  --dart-define-from-file=dart_defines.json \
  --dart-define=API_BASE_URL=https://<터널주소-또는-api.bandule.com>
```

- `--dart-define-from-file=dart_defines.json` **을 빠뜨리면 카카오 앱 키가 안 들어가서**
  카카오 로그인이 "앱 키 미설정" 으로 막힌다. 실제로 한 번 겪었다
- 뒤에 쓴 `--dart-define` 이 파일의 값을 덮어쓴다
- 결과물: `client/build/app/outputs/flutter-apk/app-debug.apk`

**왜 `--debug` 인가** — 릴리스 빌드는 서명 키가 아직 없고(9단계), 평문 HTTP 도 막혀 있다.
디버그 빌드는 용량이 크고 조금 느리지만 테스트에는 문제가 없다. HTTPS 터널을 쓰면
릴리스 빌드도 가능하지만, 서명부터 갖춘 뒤에 넘어가는 게 순서다.

---

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
- **A안(터널)이면 "내 PC 가 꺼져 있으면 앱이 안 된다"** 는 것을 반드시 말해 둔다

---

## 5. 테스트가 끝나면

- 터널을 쓴 경우: `cloudflared` 를 끄면 즉시 접근이 막힌다
- 개발 DB 에 섞인 테스터 계정·밴드 정리
- 받은 피드백은 [progress/NEXT.md](progress/NEXT.md) 에 적는다
