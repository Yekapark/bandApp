# 다음에 이어서 할 일

> 살아있는 문서. 끝난 항목은 지우고, 새로 생긴 건 여기에 적는다.
> 오늘까지의 작업 내용은 [2026-09-05-brand-notifications-settlement-video.md](2026-09-05-brand-notifications-settlement-video.md)
> , [2026-09-05-plan-lifecycle-and-media-fix.md](2026-09-05-plan-lifecycle-and-media-fix.md),
> [2026-09-05-band-delete.md](2026-09-05-band-delete.md), [phase-11-deploy.md](phase-11-deploy.md).
> 마지막 갱신: **2026-09-08**

---

## 0. 지금 상태 (이어받을 때 먼저 볼 것)

### 🟢 운영 서버가 살아 있다 (2026-09-06)

**https://api.bandule.com** — 실사용 가능한 상태다. 터널·`adb reverse` 없이 인터넷에서 붙는다.

| | |
|---|---|
| 서버 | Vultr 서울, `64.176.231.126`, Ubuntu 24.04, 1vCPU/2GB |
| 접속 | `ssh -i ~/.ssh/bandule_deploy root@64.176.231.126` |
| 서버 운영 명령 | `bandule ps` / `logs` / `errors` / `health` / `backup` / `db` — `bandule` 만 쳐도 사용법이 나온다 |
| 배포 | **`main` 에 머지하면 자동.** CI 통과 → 이미지 빌드 → GHCR → SSH → 교체 → 바깥 주소 200 확인 |
| 롤백 | `ssh ... 'cd /opt/bandapp && sh deploy/deploy.sh sha-<이전>'` |
| HTTPS | Let's Encrypt 자동 갱신. Cloudflare 주황 구름 ON (실제 접속자 IP 복원 확인) |
| 백업 | 매일 03:30 KST → R2 `s3://bandule-prod/db-backups/`, 7개 보관 |
| 감시 | UptimeRobot 등록됨 |

**설정·비밀값은 git 에 없다.** 로컬 `.env.prod` 가 정본이고, 고치면 올려야 한다:

> ### ⚠️ 올리기 전에 **반드시 서버 것과 비교한다** (2026-09-06 사고)
>
> 로컬이 정본이라는 말을 믿고 그냥 덮어썼다가 **운영 서버를 5분 죽였다.** 두 파일이
> 갈라져 있었고, 그중 `FCM_CREDENTIALS_HOST_PATH` 가 서버에 없는 경로를 가리켰다.
> Docker 는 없는 경로를 **빈 디렉터리로 만들어** 마운트하고, 앱은 그 디렉터리를 파일로
> 읽으려다 기동에 실패한다 (`FileNotFoundException: ... (Is a directory)`).
>
> ```bash
> ssh -i ~/.ssh/bandule_deploy root@64.176.231.126 'cat /opt/bandapp/.env.prod' > /tmp/server.env
> diff /tmp/server.env .env.prod
> ```
>
> 다른 줄이 있으면 **어느 쪽이 맞는지 먼저 판단하고** 올린다.


```bash
cd C:\band\bandApp && scp -i ~/.ssh/bandule_deploy .env.prod root@64.176.231.126:/opt/bandapp/.env.prod && ssh -i ~/.ssh/bandule_deploy root@64.176.231.126 'cd /opt/bandapp && chmod 600 .env.prod && bandule restart'
```

> **FCM 키 파일은 `chown 999:999`** 여야 한다. root 소유면 앱이 아예 안 뜬다(앱은 uid 999).
> 운영은 `secrets/fcm-prod.json` (project `bandule-b94d2`) 를 쓴다 — `.env.prod` 의
> `FCM_CREDENTIALS_HOST_PATH` 참조. 같은 폴더의 `bandapp-dev-67c6f-...json` 은 안 쓰는 잔재.

> **`bandule restart` 가 설정을 다시 읽지 않던 것도 고쳤다.** `docker compose restart` 는
> 컨테이너만 다시 띄울 뿐 `.env.prod` 를 안 읽는다 — 설정을 고쳐 올리고 restart 해도 옛 값
> 그대로였다(실제로 겪었다). `up -d` 로 바꿨고, **이 수정은 다음 배포 때 서버에 반영된다.**

### 나머지

