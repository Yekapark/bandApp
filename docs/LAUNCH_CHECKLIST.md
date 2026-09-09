# 출시까지 순서표

> 무엇을 **어떤 순서로** 해야 하는지만 적는다. 배포 명령·복구 절차 같은 실행 방법은
> [DEPLOY.md](DEPLOY.md), 코드로 남은 일은 [progress/NEXT.md](progress/NEXT.md),
> 심사 요건 배경은 [BACKLOG.md](BACKLOG.md) 에 있다.
> 마지막 갱신: **2026-09-06**

---

## 0. 지금 상태

### 이미 있는 것 (다시 만들 필요 없다)

| | 확인 근거 |
|---|---|
| ✅ **카카오 개발자 앱** | `.env` 에 `KAKAO_APP_ID`·`KAKAO_ADMIN_KEY`·`KAKAO_REST_API_KEY` 가 채워져 있다. 로그인·장소검색·지도가 이 앱 하나를 쓴다 |
| ✅ **도메인 + 운영 서버 + 자동 배포** | `bandule.com`, `api.bandule.com` 운영 중. HTTPS·백업·감시까지 — [NEXT.md §0](progress/NEXT.md) |
| ✅ **기기 푸시** | 개발용 Firebase 로 **실기기에서 수신 확인**(2026-09-06). 운영 프로젝트는 아직 |
| ✅ **Cloudflare R2** | 운영 버킷 `bandule-prod` 사용 중 |
| ✅ **Firebase 프로젝트(FCM)** | **운영 `bandule-b94d2` / 개발 `bandapp-dev-67c6f` 분리 완료 (2026-09-06).** 앱은 빌드 flavor(`dev`/`prod`), 서버는 `.env.prod` 로 고른다 |
| ✅ **백엔드 Phase 0~11** | 배포 설정·자동 백업·복구까지. 운영에서 돈다 |
| ✅ **Flutter 앱 화면 13개** | 요구 화면 전부 도달 |
| ✅ **테스터 배포** | Firebase App Distribution, 그룹 `밴듈테스트`. `python tools/release_tester.py "설명"` 한 줄 — [TESTING.md](TESTING.md) |

### 없는 것 — 이게 지금의 병목이다

| | |
|---|---|
| ✅ ~~**도메인**~~ | **`bandule.com` 구매 완료 (2026-09-06)** |
| ✅ ~~**서버(VM)**~~ | **Vultr 서울, 운영 중 (2026-09-06)** |
| ❌ **릴리스 서명 키** | 지금은 디버그 키로 서명 중이라 스토어에 못 올린다. **여기부터가 진짜 병목** — 9단계 |
| ❌ **개인정보처리방침·이용약관 페이지** | 스토어 등록 필수 + 한국은 법적 요건 — 8단계 |
| ❌ **스토어 개발자 계정** | Google Play 1회 $25 / Apple 연 $99 — 10단계 |
| ✅ ~~**운영 R2 버킷 · 운영 Firebase**~~ | **분리 완료 (2026-09-06)** |

> **병목이 도메인에서 서명 키로 옮겨갔다.** 서버·배포·푸시·테스터 배포는 끝났다.
> 남은 것은 **스토어에 올리기 위한 준비**(서명 키 · 약관 · 계정)와 **운영/개발 자원 분리**다.

### 만들어는 놨는데 실제로는 안 되는 것

- ✅ ~~기기 푸시 알림~~ — **된다.** 2026-09-06 실기기 수신 확인.

- ❗ **초대 링크가 네이버 밴드 앱을 연다.** 랜딩 페이지가 `bandapp://invite/{code}` 로 앱을
  부르는데 **`bandapp://` 는 네이버 밴드가 쓰는 스킴이다.** 게다가 우리 앱에는 그 링크를 받는
  `intent-filter` 도, Flutter 쪽 처리 코드도 없다 — **스킴이 겹치지 않았어도 안 열렸다.**
  초대 링크는 한 번도 동작한 적이 없다. 상세와 고칠 순서는
  [NEXT.md 1-Z](progress/NEXT.md). **초대코드 8자를 손으로 넣는 화면은 멀쩡하다.**

- ⚠️ **iOS 는 손도 안 댔다.** `client/ios/` 가 `flutter create` 기본값 그대로다.
  맥과 연회비($99)가 필요하다. 안드로이드 출시 후로 미룬다.

> **핵심: 병목은 이제 릴리스 서명 키다.** 그게 있어야 스토어에 올릴 수 있고,
> 카카오 키 해시·딥링크 검증·ProGuard 확인이 전부 그 뒤에 붙는다.

---

## 1단계 — 도메인 ✅ **완료 (2026-09-06)** — `bandule.com`

인증서·딥링크·약관 URL·스토어 등록이 전부 여기서 시작한다. **다른 걸 하기 전에 이것부터.**

- 이미 Cloudflare 계정이 있으니 **Cloudflare Registrar** 에서 사는 게 편하다(원가 판매라 갱신비
  장난이 없고, DNS 가 자동으로 붙는다). 국내 등록기관에서 사도 되지만 그러면 네임서버를
  Cloudflare 로 바꾸는 단계가 하나 더 붙는다.
- `.com` 기준 대략 연 1.5만원 안팎. 도메인 값은 수시로 바뀌니 살 때 확인한다.

**2026-09-06 확인 (Verisign RDAP, 권위 있는 응답)**

| 후보 | |
|---|---|
| `bandule.com` | ✅ **등록 안 됨 — 살 수 있다** |
| `bandule.net` | ✅ 등록 안 됨 |
| `bandule.app` | ✅ 등록 안 됨 (`.app` 은 HTTPS 강제 TLD — 우리는 어차피 HTTPS 라 무관) |
| `bandule.kr` / `bandule.co.kr` | ❓ **확인 못 했다.** `.kr` 은 공개 RDAP 이 없어 자동 조회가 안 된다. `whois.kr` 에서 직접 확인할 것 |

도메인은 한번 정하면 스토어·약관·딥링크에 다 박히므로 **`.com` 을 잡아 두는 것을 권한다.**
- 서브도메인 계획을 미리 잡아 두면 나중에 안 꼬인다:

| 용도 | 확정값 |
|---|---|
| 백엔드 API | **`api.bandule.com`** — `.env.prod` 의 `DOMAIN`, 앱의 `API_BASE_URL` |
| 약관·개인정보처리방침·초대 랜딩 | **`bandule.com`** (정적, Cloudflare Pages 무료) |

`.env.prod.example` 에 이 값이 이미 들어가 있다. Cloudflare Registrar 로 샀으므로 DNS 가 자동으로 붙어 있어 3단계는 A 레코드 한 줄이면 끝난다.

---

## 2단계 — 서버 ✅ **완료 (2026-09-06)** — Vultr 서울

**Oracle Cloud Always Free (ARM Ampere A1)** 를 먼저 노린다. 평생 무료이고 사양이
이 서비스에는 과할 정도다. 다만 **인기 리전은 용량이 자주 없어서 생성이 몇 번씩 실패한다** —
며칠 걸릴 수 있으니 1단계와 병행해서 시도해 두는 게 좋다.

- 안 되면 저가 VPS(Contabo·Vultr 등) 월 5~7천원대. `docs/DESIGN.md` §3 의 예산 안이다.
- **메모리 2GB 이상.** 1GB 면 PostgreSQL + JVM 이 빠듯하다.
- Ubuntu 22.04 이상.

**끝나면**: 서버 공인 IP 가 생긴다.

---

## 3단계 — DNS 연결 ✅ **완료 (2026-09-06)**

Cloudflare 대시보드에서 `api.<도메인>` A 레코드를 서버 IP 로.
**이 시점에는 회색 구름(DNS only)으로 둔다** — 인증서를 먼저 받아야 한다.

---

## 4단계 — 첫 배포 ✅ **완료 (2026-09-06)**