| | |
|---|---|
| 백엔드 | Phase 0~11 완료. 운영 배포까지 끝 |
| 클라이언트 | 요구 화면 13개 완료. 패키지명 `com.yeka.bandule` |
| 테스터 배포 | [docs/TESTING.md](../TESTING.md) — 서버가 살았으니 APK 만 만들면 된다 |
| 출시까지 순서 | [docs/LAUNCH_CHECKLIST.md](../LAUNCH_CHECKLIST.md) |
| **다른 PC 에서 이어서** | **[docs/NEW_PC_SETUP.md](../NEW_PC_SETUP.md)** — git 에 없는 파일 목록과 확인 절차 |
| 남은 것 | 스토어 심사 제출 · **Phase 12 슬라이스 0**(Play Console 설정, 사람) + **슬라이스 4**(샌드박스 e2e) |
| 끝난 것 | 릴리스 서명 키 · 약관·개인정보 URL(`bandule.com/privacy`,`/terms`) · 카카오 콘솔 패키지명 · 개발자 등록 · 비밀값 로테이션(2026-09-08) · 운영 Firebase 분리(서버 `bandule-b94d2`, 앱 `--flavor prod`) · **Phase 12 슬라이스 1~3 (인앱결제 코드)** |
| 요금 정책 | **밴드당 연 구독 ₩19,000 / 년** 확정 (2026-09-08). `PREMIUM_YEARLY`, 365일. 유료 잠금은 미디어 만료 해제 하나 — 무료는 업로드 30일 뒤 삭제. 가격은 스토어 상품 설정값이라 코드 변경 없음 |

### Phase 12 (인앱결제) — 코드는 됐고 스토어 설정만 남음

슬라이스 1~3 머지 완료 (PR #69·#70·#71). `app.plan.billing.gateway=noop` 이 기본이라 **지금 운영
동작은 안 바뀐다.** 켜려면:

1. **슬라이스 0 (사람)** — Play Console 에서
   - 구독 상품 `premium_yearly`, 기본 요금제 ₩19,000 / 1년
   - Google Cloud 서비스 계정 → Play Console 에서 "재무 데이터 보기" 권한 부여, JSON 키 발급
   - RTDN 용 Pub/Sub 토픽 생성 → Play Console > 수익 창출 설정에 등록, **push 구독**을
     `https://api.bandule.com/api/v1/webhooks/google-play?token=<시크릿>` 로 (인증 서비스 계정 지정 시 OIDC 도 동작)
   - 라이선스 테스터에 본인·테스터 계정 등록
   - 서명된 AAB 를 내부 테스트 트랙에 올림 (검증 API 는 트랙에 올라간 앱에만 동작)
2. **서버 `.env.prod` 에 추가** 후 앱 재기동:
   ```
   PLAN_BILLING_GATEWAY=google
   PLAN_BILLING_GOOGLE_PACKAGE=com.yeka.bandule
   PLAN_BILLING_GOOGLE_CREDENTIALS_PATH=/run/secrets/play-developer-sa.json   # 키 파일 마운트도 함께
   PLAN_BILLING_WEBHOOK_SECRET=<위 ?token= 과 같은 값>
   # (선택) PLAN_BILLING_PUBSUB_AUDIENCE=<push 구독 audience> / PLAN_BILLING_PUBSUB_SA=<서비스계정 이메일>
   ```
   > 서비스 계정 키 파일 없이 `gateway=google` 로 켜면 **기동에 실패한다**(FCM 키와 같은 fail-fast).
3. **슬라이스 4** — 라이선스 테스터 기기로 실제 구매 → PREMIUM 전환 → Play 스토어에서 해지 →
   웹훅으로 `canceled` → 만료 시각 지나면 FREE. 환불(REVOKED)로 즉시 강등도 확인.

**로컬 개발**은 그대로다 — `docker compose up -d` + `adb reverse tcp:8080 tcp:8080`.
실기기 빌드에 **`--dart-define-from-file=dart_defines.json` 을 빠뜨리면 카카오 로그인이 막힌다.**

```powershell
cd C:\band\bandApp; docker compose up -d
& "$env:LOCALAPPDATA\Android\sdk\platform-tools\adb.exe" -s R3CX40J7QJE reverse tcp:8080 tcp:8080
cd C:\band\bandApp\client; flutter run -d R3CX40J7QJE --flavor dev --dart-define-from-file=dart_defines.json
```

> **`--flavor dev` 가 필요하다 (2026-09-06 부터).** 개발용·운영용 Firebase 를 나눠서,
> flavor 없이 빌드하면 Gradle 이 어느 쪽인지 못 골라 멈춘다.
>
> | | Firebase | 언제 |
> |---|---|---|
> | `--flavor dev` | `bandapp-dev-67c6f` | 로컬 개발 |
> | `--flavor prod` | `bandule-b94d2` | 테스터 배포·스토어 (`release_tester.py` 가 자동으로 붙인다) |
>
> 설정 파일은 `client/android/app/src/dev/`·`src/prod/` 에 각각 있고 **커밋되지 않는다** —
> PC 마다 콘솔에서 받아 넣는다. 산출물 이름은 `app-<abi>-<flavor>-release.apk` 다.

---

## 1. 못 끝낸 것

### 1-A. 비밀값 전부 교체 (2026-09-08 유출) — ✅ 완료 (2026-09-08)

**무슨 일이었나** — 서버 `.env.prod` 를 로컬과 비교하려고 `env.prod.server` 로 내려받았는데
`git add -A` 에 휩쓸려 공개 저장소에 커밋·푸시됐다. 강제 푸시로 `main` 에서 뺐지만 노출된
값은 살아 있다고 보고 전부 교체했다.

- 서버 `.env.prod` 에서 `JWT_SECRET` · `DB_PASSWORD` · `REDIS_PASSWORD` ·
  `R2_ACCESS_KEY_ID`/`R2_SECRET_ACCESS_KEY` · `KAKAO_ADMIN_KEY`/`KAKAO_REST_API_KEY` ·
  `MAIL_SMTP_PASSWORD` 교체 완료. 교체 전 스냅샷은 서버 `/opt/bandapp/.env.prod.pre-rotate`.
- 로컬 `.env.prod` 도 서버 값으로 동기화 완료 (2026-09-08).
- 재발 방지: `.githooks/pre-commit` 이 `.env*`·`*.jks`·`google-services.json` 이름을 막는다.
  새 PC 에서 `git config core.hooksPath .githooks` 한 번. `CLAUDE.md` 에 "`git add -A` 금지" 규칙.

> ⚠️ **다시는 `scp 로컬 .env.prod → 서버` 를 무심코 돌리지 말 것.** 서버가 로테이션 정본이다.
> 올릴 일이 생기면 §0 의 diff 를 먼저 본다.

---

### 1-Z. 신고 접수 메일 — ✅ 해결 (2026-09-08)

신고 접수를 푸시 + **메일** 두 경로로 보낸다(`ReportMail`, `REPORT_NOTIFY_EMAILS`, 배포 `f57d304`).
"메일이 안 온다" 로 한참 헤맸는데 두 개가 겹쳐 있었다:

1. **`docker-compose.prod.yml` 이 `MAIL_*`·`REPORT_NOTIFY_*` 를 컨테이너에 안 넘겼다** (PR #68).
   서버 `.env.prod` 엔 3줄이 있는데(`grep -c '^MAIL_' .env.prod` = 3) 컨테이너 안
   `printenv | grep '^MAIL_'` 은 0줄이었다 — compose 는 `environment:` 에 적힌 키만 주입한다.
   `app` 서비스 `environment:` 에 그 5개를 추가해 고쳤고 자동 배포로 반영됐다. `MAIL_FROM` 이
   비면 `EmailSender.isConfigured()` 가 false 라 **신고뿐 아니라 비밀번호 재설정·이메일 인증
   메일도 그동안 안 나갔다.** (`MAIL_SMTP_PASSWORD` 는 2026-09-08 재발급분, §1-A.)
2. 그 뒤에도 "안 온다" 던 것의 실제 원인 — **중복 신고였다.** 테스트 계정 두 개(user 4·5)가
   이미 그 대상을 OPEN 으로 신고해놔서 `REPORT_ALREADY_SUBMITTED`(409)로 되돌아갔고,
   트랜잭션이 `notifyOperatorsAfterCommit` **전에** 터져 행도 메일도 없었다. 앱엔 "이미 접수되어
   처리 중인 신고입니다." 가 떴을 것. 기존 OPEN 을 RESOLVED 로 내리고 다시 신고 → 행 생김.

**메일이 안 왔을 때 순서대로 볼 것**

| 증상 | 뜻 |
|---|---|
| 로그에 `[email] 발신 계정 미설정` | `MAIL_FROM` 이 앱 컨테이너까지 안 갔다 → `up -d app` 로 재기동 (restart 아님) |
| 로그에 `[email] 발송 실패` | SMTP 인증·주소 형식 → 앱 비밀번호, `MAIL_FROM` 의 꺾쇠 |
| 로그에 mail 줄이 아예 없음 | **접수 자체가 안 됐다.** `SELECT id,status FROM reports ORDER BY id DESC LIMIT 5;` — 같은 신고자+대상에 OPEN 이 이미 있으면 409 라 새 행이 안 생긴다 |

**참고**

- `EmailSender.send()` 는 **성공 시 로그를 안 남긴다.** mail 줄이 없다고 실패한 게 아니다.
- `MAIL_FROM` 은 `밴듈 <주소@gmail.com>` 처럼 **꺾쇠 필수.** 없으면 `AddressException`.
- 앱 비밀번호는 2026-09-07 노출 → 2026-09-08 재발급·반영 완료(§1-A).
- 메일 본문·조회 쿼리는 `ReportMail`, `ReportMailTest` 가 지킨다.
- 운영자가 신고를 받고 할 수 있는 일은 [docs/MODERATION.md](../MODERATION.md) — 앱으로 할 수
  있는 건 없고 밴드장 연락 또는 DB 직접.

---

### 1-Y. 지금 당장 걸려 있는 것 (2026-09-06 저녁, 2026-09-08 갱신)

| | 누가 | 안 하면 |
|---|---|---|
| ~~카카오 콘솔에 새 키 해시 등록~~ | ✅ 완료 | |
| ~~기존 앱 지우고 재설치~~ | ✅ 완료 | 서명이 바뀐 뒤로는 그냥 업데이트된다 |
| ~~Cloudflare Pages 연결~~ | ✅ 완료 (2026-09-08) | `bandule.com` · `/privacy/` · `/terms/` 라이브(200). `site/` 폴더가 그대로 게시된다 |
| ~~ProGuard 켜기~~ | ✅ 완료(코드) | `client/android/app/build.gradle.kts` 의 release 에 `isMinifyEnabled`/`isShrinkResources` = true, `proguard-rules.pro` 에 카카오 keep. **실기기 릴리스 빌드로 지도·로그인·푸시 재확인은 아직 — 스토어 제출 빌드 뽑을 때 같이** |
| ~~Gmail 앱 비밀번호 발급~~ | ✅ 완료 (2026-09-08) | 재발급분이 서버·로컬 `.env.prod` 에 반영됨(§1-A). 신고·재설정·인증 메일 나간다 |

**릴리스 서명 키가 생겼다 (2026-09-06).** `client/android/bandule-release.jks`, 인증서
`CN=yeka, L=seoul`. `android/key.properties` 가 있으면 그 키로 서명하고 없으면 디버그 키로
넘어간다. **키와 비밀번호를 잃어버리면 그 앱은 영원히 업데이트할 수 없다** — 백업 필수.
카카오 키 해시·딥링크 지문 값은 [LAUNCH_CHECKLIST 9단계](../LAUNCH_CHECKLIST.md).

**앱 내 업데이트 알림은 시도했다가 접었다.** Firebase App Distribution 의 인앱 알림 SDK 는
**테스터가 구글 계정으로 로그인해야** 동작하는데, 등록된 테스터 둘이 `naver.com`·`hanmail.net`
이라 쓸 수 없었다. 플러그인도 개인이 만든 것이고 그 아래 SDK 는 베타다. 코드는 걷어냈고
(`revert(client): 앱 내 업데이트 안내를 걷어낸다`), 정식 출시하면 Play 스토어가 자동
업데이트를 대신한다. **다시 살릴 거라면 테스터에게 구글 계정을 받아야 한다.**

**신고 알림이 붙었다.** `.env.prod` 의 `REPORT_NOTIFY_USER_IDS=4,5` (지시자의 이메일·카카오
계정). 받은 뒤 무엇을 할 수 있는지는 [docs/MODERATION.md](../MODERATION.md) — 요약하면
**운영자가 앱으로 할 수 있는 일은 없고**(남의 밴드 글은 못 지운다, 계정 정지 기능이 없다),
밴드장에게 연락하거나 DB 에서 직접 손대는 두 길뿐이다.


### 1-Z. 초대 링크 ✅ **고쳤다 (2026-09-06)** — 확인만 남았다

지시자 제보로 드러난 문제였다. 랜딩 페이지가 `bandapp://invite/{code}` 로 앱을 불렀는데
**`bandapp://` 는 네이버 밴드가 쓰는 주소**라 그 앱이 열렸고, 게다가 우리 앱에는 링크를
받는 설정도 처리 코드도 없어서 **스킴이 겹치지 않았어도 안 열렸다.**

고친 것:

- 스킴 `bandapp` → `bandule` (`application.yml`, `DeeplinkProperties`)
- `AndroidManifest.xml` 에 `bandule://invite` 를 받는 `intent-filter`
- `app_links` 로 받아 합류 화면(`/band-gate/join?code=...`)으로 넘긴다
  (`core/deeplink/invite_link_handler.dart`)
- 같이 발견해 고친 것 — `DeeplinkProperties` 의 패키지명 기본값이 `com.yeka.bandapp`
  으로 남아 있었다(오늘 `com.yeka.bandule` 로 바꾼 것이 안 따라왔다)

**서버 쪽은 끝났다 (2026-09-06).** 배포 + `.env.prod` 의 `DEEPLINK_SCHEME=bandule` 반영까지
확인했다:

```bash
curl -s https://api.bandule.com/invite/<코드> | grep -o "band[a-z]*://invite/"   # bandule://invite/
```

**앱 쪽 실기기 확인이 남았다.** 밴드 설정 > 멤버 초대에서 링크를 만들어 카톡 등으로 자신에게
보낸 뒤 눌러 본다. **밴듈이 열려 합류 화면에 코드가 채워져 있어야 한다.**
링크 처리 코드는 `0.1.0+15` 이후 빌드에만 있다 — 그 전 빌드로는 확인되지 않는다.

> 브라우저 주소창에서 앱이 **바로** 열리는 App Links 는 아직이다. 릴리스 서명 키의 지문을
> `assetlinks.json` 에 올려야 해서 스토어 등록 뒤에 붙인다
> (`app.deeplink.android-sha256-cert-fingerprints`). 지금은 랜딩 페이지를 한 번 거친다.

> **서버에 `DEEPLINK_SCHEME` 이 박혀 있으면 그것도 바꿔야 한다.** `.env.prod` 를 확인하고,
> 있으면 `bandule` 로 고쳐 올린다(§0 의 scp 명령).

### 1-A. "BOTTOM OVERFLOWED BY 63 PIXELS" — ✅ 닫음 (2026-09-08, 지시자 판단)

끝내 재현하지 못했다. 어느 화면인지 특정 못 했고, 기능 영향 없음(디버그 빌드에서만 노란
줄무늬, 릴리스에선 안 보임). 지시자가 "처리된 걸로" 판단해 닫는다. 다시 눈에 띄면 앱을 켜 두고:

```powershell
& "$env:LOCALAPPDATA\Android\sdk\platform-tools\adb.exe" -s R3CX40J7QJE logcat -c
& "$env:LOCALAPPDATA\Android\sdk\platform-tools\adb.exe" -s R3CX40J7QJE logcat | Select-String -Pattern "overflowed|RenderFlex"
```

### 1-B. 실기기 end-to-end — ✅ 닫음 (2026-09-08, 지시자 판단)

백엔드 21개 항목은 로컬 스택에 실제 요청을 넣어 확인했다(`[API]` 표시). 화면으로만 확인
가능한 나머지(압축 진행률 표시, 사진 축소 크기, 쿠폰 입력 화면, R2 객체 삭제, 영상 재생,
합주실 폼 지도 핀)는 지시자가 "처리된 걸로" 판단해 닫는다. 스토어 제출 릴리스 빌드를
실기기에서 돌릴 때 지도·로그인·푸시와 함께 눈으로 훑으면 된다.

### 1-D. 기기 푸시 — 살렸다 (2026-09-06)

그동안 **기기 푸시가 한 통도 오지 않았다.** 백엔드는 Phase 9 에서 끝났는데 안드로이드에서
Firebase 를 초기화해 줄 `com.google.gms.google-services` 그래들 플러그인이 빠져 있었다.
`PushService` 가 "설정 없으면 조용히 비활성화" 로 짜여 있어 앱은 멀쩡히 돌았고, 그래서
아무도 눈치채지 못했다.

전 구간 실증(로컬 백엔드 + 갤럭시 S24):

| | |
|---|---|
| 서버 | `FCM 푸시 발송 활성화 projectId=bandapp-dev-67c6f` |
| 기기 | `FirebaseApp initialization successful` |
| 토큰 | `device_tokens` 에 `user=21 ANDROID` 등록 |
| 도착 | 알림창에 **"새 합주 일정 / 11월 11일 20:00 합주 일정이 등록됐어요."** |

같이 고친 버그 — **푸시를 받아도 홈 종 배지가 안 올랐다.** 알림 목록 프로바이더가
autoDispose 가 아니라 캐시된 옛 값을 계속 그렸고, 앱을 완전히 껐다 켜야만 반영됐다(§4 의
그 패턴). `push_service.dart` 가 (1) 포그라운드 푸시 수신 시 (2) 앱이 다시 활성화될 때
(`AppLifecycleListener.onResume` — 백그라운드에서 받은 경우가 이쪽이고 더 흔하다)
알림 목록 캐시를 비우도록 했다. 기기에서 `배지 0 → 백그라운드 → 푸시 → 복귀 → 배지 1` 확인.

**운영 Firebase 분리 완료 (2026-09-06 확인 2026-09-08).** 서버는 `secrets/fcm-prod.json` →
`FCM 푸시 발송 활성화 projectId=bandule-b94d2`, 앱은 `--flavor prod` → `bandule-b94d2`.
개발용 `bandapp-dev-67c6f` 는 로컬(`--flavor dev`)만.

**남은 것**: 알림 채널이 FCM 기본값(`fcm_fallback_notification_channel`)이라 설정 화면에
"기타" 로 보인다 — 앱에서 채널을 만들어 이름을 주면 좋다(출시 전 다듬기).

### 1-E. 게시판·지도 후속 (2026-09-06, 지시자 제보로 발견)

**영상 압축이 33% 에서 멈춘다.** 실기기에서 재현·규명했다. `video_compress` 가 쓰는
`otaliastudios/transcoder` 안에서 나는 교착이다 — 오디오 디코더가 출력 버퍼 6개를 다 쥔 채
비우지 못해 리더가 멈추고, 그 상태로 `advanced=false` 를 초당 85번 반복하며 CPU 만 태운다.
(입력: HEVC/`hvc1` · 5분 43초 · 131MB. 출력 파일이 4분간 1바이트도 안 늘었다.)

라이브러리 안쪽이라 직접 못 고친다. 대신 **우리 쪽 방어**를 넣었다 — 진행률이 90초 동안
안 바뀌면 압축을 취소하고 원본으로 진행한다(`_compressStall`). 안내 문구도 띄운다.
**근본 해결은 압축 라이브러리 교체이고, 지시자와 "나중에 검토" 로 합의했다.**

**업로드 중 아무 표시가 없었다.** 글을 등록하면 첨부가 올라가는 동안 몇 분씩 정지한 것처럼
보였다. 저장소 PUT 에 `onSendProgress` 를 뚫고 화면에 "첨부 올리는 중 n/m · xx%" 와 막대를
띄운다. 스트림 업로드라 dio 가 전체 크기를 모르므로(`total=-1`) 우리가 아는 크기를 넘긴다.
"화면을 벗어나지 마세요" 안내도 함께 — 글은 먼저 저장되고 첨부가 나중에 올라가는 구조라
중간에 나가면 첨부만 유실된다.

**합주실을 추가해도 지도에 핀이 안 생겼다.** 마커를 `onMapReady` 에서만 그렸는데 이 콜백은
지도 위젯이 처음 만들어질 때 한 번만 불린다. 목록이 갱신돼도 다시 안 그려졌다(앱을 껐다
켜야 보였다). 목록 지문이 바뀌면 핀을 지우고 다시 그리도록 했다. **알림 배지와 같은 계열**이다.

**장소 검색을 버튼 방식으로 바꿨다.** 타이핑이 멎을 때마다 350ms 뒤 자동 검색해서 등록 한 번에
카카오 로컬 API 를 5~10회씩 태웠고, 결과가 뜬 자리를 키보드가 가려 어차피 한 번 내려야 했다.
이제 입력창 끝의 돋보기(또는 키보드 검색키)를 눌러야 한 번 호출하고, 키보드를 내리고,
결과 목록까지 스크롤한다. 기기 확인: 타이핑 4초 뒤에도 호출 없음 → 버튼 누르니 5건 표시.

**남은 개선(안 함)**: 지도가 첫 합주실 위치에 고정돼 있어 멀리 있는 핀은 화면 밖이다.
모든 핀이 들어오도록 맞추면 좋다.

### 1-C. 카카오 로그인 keyHash

**2026-09-06 — 실기기에서 카카오 로그인 성공을 확인했다.** 패키지명을 `com.yeka.bandule` 로
바꾼 뒤에도 콘솔을 손대지 않고 그대로 됐다. Redis 리프레시 세션 TTL 이 14일 만료에서
94초 지난 값이라, 버튼을 누른 그 시각에 세션이 새로 생긴 것이 확인된다.

> 한동안 "앱 키 미설정" 으로 막혔던 적이 있는데, 그건 APK 를 빌드할 때
> `--dart-define-from-file=dart_defines.json` 을 빠뜨려 앱 안에 키가 안 들어간 것이었다.
> **실기기 빌드는 반드시 이 옵션을 붙인다.**

키 해시는 서명 키에서 나오는 값이라 패키지명과 무관하다. 세 곳에서 계산해 모두 같았다
(디버그 키스토어 / 설치된 APK 서명 / 아래 기록값):

```
패키지명   com.yeka.bandule            ← 2026-09-06 에 com.example.bandapp_client 에서 바꿨다
키 해시    ahCJ5a5dXyiPh3x9ksny6yMbjzk=
```

> **패키지명을 바꿨으니 카카오 콘솔의 Android 플랫폼 등록을 반드시 새로 해야 한다.**
> 개발자 콘솔 > 내 애플리케이션 > 앱 설정 > 플랫폼 > Android 에서 패키지명을
> `com.yeka.bandule` 로 고치고 저장한다. 키 해시는 **서명 키에서 나오는 값이라 그대로**다
> (같은 debug.keystore 를 쓰는 한 바뀌지 않는다). 이걸 안 하면 카카오 로그인이
> `KakaoTalk not installed` 가 아니라 플랫폼 불일치로 막힌다.
> 네이버 지도(NCP)에도 패키지명이 등록돼 있으면 그쪽도 같이 고친다.

키 해시는 폰에 설치된 APK 의 서명 인증서에서 직접 뽑아 대조한 값이라 확실하다. 그래도 거부되면
**앱이 스스로 찍는 값**을 쓴다 — 디버그 빌드는 시작할 때 로그에
`kakao keyHash (콘솔에 등록할 값): ...` 을 남긴다. 콘솔에서 확인할 것:
① 지금 보고 있는 앱의 네이티브 키가 `dart_defines.json` 의 것과 같은지,
② 입력 후 **저장 버튼**을 눌렀는지.

---

## 2. 우선순위가 높은 남은 작업

### 2-A. 출시 전 필수

- ~~**패키지명이 `com.example.bandapp_client`**~~ **완료 (2026-09-06)** — `com.yeka.bandule` 로
  바꿨다(`namespace`·`applicationId`·`MainActivity.kt` 패키지·디렉터리, 백엔드 딥링크 기본값).
  **남은 사람 작업: 카카오 콘솔(+ 쓰고 있다면 NCP)의 Android 플랫폼 패키지명을 새 값으로 고칠 것** — §1-C.
- ~~**`client/.gitignore` 가 `/android/` 를 통째로 무시한다**~~ **완료 (2026-09-06)** —
  안드로이드 프로젝트를 추적으로 돌렸다(27개 파일). 그전까지는 빌드 설정·매니페스트·
  카카오 스킴·아이콘·패키지명이 전부 이 PC 에만 있었다. 비밀·기계별 파일은
  `client/android/.gitignore`(flutter create 산출물)와 루트 `.gitignore` 가 이미 막는다 —
  `local.properties`(카카오 키), 키스토어, `*.iml`, 빌드 산출물, `GeneratedPluginRegistrant.java`.
  `/ios/`·`/web/`·`/windows/` 는 아직 `flutter create` 기본값 그대로라 계속 무시한다.
  손댈 일이 생기면 그때 푼다.
- ~~**릴리스 서명 설정 없음**~~ **완료 (2026-09-06)** — `client/android/bandule-release.jks`
  로 서명한다. `android/key.properties` 가 없으면 디버그 키로 넘어가므로 키 없는 PC 에서도
  빌드는 된다. 카카오 콘솔에 새 키 해시(`7zGOncUg+QW8Yt2dmsgmgmI5TPQ=`)도 등록했다.
- ~~**ProGuard 가 꺼져 있다**~~ **켜져 있다 (코드 확인 2026-09-08)** —
  `client/android/app/build.gradle.kts` 의 `buildTypes.release` 에 `isMinifyEnabled = true`,
  `isShrinkResources = true`, `proguardFiles(... "proguard-rules.pro")`. 카카오 keep 규칙도 있다.
  **남은 것: 스토어 제출용 릴리스 빌드를 실기기에서 돌려 지도·로그인·푸시 확인**
  (난독화가 SDK 리플렉션을 깨는 일이 흔하다). 이건 제출 빌드 뽑을 때 한 번.

---

## 3. 기능으로 남은 것

[client-SCREENS.md](client-SCREENS.md) §4 "알려진 제약" 표가 최신이다. 요약하면:

| 항목 | 상태 |
|---|---|
| 미납 독촉 알림 | 백엔드 API 없음 — 만들지 결정 필요 |
| 홈 '이번 달 정산' 카드 | 밴드 집계 API 없어 값이 `—`. 지금은 정산 탭으로 보낸다 |
| 알림 딥링크 | 알림을 눌러도 해당 화면으로 안 간다. push data 에 `bandId`·`reservationId` 는 이미 실려 온다 |
| 정기 일정 규칙 상세/수정 | 등록·목록·삭제만 있다 |
| 캘린더 주간 뷰 | 월간만 |
| 셋리스트 완료 체크 | 추가·수정·삭제·재정렬만 |
| ~~네이버 로그인~~ | **버튼을 뺐다 (2026-09-06).** 눌리지 않는 버튼이 앱을 덜 된 것처럼 보이게 해서. 실제로 붙일 때 다시 넣는다 |
| 밴드 장르·파트 | "추후 지원 예정" 안내문만 |
| ~~약관 동의 기록~~ | **완료 (2026-09-06).** 가입 시점에 `terms_agreements` 에 남긴다(누가·언제·어느 시행일 버전에). 재동의 화면은 아직 없다 — 약관을 고쳐 새로 게시하면 `app.terms.version` 을 올리고, 기존 회원 재동의가 필요해지면 그때 만든다 |

---

## 4. 작업할 때 챙길 것

```bash
cd client && python tools/check_cache_invalidation.py   # 저장 후 캐시 무효화 누락 검사
cd client && flutter test
cd client && flutter analyze --no-fatal-warnings --no-fatal-infos   # CI 와 같은 명령
cd C:\band\bandApp && ./gradlew test
```

- 저장(쓰기)을 하는 화면을 만들면 **반드시 관련 프로바이더를 `ref.invalidate`** 한다.
  이 앱의 provider 는 autoDispose 가 아니라, 안 비우면 화면을 벗어났다 오는 순간
  옛 값이 다시 그려진다(오늘 4곳에서 났다). 위 검사 스크립트가 잡아 준다.
- **`flutter analyze` 는 CI 와 똑같은 옵션으로 돌린다.** 결과를 `grep` 으로 거르지 말 것 —
  분석기는 `error` 를 7칸 우측정렬(공백 **2**칸)로, `info` 는 공백 3칸으로 찍는다.
  공백 3칸으로 error 를 찾다가 문법 오류를 놓치고 CI 를 깨뜨린 적이 있다.
- **레이트리밋을 검증하는 테스트는 직접 루프를 돌리지 말고 `RateLimitAssertions.assertRateLimited`
  를 쓴다.** `RedisRateLimiter` 가 1분 고정 윈도우라, 상한보다 조금만 많이 던지는 루프는
  분 경계를 넘는 순간 카운터가 리셋돼 429 가 한 번도 안 난다. 헬퍼가 상한의 2배+2회를
  던져 산수로 막아 준다(`N > 2×상한` 이면 어떻게 갈려도 한쪽이 상한을 넘는다).
  이걸로 `Report`·`MediaUpload`·`AuthRateLimit` 세 테스트가 차례로 깨졌다.
- 아이콘을 바꾸려면 `client/brand/*.svg` 를 고치고
  `cd client && python tools/render_icons.py`.