[DEPLOY.md](DEPLOY.md) §1~§2 를 그대로 따라간다. 요약하면 서버에서:

1. 도커 설치, 방화벽(80·443·SSH)
2. 리포 클론 → `.env.prod` 채우기 (비밀번호 3종은 `openssl rand` 로 새로 만든다.
   **로컬 `.env` 값을 그대로 쓰지 않는다**)
3. `sh deploy/init-letsencrypt.sh` — 인증서 발급 + Nginx 기동
4. `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d`
5. `curl https://api.<도메인>/actuator/health` 가 `{"status":"UP"}`

> 설정을 시험하는 중이라면 `STAGING=1 sh deploy/init-letsencrypt.sh` 로 연습용 인증서를
> 받는다. 진짜 인증서는 발급 횟수 제한이 있어서 헤매다 걸리면 며칠 기다려야 한다.

**여기서 처음으로 "인터넷에 올라간 밴듈"이 생긴다.**

---

## 5단계 — Cloudflare 주황 구름 ⚠️ **켰다가(2026-09-06) 회색으로 되돌아가 있음**

> **현재 상태 (2026-09-09 확인)**: `api.<도메인>` 은 **회색 구름(DNS only)** 이다
> (`nslookup api.<도메인>` 이 오리진 IP 를 그대로 준다). 2026-09-06 에 주황으로 켰다는
> 기록은 있는데 되돌린 기록이 없다 — **왜 되돌렸는지 아무도 모른다.** Cloudflare 대시보드의
> **감사 로그**(계정 관리 → 감사 로그)에 DNS 레코드 변경 이력이 남으니 거기서 확인한다.
>
> 되돌린 이유를 확인하기 전까지는 주황으로 다시 켜지 않는다. 무료 플랜의 Bot Fight Mode 가
> 앱의 `Dart/3.13 (dart:io)` 요청을 봇으로 보고 막는 식의 이유였을 수 있다.
>
> 회색인 동안 `app.conf.template` 의 realip include 는 **꺼져 있어야 한다**
> ([DEPLOY.md](DEPLOY.md) §1 의 빨간 박스). 주황으로 다시 켤 때 같이 되살린다.

인증서가 나온 뒤에 켠다. 서버 진짜 IP 가 감춰진다.

1. SSL/TLS 모드를 **`Full (strict)`** 로 먼저 바꾼다 — `Flexible` 이면 무한 리다이렉트가 난다
2. `api.<도메인>` 을 주황 구름으로 전환
3. `curl https://api.<도메인>/actuator/health` 재확인

접속자 IP 판정은 이미 처리돼 있다([DEPLOY.md](DEPLOY.md) §1). 설정이 맞는지는
`sh deploy/nginx/test-realip.sh` 로 확인한다.

---

## 6단계 — 자동 배포 ✅ **완료 (2026-09-06)** — main 머지 시 자동

GitHub 리포지토리 시크릿 4개(`DEPLOY_HOST`·`DEPLOY_USER`·`DEPLOY_KEY`·`DEPLOY_PORT`)를 넣고,
`main` 에 커밋해서 실제로 배포가 도는지 본다. 실패하면 워크플로 로그에 그대로 찍힌다.

> 그때까지는 `main` 에 푸시할 때마다 **이미지 빌드·GHCR 업로드까지만 돌고 SSH 단계는
> 건너뛴다.** 서버가 없다고 워크플로가 빨갛게 실패하지는 않는다. 즉 **지금도 코드를 넣으면
> 배포 가능한 이미지가 계속 쌓이고 있고**, 서버가 생기는 순간 시크릿만 채우면 이어진다.

같이 할 것: 서버 크론에 백업 등록 ([DEPLOY.md](DEPLOY.md) §4).

---

## 7단계 — 카카오 콘솔 정리 (30분, 서버와 무관하니 언제든 가능)

**지금 당장 할 수 있고, 안 하면 카카오 로그인이 막힌다.**

- [ ] **패키지명을 `com.yeka.bandule` 로 고친다** — 개발자 콘솔 > 내 애플리케이션 >
      앱 설정 > 플랫폼 > Android. 2026-09-06 에 `com.example.bandapp_client` 에서 바꿨다.
      키 해시는 서명 키에서 나오는 값이라 그대로 두면 된다
- [ ] 릴리스 키스토어를 만든 뒤에는 **그 키의 해시도 추가**한다(디버그 것과 다르다, 9단계)
- [ ] **비즈앱 전환 필요 여부 확인** — 이메일 같은 개인정보 항목을 받으려면 검수가 필요할 수
      있다. 사업자등록 요구 여부를 콘솔에서 직접 확인할 것. 백엔드는 **이메일이 없어도 동작하도록**
      만들어져 있어서(소셜 가입자 `email` NULL 허용) 검수가 늦어져도 막히지는 않는다

---

## 7-B단계 — Firebase 운영 프로젝트 + 기기 푸시 살리기 (2~3시간, 서버와 무관)

**코드 배선은 끝났다 (2026-09-06).** `com.google.gms.google-services` 그래들 플러그인을
붙였고, `android/app/google-services.json` 이 **있으면 적용되고 없으면 건너뛴다** —
카카오 키가 없을 때와 같은 방식이라, 설정 파일이 없어도 빌드는 그대로 된다.
Dart 쪽(`PushService`)·매니페스트 권한(`POST_NOTIFICATIONS`)·`FirebaseMessagingService` 는
원래 다 들어 있었다.

**남은 건 콘솔 작업뿐이다.** 아래를 하면 그 순간부터 푸시가 온다.

- [ ] **운영 Firebase 프로젝트 생성** (지금 있는 개발용과 분리)
- [ ] 그 프로젝트에 **Android 앱 등록 — 패키지명 `com.yeka.bandule`**
      (7단계에서 카카오 콘솔에 넣는 것과 같은 값)
- [ ] `google-services.json` 을 받아 **`client/android/app/` 에 넣는다.**
      이 파일은 `client/android/.gitignore` 가 막으므로 커밋되지 않는다 — PC 마다 직접 넣는다
      (`local.properties` 와 같은 취급). 비밀값이라서가 아니라, 커밋하면 모든 빌드가
      한쪽 Firebase 프로젝트로 고정되기 때문이다
- [ ] 서비스 계정 JSON(비공개 키)을 받아 운영 서버의 `.env.prod` 에 연결
      (`FCM_PROJECT_ID`, `FCM_CREDENTIALS_HOST_PATH`)
- [ ] **실기기로 확인** — 로그인 후 토큰이 등록되는지, 일정 리마인더가 실제로 오는지

> 개발용 프로젝트를 그대로 써도 동작은 한다. 다만 테스트 알림이 실사용자에게 가거나 그 반대가
> 되면 곤란하니, R2 와 같은 이유로 나누는 것을 권한다.

**알림 형태는 이미 정해져 있다** — 백엔드가 `notification` + `data` 를 함께 보내므로
(`FcmPushSender`), 앱이 꺼져 있거나 백그라운드면 **안드로이드가 알아서 알림창에 띄우고**,
앱이 떠 있으면 `PushService` 가 스낵바로 보여 준다. 별도 처리 코드가 더 필요하지 않다.

## 8단계 — 약관·개인정보처리방침 (반나절)

스토어 등록에 **URL 입력이 필수**고, 한국에서 실사용자를 받으면 개인정보처리방침 게시는
법적 요건이다.

- Cloudflare Pages 에 정적 페이지로 무료 호스팅 (`<도메인>/privacy`, `<도메인>/terms`)
- 개인정보처리방침에 반드시 넣을 것: **탈퇴 후 90일간 이메일·이름·카카오 식별자를 보관한 뒤
  파기** (코드가 그렇게 동작한다 — `app.withdrawal.retention-days`)
- 수집 항목: 이메일·이름·카카오 식별자, 밴드/일정/정산 기록, 업로드한 사진·영상, 기기 푸시 토큰

---

## 9단계 — 릴리스 빌드 준비 (반나절)

**그래들 배선은 끝났다 (2026-09-06).** `android/key.properties` 가 있으면 그 키로 서명하고,
없으면 디버그 키로 넘어간다 — 키가 없는 PC 에서도 빌드는 그대로 된다. **남은 건 키를
만드는 일뿐이고, 그건 비밀번호를 쥐는 사람이 직접 해야 한다.**

- [ ] **키스토어 만들기 — 지시자가 직접.** 아래를 `client/android/` 에서 실행한다.
      비밀번호를 두 번 묻는데(키스토어 / 키), **같은 값으로 해도 된다.**

      keytool -genkeypair -v -keystore bandule-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias bandule

- [ ] **`client/android/key.properties` 를 만든다** (아래 4줄, 비밀번호는 방금 정한 값):

      storePassword=...
      keyPassword=...
      keyAlias=bandule
      storeFile=bandule-release.jks

- [ ] **`.jks` 파일과 비밀번호를 안전한 곳에 백업한다.** 둘 다 `.gitignore` 가 막고 있어
      저장소에는 안 들어간다. **잃어버리면 그 앱은 영원히 업데이트할 수 없다** — 같은
      패키지명으로 새 키를 쓴 앱은 스토어가 업데이트로 받아 주지 않는다
- [x] **릴리스 키 생성 완료 (2026-09-06)** — `client/android/bandule-release.jks`,
      인증서 `CN=yeka, L=seoul`. 이 키로 서명한 APK 를 실제로 만들어 확인했다

- [ ] **카카오 키 해시 추가** — 서명이 바뀌면 카카오 로그인이 막힌다. 콘솔 > 내 애플리케이션 >
      앱 설정 > 플랫폼 > Android > 키 해시에 **추가**한다(기존 디버그 값은 개발 빌드에
      필요하니 지우지 않는다):

          7zGOncUg+QW8Yt2dmsgmgmI5TPQ=

- [ ] **`ANDROID_SHA256_CERT_FINGERPRINTS` 를 `.env.prod` 에** (딥링크 App Links 검증용):

          C2:6B:0E:24:CD:CB:39:BF:1A:E4:30:62:07:13:3A:93:77:96:06:EE:18:39:D8:E2:A8:96:C0:85:CE:9B:0B:85

> **이 값들은 비밀이 아니다.** APK 안 인증서에서 누구나 읽을 수 있어 저장소에 적어 둔다.
> 비밀은 `.jks` 파일과 비밀번호 쪽이고, 그건 `.gitignore` 가 막는다. 다시 뽑으려면:
>
>     "$LOCALAPPDATA/Android/sdk/build-tools/36.1.0/apksigner.bat" verify --print-certs <apk>
>
> 나온 SHA-1 을 base64 로 바꾸면 카카오 키 해시, SHA-256 을 콜론으로 끊으면 딥링크 지문이다.

> **서명이 바뀌면 기존 설치본을 업데이트할 수 없다.** 안드로이드가 서명이 다른 앱을
> 업데이트로 받지 않는다 — 테스터는 **기존 앱을 지우고 새로 설치**해야 한다. 스토어에
> 올린 뒤에는 이 키를 절대 바꿀 수 없으니, 지금 백업해 둔다.
- [ ] **ProGuard 켜기**(`isMinifyEnabled`) — 카카오맵 규칙은 이미 넣어 뒀지만 아직 꺼져 있다.
      켠 뒤 **릴리스 빌드로 지도·로그인을 다시 확인**한다(난독화가 SDK 를 깨는 일이 흔하다)
- [ ] **릴리스 빌드는 서버 주소를 넘겨야 한다.** 앱 기본값은 로컬(`localhost` / 에뮬레이터
      `10.0.2.2`)이라, 그대로 빌드하면 아무 데도 붙지 못한다:

      flutter build appbundle --dart-define=API_BASE_URL=https://api.bandule.com --dart-define=KAKAO_NATIVE_APP_KEY=...

      `dart_defines.json` 을 쓴다면 운영용 파일을 따로 만들어 `--dart-define-from-file` 로 넘긴다

---

## 10단계 — 스토어 등록 (심사 대기 포함 1~2주)

| | 비용 |
|---|---|
| Google Play 개발자 등록 | 최초 1회 $25 |
| Apple Developer Program | 연 $99 |

- **안드로이드 먼저.** iOS 는 아직 `client/ios/` 가 `flutter create` 기본값이라 손댈 게 더 많고,
  맥과 연회비가 필요하다
- 심사 대응은 이미 코드에 들어가 있다 — 계정 삭제(Phase 1), 신고·차단(Phase 8)

**2026-09-06 — 개발자 계정 등록을 시작했다(신원 확인 심사 중).** 심사가 끝나면 아래가 필요하다.

- [ ] **약관·개인정보처리방침 URL** — `bandule.com/privacy`, `bandule.com/terms`
      (Cloudflare Pages 연결만 하면 된다)
- [ ] **데이터 안전 설문** — 스토어 등록 양식의 필수 항목이다. 답할 내용은
      [legal/privacy-facts.md](legal/privacy-facts.md) 에 그대로 있다:
      수집 항목, 국외 전송(있음), 암호화(있음), 계정 삭제 가능(있음)
- [ ] **앱 아이콘·스크린샷·설명문** — 스토어 페이지에 들어갈 것. 아이콘은 이미 있고
      스크린샷은 실기기에서 찍으면 된다
- [ ] **콘텐츠 등급 설문**
- [ ] **AAB 로 빌드** — 스토어는 APK 가 아니라 `.aab` 를 받는다.
      `flutter build appbundle --release --dart-define-from-file=dart_defines.json
      --dart-define=API_BASE_URL=https://api.bandule.com`
      (테스터 배포용 `release_tester.py` 는 APK 를 만든다 — 스토어용과 다르다)
- [ ] **ProGuard 켠 뒤 재확인** — 9단계

---

## 테스터에게 미리 돌려보게 하려면

**이미 하고 있다.** Firebase App Distribution, 그룹 `밴듈테스트`.
`cd client && python tools/release_tester.py "설명"` 한 줄이면 빌드부터 배포까지 간다 —
[TESTING.md](TESTING.md).

## 병행 가능 / 순서에 안 걸리는 것

- **[NEXT.md](progress/NEXT.md) §1-B 실기기 검증 체크리스트** — 영상 첨부·정산 유지·밴드 삭제 등
  아직 눈으로 확인 안 한 것들
- **[NEXT.md](progress/NEXT.md) §1-A "BOTTOM OVERFLOWED" 재현** — 아직 원인 미확인
- **7-B 단계 운영 Firebase 분리** — 지금은 개발용 프로젝트를 앱·서버가 함께 쓴다

---

## 운영 R2 버킷 만들기 (4단계 즈음, 30분)

지금 있는 건 `bandapp-media-dev` 개발용 하나다. 실사용자의 사진·영상과 개발 중 테스트 파일이
같은 곳에 섞이면 나중에 정리가 불가능해진다. **DB 백업도 같은 버킷의 `db-backups/` 에 올라가므로**
더더욱 나눠야 한다 — 개발 중에 버킷을 비우다 운영 백업을 지우는 사고가 난다.

- [ ] Cloudflare 대시보드 > R2 > 운영 버킷 생성 (예: `bandule-media`)
- [ ] 그 버킷에 접근하는 **API 토큰을 새로 발급** (개발용 키를 그대로 쓰지 않는다)
- [ ] `.env.prod` 의 `R2_BUCKET`·`R2_ACCESS_KEY_ID`·`R2_SECRET_ACCESS_KEY` 에 넣는다
- [ ] 버킷은 **비공개로 유지한다.** 앱은 짧은 만료의 presigned URL 로만 접근한다 —
      공개로 바꾸면 밴드 사진·영상이 주소만 알면 누구나 보이게 된다
