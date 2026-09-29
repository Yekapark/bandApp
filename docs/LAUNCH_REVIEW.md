# 출시 전 점검 — 남은 일과 진행 상태

> **이 문서가 스토어 출시 작업의 정본이다.** 2026-09-27 점검에서 나온 문제를 모두 담고,
> 각 항목의 **상태** 칸이 지금 어디까지 왔는지를 말한다.
> 배경 문서: [LAUNCH_CHECKLIST.md](LAUNCH_CHECKLIST.md)(출시 순서), [legal/play-app-content.md](legal/play-app-content.md)(콘솔 선언 답안),
> [progress/review-2026-09-1*](progress/README.md)(영역별 점검 1~3차).
> 마지막 갱신: **2026-09-27** (G1 완료)

---

## 작업 규칙 — 이 문서로 일하는 법

1. **작업을 시작하기 전에 이 문서를 읽는다.** "상태" 칸에서 ✅ 가 아닌 항목 중 §1 의 처리 순서가
   가장 앞선 것을 고른다. 사용자가 다른 항목을 지정하면 그것을 한다.
2. 항목을 처리했으면 **그 자리에서 상태를 바꾼다.** 날짜와 근거(바꾼 파일·커밋·PR·확인 결과)를
   함께 적는다. 코드만 고치고 사람 확인이 남았으면 ✅ 가 아니라 🟡 로 두고 **남은 것**을 적는다.
3. 표의 문제 설명은 지우지 않는다 — 끝난 항목도 무엇이 문제였는지 남겨 둔다.
4. 처리하다 새 문제를 찾으면 해당 영역 표 끝에 새 ID 로 추가하고 §1 순서에도 넣는다.
5. 문제를 고치면 [TROUBLESHOOTING.md](TROUBLESHOOTING.md) 맨 위 기록도 남긴다(CLAUDE.md 규칙).

**상태 표기**

| 표기 | 뜻 |
|---|---|
| ✅ | 끝. 확인까지 마쳤다 |
| 🟡 | 코드·문서는 끝났고 **사람 확인·콘솔 반영이 남았다** (남은 것을 적는다) |
| 🔧 | 진행 중 |
| ⬜ | 아직 안 했다 |
| ➖ | 하지 않기로 했다 (이유를 적는다) |

**심각도**: **차단** = 제출·심사가 막히거나 출시 직후 핵심 기능이 안 된다 · **높음** = 돈·개인정보·데이터
손실 · **중간** = 오해·불편 · **낮음** = 다듬기.

---

## 1. 처리 순서와 현황

| 순서 | ID | 심각도 | 한 줄 | 상태 |
|---|---|---|---|---|
| 1 | P1 | 차단 | 비공개 테스트 12명·14일 (개인 계정 확인됨) | 🔧 2026-09-27 트랙 "비공개2차"(0.1.0+30, 테스터 목록 "밴듈") 검토 제출. 다음: 승인 뒤 테스터 12명+ 옵트인 → 14일 → 프로덕션 신청 |
| 2 | P2 | 차단 | Play 앱 서명 키의 카카오 키 해시 등록 | 🟡 2026-09-27 카카오 콘솔에 현재·이전 앱 서명 키 해시 등록 완료 · Play 설치본으로 로그인·지도 확인 남음 |
| 3 | P3 | 차단 | 스토어 설명이 유료 기능을 무료라고 적음 | ✅ 2026-09-27 콘솔 스토어 등록정보 교체(1435자) 후 검토 제출 |
| 4 | P5 | 차단 | targetSdk 36 | ✅ 2026-09-27 AAB 0.1.0+30 에서 36 확인 |
| 5 | P6 | 차단 | 16KB 페이지 크기 | ✅ 2026-09-27 AAB 0.1.0+30 의 64비트 .so 9개 전부 16KB 정렬 |
| 6 | G1 | 높음 | 로컬 미커밋 수정 커밋·배포, main 과 합치기 | ✅ 2026-09-27 PR #98 squash 머지(`10e5adc`), CI·배포 성공, 운영 health UP |
| 7 | P4 | 높음 | 구독 화면 사전 고지·비교표 | ✅ 2026-09-28 실기기(0.1.0+31)에서 요금제 화면 고지·비교표 확인. "구독 관리" 버튼은 PREMIUM 일 때만 보여 §6 구매 테스트 때 함께 본다 |
| 8 | B1 | 높음 | 계정 보류·만료 뒤 복구돼도 PREMIUM 안 돌아옴 | 🟡 2026-09-28 PR #102 머지(`3ae1590`), CI·배포 성공, 운영 health UP. 실결제 시나리오(§6 보류→복구)는 사람 확인 |
| 9 | B2 | 높음 | 앱 시작 시 미완료 구매 조회 | 🟡 2026-09-28 B3 와 한 PR(#103) 머지(`1e21219`)·배포, health UP. 앱 전역 복구 + 복구 API. 다음 스토어 빌드(+32)에서 실확인 남음 |
| 10 | B3 | 높음 | 구매를 밴드에 묶기 (obfuscatedAccountId) | 🟡 2026-09-28 B2 와 한 PR(#103) 머지·배포. `band-{id}` 표시 + 서버 대조. +32 에서 실결제 확인 남음 |
| 11 | B5 | 높음 | 밴드 삭제·탈퇴 시 구독 경고·차단 | 🟡 2026-09-28 서버 삭제 차단(409) + 앱 삭제·위임·탈퇴 안내. +32 에서 실기기 확인 남음 |
| 12 | B4 | 높음 | 한 계정이 여러 밴드를 결제할 수 있게 (같은 값의 상품 5개) | 🟡 2026-09-28 코드 완료(#105)·배포. Play Console 에 `premium_yearly_2`~`_5` 생성·활성화 완료. +32 에서 밴드 2개 결제 확인 남음 |
| 13 | U1 | 높음 | 로그아웃 후에도 푸시가 옴 | 🟡 2026-09-28 서버·앱 수정. +32 실기기에서 로그아웃 뒤 푸시 안 오는지 확인 남음 |
| 14 | L1 | 높음 | 개인정보처리방침 위탁·국외이전 보완 | ✅ 2026-09-28 Vultr·Resend·DB 백업 반영, 메일 업체 서버 확인(Resend) |
| 15 | U3 | 중간 | 스토어 빌드 스크립트 | ✅ 2026-09-27 `release_store.py` 첫 실행 통과 |
| 16 | B6 | 중간 | 자동 갱신 구독자에게 만료 경고 | ✅ 2026-09-28 서버 예고·앱 배너 모두 자동 갱신 구독 제외(해지 예약·쿠폰만) |
| 17 | B8 | 중간 | 만료 배치가 스토어 재조회 없이 강등 | ✅ 2026-09-28 강등 전 스토어 재확인 — 유효하면 연장, 장애면 3일까지 미룸 |
| 18 | B7 | 중간 | 쿠폰·결제 기간 섞임 | 🟡 2026-09-28 쌓기로 구현(스토어 결제일 연기). 서비스 계정 "주문 및 구독 관리" 권한 있음(사용자 확인 2026-09-29). 실결제 확인 남음 |
| 19 | U2 | 중간 | 미디어 URL 10분 만료 + 상세 캐시 | 🟡 2026-09-28 실패 시 새 주소로 재조회·1회 재시도, 상세 autoDispose. 실기기 확인 남음 |
| 20 | P7·P8 | 중간 | UGC 운영 수단·스토어 카테고리 | ✅ P8: 카테고리 "도구"(2026-09-27). P7: 2026-09-28 계정 정지·글 숨김 운영 스크립트 + 약관 제14조(시행 10-06), 2026-09-29 사용자가 운영 서버에서 `moderate.py reports` 실행 확인 |
| 21 | L2·L3 | 중간 | 연락처 통일, 판매자 법적 요건 확인 | 🟡 2026-09-29 L3: 신고번호 Play 입력(사용자), 약관 환불 문구 완화, 판매자 정보를 사이트 모든 쪽 하단·앱 요금제 화면에 표시. L2 ✅: `notice@bandule.com` 통일·수신 확인·Play Console 연락처·설명 교체(사용자, 2026-09-29). 남은 것: L3 요금제 화면 판매자 정보는 +32 빌드에서 확인 |
| 22 | U7 | 중간 | 카카오 SDK·video_compress 가 KGP 를 써서 향후 Flutter 에서 빌드 실패 예고 | 🟡 2026-09-29 카카오 SDK 2.0.1·영상 압축 v_video_compressor 로 교체(사용자 승인), CI 에 릴리스 APK 빌드 추가. 남은 것: +32 에서 카카오 로그인·영상 압축 확인, kakao_map_sdk 가 KGP 를 버리면 builtInKotlin=true |
| 23 | P11 | 낮음 | Android 16 큰 화면(태블릿·폴더블) 레이아웃 확인 | 🟡 2026-09-29 큰 화면에서 앱을 가운데 720dp 기둥으로 묶음(`ReadableWidth`). 태블릿·폴더블(또는 폰 가로) 눈 확인 남음(§7) |
| 24 | G2 | 낮음 | main 에 안 합쳐진 옛 원격 브랜치 11개 정리, dependabot(AWS SDK BOM) PR 처리 | 🟡 2026-09-29 dependabot #119 머지·배포(health UP). 남은 브랜치 2개의 커밋은 살려서 main 에 합침(사용자 위임). 남은 것: 브랜치 삭제 스크립트 실행(사용자, 다음에) |
| 25 | 나머지 | 낮음 | P9·P10, B9~B12, U4~U6, L4·L5·L6 | 🔧 2026-09-29 L4·L5·L6·P9·U4 ✅, B9·U5 ➖(사용자 결정), B11·B12·P10 🟡. 남은 것: B10(Pub/Sub 인증 — 콘솔 작업), U6 일부(출시 후) |

P1(14일)이 가장 오래 걸리므로 먼저 시작하고, 그 기간에 코드 항목(G1~U1)을 고쳐 같은 트랙에 올린다.

---

## 2. Git·배포 상태

| ID | 심각도 | 문제 | 해결방안 | 상태 |
|---|---|---|---|---|
| G1 | 높음 | 점검 1~3차 수정(약 40개 파일 — 인증 토큰 갱신 경합·한글 비밀번호 72바이트, 밴드 위임·탈퇴 경합·초대코드 2개·빈 밴드 재참여, 반복 회차 원래 슬롯·셋리스트 덮어쓰기, `V19`)이 로컬에만 있어 운영에 없다. 로컬 HEAD `5a84f31` 은 원격 main `982e528` 보다 4커밋 뒤(#94 **DB 백업 8일 멈춤 수정** 포함). main 에 안 합쳐진 원격 브랜치 12개 중 11개는 이미 반영된 옛 브랜치, dependabot(AWS SDK BOM) 하나만 새 내용 | 브랜치 → 파일 이름을 적어 커밋(`git add -A` 금지) → `git pull`(`TROUBLESHOOTING.md` 충돌은 양쪽 항목 모두 살림) → 전체 테스트 → PR → 자동 배포. **V19 는 되돌릴 수 없다** — 배포 직전 `bandule backup`, 롤백은 이미지가 아니라 DB 복원까지. 옛 브랜치 삭제, dependabot PR 은 CI 통과 시 머지 | ✅ 2026-09-27 커밋 54개 파일(수정 39·신규 15)을 PR [#98](https://github.com/Yekapark/bandApp/pull/98) 로 squash 머지 `10e5adc`. CI(rules·build·analyze-test) 통과 → Deploy 성공(배포 전 백업은 `deploy.sh` 가 수행) → `https://api.bandule.com/actuator/health` = UP. **V19 운영 적용됨.** 옛 브랜치·dependabot 은 G2 로 분리 |
| G2 | 낮음 | 이미 main 에 더 새 버전이 들어간 옛 원격 브랜치 11개(`chore/drop-unused-plan-code` 등)가 남아 있다. dependabot `gradle-minor-patch`(AWS SDK BOM) PR 이 열려 있다 | 옛 브랜치는 삭제, dependabot 은 CI 통과 확인 후 머지 | 🟡 2026-09-29 — ① dependabot #119(AWS SDK BOM 2.54.17→2.55.6, firebase-admin 9.10.0→9.11.0) CI 통과 확인 후 squash 머지 `d9b722e` → 배포 성공, health UP. ② 원격 브랜치 58개(main 제외)를 PR 기록과 대조: **55개는 삭제해도 되는 것**(머지된 PR 의 마지막 커밋과 같음 51 + main 보다 앞선 커밋 없음 4). 이 세션의 GitHub 토큰으로는 브랜치 삭제가 403 이라 `git push origin --delete` 스크립트를 사용자에게 전달. ③ **머지 안 된 커밋이 남은 2개**는 사용자가 판단을 맡겨 **살렸다**: `feat/notification-channel` 의 참석·납부 체크 먼저 칠하기(정산은 화면 전체 잠금도 풀림)를 지금 코드에 옮기고, "Cloudflare 프록시를 껐다 — LA 경유 700ms" 기록은 `DEPLOY.md`·`LAUNCH_CHECKLIST.md`("왜 되돌렸는지 아무도 모른다" 해소)·`NEXT.md` 에, `phase-11-deploy` 의 서버 선택 기준(서울 리전·Ubuntu 24.04·NVMe)은 `DEPLOY.md` §1 에. 이제 두 브랜치도 지워도 된다. **남은 것: 스크립트 실행(두 브랜치 포함), (선택) GitHub Settings › General › "Automatically delete head branches" 켜기 — 앞으로 머지하면 브랜치가 저절로 지워진다** |

---

## 3. Google Play 정책·제출 요건

이미 된 것: 앱 안·웹 계정 삭제 경로, 신고·차단, 위치 권한 제거, 광고 ID 없음, Play Billing Library 8
(`in_app_purchase_android` 0.5.3), 방침·약관의 결제 조항.

| ID | 심각도 | 문제 | 해결방안 | 상태 |
|---|---|---|---|---|
| P1 | 차단 | 2023-11 이후 만든 **개인** 개발자 계정은 비공개 테스트에 12명 이상이 14일 연속 참여해야 프로덕션을 신청할 수 있다. 기록상 내부 테스트 트랙만 썼고 테스터는 2명(naver·hanmail 계정) | Play Console › 설정 › 개발자 계정에서 유형 확인(조직이면 해당 없음). 개인이면 비공개 테스트 트랙 생성, **Gmail 계정** 테스터 12명 이상 등록, AAB 업로드, 참여 링크 배포. 14일 뒤 프로덕션 액세스 신청 | 🔧 2026-09-27 진행 중 (사람 작업) |
| P2 | 차단 | Play 앱 서명을 쓰면 스토어 설치본은 구글 키로 재서명된다. 카카오 로그인·카카오맵은 키 해시로 앱을 확인하는데, 등록된 것은 디버그 키와 업로드 키(`7zGOnc…`)뿐. `ANDROID_SHA256_CERT_FINGERPRINTS` 에 적힌 `C2:6B:…` 도 업로드 키 값 | Play Console › 앱 무결성 › 앱 서명 › **앱 서명 키 인증서**의 SHA-1 → `python -c "import base64,sys;print(base64.b64encode(bytes.fromhex(sys.argv[1].replace(':',''))).decode())" <SHA-1>` → 카카오 콘솔 Android 키 해시에 **추가**. SHA-256 은 **서버** `.env.prod` 에 직접. 내부 테스트 설치본으로 로그인·지도 확인 | 🟡 2026-09-27 Play Console › 앱 서명("Google Play로 보호됨" 안으로 옮겨짐)에서 **기존 키**와 **이전 앱 서명 키**(2026-09-09) SHA-1 을 키 해시로 바꿔 카카오 콘솔에 추가. **남은 것: 내부 테스트 트랙으로 설치한 앱에서 카카오 로그인·지도 확인.** SHA-256 은 App Links 를 켤 때 서버에 |
| P3 | 차단 | 스토어 설명이 "모든 기능을 무료로", "영상은 앱 안에서 바로 재생", "정기 합주 자동 등록"을 조건 없이 적었다. 영상 업로드·정기 규칙은 서버가 `PLAN_REQUIRED` 로 막는 유료 기능 → 메타데이터 정책(오해의 소지) | 설명문에 "(프리미엄)" 표시, "■ 무료와 프리미엄" 으로 무료 범위·혜택 3가지·자동 갱신·해지 명시 | 🟡 2026-09-27 `docs/store-listing.md` 수정 완료. 2026-09-27 Play Console 기본 스토어 등록정보 "자세한 설명" 교체·저장(1150→1435자, Claude in Chrome). 2026-09-27 게시 개요에서 검토 제출 완료 |
| P4 | 높음 | 구독 제안 화면이 가격·주기만 보여 준다. 자동 갱신·해지 방법은 구매 후 화면에만 있다. 비교표에 "영상 업로드" 행이 없어 무료도 영상을 올릴 수 있는 것처럼 읽힌다 (`plan_screen.dart` `_ActionButton`·`_CompareTable`) | 구매 버튼 위 고지("밴드당 연 ₩19,000 · 매년 자동 갱신 · Play 스토어에서 언제든 해지 · 해지해도 기간 끝까지 이용") + 약관·방침 링크, 비교표에 "영상 업로드" 행, PREMIUM 이면 `https://play.google.com/store/account/subscriptions?sku=premium_yearly&package=com.yeka.bandule` 로 가는 "구독 관리" 버튼 | 🟡 2026-09-27 `plan_screen.dart`: 구매 버튼 위 고지(`_SubscriptionTerms` — 가격·연 자동 갱신·해지 경로·기간 끝까지 유지·밴드 단위/계정당 한 밴드·약관 주소), 비교표에 "영상 업로드 — / 가능" 행. 2026-09-27 `url_launcher` 추가 승인 → PREMIUM·해지 예약 안내에 "Google Play 에서 구독 관리" 버튼(`IapService.manageSubscriptionUrl`, 매니페스트 `<queries>` https VIEW). 0.1.0+31(`pubspec.lock` 포함, #101). ✅ 2026-09-28 사용자가 실기기 요금제 화면에서 고지·비교표 확인. "구독 관리" 버튼은 PREMIUM 상태에서만 나오므로 §6 구매 테스트에서 확인 |
| P5 | 차단 | 2026-08-31 부터 신규 앱·업데이트는 targetSdk 36 필수. `targetSdk = flutter.targetSdkVersion` 이라 빌드한 PC 의 Flutter 에 따라 값이 달라진다 | `build.gradle.kts` 에 `playMinTargetSdk = 36`, `compileSdk`·`targetSdk` = `maxOf(Flutter 기본값, 36)`. Android 16 은 큰 화면에서 방향 고정을 무시하니 태블릿 에뮬레이터로 레이아웃 한 번 확인 | ✅ 2026-09-27 `build.gradle.kts` 수정, `release_store.py` 로 AAB 0.1.0+30 의 targetSdk 36 확인(병합 매니페스트). 태블릿 레이아웃은 P11 로 분리 |
| P6 | 차단 | targetSdk 35 이상은 64비트 `.so` 가 16KB 페이지 정렬이어야 한다. 카카오맵 SDK(`kakao_map_sdk` 1.3.0)가 네이티브 라이브러리를 싣는다 | `client/tools/release_store.py` 가 AAB 안 64비트 `.so` 의 ELF LOAD 정렬(≥ 0x4000)을 검사. 걸리면 해당 SDK 를 16KB 지원 버전으로 | ✅ 2026-09-27 AAB 0.1.0+30 검사 — 64비트 .so 9개 모두 LOAD 정렬 ≥ 16KB. 카카오맵 SDK 교체 불필요 |
| P7 | 중간 | 13~15세를 대상에 넣었고 UGC(글·사진·영상)가 있는데, 운영자 조치가 DB 직접 수정뿐이고 계정 정지 기능이 없다 (`MODERATION.md` §2) | `users.suspended_at` + 로그인·JWT 필터 차단, 글 숨김·미디어 삭제·정지를 한 번에 하는 운영 스크립트, 약관 제14조에 정지 근거 | ✅ 2026-09-28 — **사용자 승인(엔티티 변경).** `V20__user_suspension.sql`: `users.suspended_until`(지나면 저절로 풀림, 영구는 9999-12-31)·`suspension_reason`(운영 기록, 탈퇴 익명화 때 삭제). 서버: 이메일·카카오 로그인과 토큰 갱신이 정지면 401 `ACCOUNT_SUSPENDED`(기간·문의처 문구, 사유는 안 실음, 틀린 비밀번호엔 정지 여부를 안 알림), 갱신 거절 때 남은 세션 전부 삭제. 차단 목록 값 `"S"` 면 쓰던 access 토큰도 즉시 정지로 답함(탈퇴와 구분). 운영: `tools/moderate.py` — `reports`·`post`·`hide-post`(앱 삭제와 같은 표시 + 첨부 보관기한을 당겨 04:15 배치가 R2 삭제 + 관련 신고 처리)·`hide-media`·`suspend --days/--forever --reason [--hide-posts]`(DB 기록 + 기기 토큰 삭제 + Redis 차단 + USER 신고 처리)·`unsuspend`·`resolve`, 모두 미리보기 후 `yes`. 앱: 갱신이 정지로 거절되면 로그아웃하며 로그인 화면에 서버 안내를 띄움. 약관 제14조 제5~8항(사유·기간·효과·이메일 고지·7일 이의), 방침에 정지 기록 항목·보유 기간 — **시행일 2026-10-06**(제3조 7일 전 게시, L5 와 함께 하루 미룸). `MODERATION.md` 절차·기간 기준·고지 메일 견본. 테스트: `AccountSuspensionIntegrationTest` 2건(스크립트와 같은 SQL·Redis 로 정지·해제), `suspension_notice_test.dart` 3건. 2026-09-29 사용자가 `python tools/moderate.py reports` 로 운영 서버 접속·조회 확인. **10-06 전에는 정지하지 않는다**(시행 후 테스트 계정 하루 정지는 §7 선택 항목) |
| P8 | 중간 | 스토어 카테고리를 **소셜**로 고르면 아동 안전 표준(CSAE) 정책이 붙는다(공개 웹 페이지·앱 내 신고·담당자) | "음악 및 오디오" 또는 "라이프스타일" 선택. 소셜이면 `bandule.com/child-safety` 와 담당자 준비 | ⬜ |
| P9 | 낮음 | 데이터 안전·콘텐츠 등급 답안에 "댓글"이 있으나 댓글 기능 없음 | `play-app-content.md` 에서 "게시글 본문"으로 수정 | ✅ 2026-09-29 — "댓글" 은 **우리 문서의 근거 칸에만** 있었다. Play 데이터 보안은 유형 체크(메시지 › 기타 인앱 메시지)이고 IARC 는 예/아니요라 콘솔 답은 그대로 맞다 — 콘솔에서 바꿀 것 없음. 문서만 "게시글 본문" 으로 |
| P10 | 낮음 | 병합 매니페스트에 `READ/WRITE_EXTERNAL_STORAGE` (READ_MEDIA_* 는 없어 사진·동영상 권한 선언 대상 아님) | 병합 리포트에서 `maxSdkVersion=32` 확인, 없으면 `tools:node="remove"` | 🟡 2026-09-29 — 앱은 저장소 권한을 쓰지 않는다(시스템 선택기가 캐시로 복사, 압축본도 캐시). 매니페스트에 `tools:node="remove"` 두 줄. CI `android-build` 에 병합 권한 목록 주석을 달아 확인: INTERNET·ACCESS_NETWORK_STATE·WAKE_LOCK·POST_NOTIFICATIONS·BILLING·c2dm RECEIVE(+내부용 1개), 저장소 권한 없음. **남은 것: +32 에서 사진·영상 첨부가 그대로 되는지(§7 영상 첨부 항목과 함께), 특히 Android 9 이하 기기가 있으면 그걸로** |
| P11 | 낮음 | targetSdk 36(Android 16)은 가로·세로 너비 600dp 이상 화면에서 방향 고정·크기 제한을 무시한다. 태블릿·폴더블에서 레이아웃이 늘어나거나 깨질 수 있다 | 태블릿 에뮬레이터(또는 폴더블 펼침)로 홈·캘린더·정산·게시판을 한 번씩 본다 | 🟡 2026-09-29 — 확인해 보니 앱은 원래 **방향 고정을 안 했다**(매니페스트 `screenOrientation`·`setPreferredOrientations` 없음) — Android 16 이 바꾸는 동작은 없고, 문제는 화면들이 폰 세로 기준이라 큰 화면에서 카드·목록·버튼이 끝까지 늘어나는 것. `core/layout/readable_width.dart` 를 `app.dart` 의 공통 builder 에 넣어 폭 720dp 가 넘으면 **가운데 720dp 기둥**(양옆은 앱 배경색)으로 묶고, 안쪽 `MediaQuery` 의 화면 폭도 720 으로 바꿔 폭을 재는 위젯(합주실 선택 시트 등)이 어긋나지 않게 했다. 가로 폰의 카메라 구멍 여백은 기둥 밖 여백으로 흡수. 전체화면 사진·영상도 기둥 안이라 가로에서 조금 좁다(감수). 테스트 `readable_width_test.dart` 3건. **남은 것: 태블릿 에뮬레이터(ARM 이미지 — x86_64 는 카카오맵이 꺼짐)나 폴더블 펼침, 또는 폰을 가로로 돌려 홈·캘린더·정산·게시판·요금제를 한 번씩 보기** |

---

## 4. 결제(인앱 구독)

이미 된 것: 상품 ID·만료일 검증, 운영에서 noop 게이트웨이 거부, 웹훅 멱등·재전송 폭풍 차단, 해지·만료·환불
실기기 확인(2026-09-09). 아래는 그 바깥의 **구독 생애주기** 문제다.

| ID | 심각도 | 사용자에게 생기는 일 | 원인 | 해결방안 | 상태 |
|---|---|---|---|---|---|
| B1 | 높음 | 카드 결제 실패로 계정 보류 → 결제 수단을 고치면 Google 은 다시 청구하는데 밴드는 FREE 그대로. 30일 뒤 사진·영상 삭제 | `BandPlan.downgradeToFree()` 가 `purchaseToken`·`store` 를 비운다. 이후 같은 토큰의 RECOVERED(1)·RESTARTED(7)·RENEWED(2) RTDN 이 밴드를 못 찾고 1시간 뒤 버려진다 | 강등해도 토큰을 남긴다 — `downgradeToFree()` 에서 `store`·`purchaseToken` 을 지우지 않는다(V17 CHECK 는 둘이 짝이기만 하면 된다). REVOKED 만 버린다. 회귀 테스트: ON_HOLD → RECOVERED 시 PREMIUM 복귀 | 🟡 2026-09-28 — `downgradeToFree` 가 토큰을 남기고 REVOKED 는 새 `revokeToFree` 로 비운다(`PlanMutationService.applyRevoke`). 테스트: `BandPlanTest`, `GooglePlayWebhookIntegrationTest` 의 보류→RECOVERED·만료→RESTARTED·REVOKED 3건. 개인정보처리방침 보관 문구 정정(시행일 2026-09-28). PR #102 머지·배포. 남은 것: 테스트 카드로 보류→복구 실확인(§6) |
| B2 | 높음 | 결제 직후 검증 전에 앱이 꺼지거나 네트워크가 끊기면 요금제 화면을 다시 열 때까지 검증이 안 되고, 3일 뒤 Google 이 자동 환불. Play 스토어에서 재구독한 것도 앱이 모른다 | `purchaseStream` 구독이 `PlanScreen.initState` 에만 있고 `restorePurchases()` 호출이 없다 | 로그인 직후 앱 전역(`app.dart`)에서 스트림 구독 + `restorePurchases()`, 앱 복귀 때도 한 번 | 🟡 2026-09-28 (PR #103) — 결제 스트림을 앱 전역 `PurchaseSync`(`purchase_sync.dart`)로 옮겼다. 로그인하면 스트림을 열고 `restorePurchases()`, 앱 복귀 때도(30초 간격) 다시 묻는다. 확인 안 된 구매만 새 API `POST /api/v1/plan/google/restore` 로 보내고, 성공해야 스토어에 완료를 알린다. 이미 확인된 구매는 서버를 부르지 않는다. 테스트: `test/purchase_sync_test.dart`, `PlanPurchaseRestoreIntegrationTest`. 남은 것: 다음 스토어 빌드(0.1.0+32)에서 결제 직후 앱 강제 종료 → 재실행 시 PREMIUM 확인. 결제 후 3일 동안 앱을 한 번도 안 열면 여전히 환불된다(웹훅 쪽에서 밴드 표시로 반영하는 것은 B12) |
| B3 | 높음 | 검증이 "결제한 밴드"가 아니라 "지금 선택된 밴드"로 간다. B2 상황에서 다른 밴드로 붙을 수 있다 | `_verifyPurchase` 가 `currentBandProvider` 사용, `applicationUserName` 없음, 서버 obfuscatedAccountId 대조 미구현 | 구매 시 `applicationUserName = "b{bandId}-u{userId 해시}"`, 서버는 `externalAccountIdentifiers.obfuscatedExternalAccountId` 의 bandId 로 결정, 다르면 409 | 🟡 2026-09-28 (PR #103) — 구매할 때 `applicationUserName = band-{bandId}`(`IapService.bandTag`), 서버는 `externalAccountIdentifiers.obfuscatedExternalAccountId` 를 `PurchaseBandTag` 로 읽는다. 복구 API 는 그 밴드로 반영(밴드장만), 기존 검증 API 는 적힌 밴드와 다르면 409 `PURCHASE_BAND_MISMATCH`. 표시 없는 옛 구매는 422 `PURCHASE_BAND_UNKNOWN` — 앱은 방금 결제를 시작한 밴드가 있을 때만 그 밴드로 검증한다. 설계 변경: 표시에 사용자 해시는 넣지 않았다(밴드 id 만, 개인정보 아님). 남은 것: 실결제로 Play 응답에 표시가 실리는지 확인 |
| B4 | 높음 | 한 Google 계정은 같은 구독 상품을 동시에 하나만 가진다. 밴드 2개의 밴드장은 두 번째 밴드를 결제할 수 없고 "이미 보유" 오류만 본다 | 상품 `premium_yearly` 하나, 구독은 밴드 단위 | 단기: 오류 코드를 잡아 안내 + 요금제 화면·약관에 제약 명시. 장기: 상품 구조 재검토 | 🟡 2026-09-28 — 사용자 결정: 구독은 밴드 단위이고 한 사람이 여러 밴드를 올릴 수 있어야 한다 → **값·기간이 같은 구독 상품 5개**(`premium_yearly`, `_2`~`_5`). 앱은 이 계정이 가진 상품을 스토어에 물어(`restorePurchases`) 빼고 앞에서부터 골라 결제하고, "이미 보유" 로 실패하면 다음 상품으로 다시 띄운다. 5개 다 가졌으면 "한 계정으로 밴드 5개까지" 안내. 서버는 `app.plan.billing.google-product-ids`(목록, 기본 5개)에 있는 상품만 PREMIUM. 요금제 화면 고지를 "밴드마다 따로 결제, 한 계정으로 5개까지" 로. 구독 관리 링크는 상품을 고르지 않고 목록을 연다. 함께 고침: Play 가 취소·오류 이벤트에 상품 id 를 비워 보내 예전엔 버려졌다(취소하면 버튼이 잠긴 채 남음). 2026-09-28 PR #105 머지·배포. Play Console 에 `premium_yearly_2`~`_5` 를 `premium_yearly` 와 같은 설정으로 만들고 활성화했다(이름 "밴듈 프리미엄 (연간) N", 기본 요금제 `yearly` 매년 자동 갱신, 유예 14일·계정 보류 자동 46일, 다음 청구일에 청구, 재구독 허용, KRW 19,000 기준 자동 환산 174개국, 이전 버전과의 호환성). **남은 것: +32 에서 밴드 2개 결제 확인** |
| B5 | 높음 | PREMIUM 밴드를 삭제하거나 결제한 밴드장이 탈퇴·위임해도 Google 결제는 계속된다. 삭제된 밴드는 갱신 RTDN 이 버려져 없는 밴드에 매년 청구 | 삭제(`band_settings_screen.dart`)·탈퇴(`account_screen.dart`) 창에 구독 언급 없음, `BandPurgeService` 는 요금제 행만 지움 | 서버: 스토어 구독이 살아 있는(해지 예약 아닌) 밴드 삭제를 409 로 막고 "먼저 Play 에서 해지" 안내. 앱: 탈퇴·위임 창에 구독 경고 + 구독 관리 링크, 위임받은 밴드장에게 "결제한 사람의 Google 계정에서 관리" | 🟡 2026-09-28 — 서버: `BandPlan.isAutoRenewingStoreSubscription()`(PREMIUM·스토어·해지 예약 아님)이면 `BandDeletionService.delete` 가 409 `BAND_HAS_ACTIVE_SUBSCRIPTION`. 해지 예약·쿠폰 PREMIUM 은 삭제 가능. 요금제 응답에 `autoRenewing` 추가(B6 도 쓸 값). 앱: 삭제 전에 요금제를 새로 받아 자동 갱신이면 "구독을 먼저 해지" 안내 + 구독 관리 열기, 프리미엄이면 "남은 기간도 사라져요", 위임 창에 "구독은 결제한 사람 계정에 남아요", 탈퇴 화면·창에 "탈퇴해도 해지 안 됨" + 구독 관리 버튼(탈퇴 자체는 막지 않는다 — Play 계정 삭제 정책), 요금제 화면에 "결제한 사람 계정에서만 관리". 결제자를 서버가 모르므로(구매 기록엔 밴드만) 안내는 "결제한 사람" 으로 쓴다. 테스트: `BandDeletionIntegrationTest` 3건 추가, 기존 전체 삭제 픽스처는 해지 예약 후 삭제. 남은 것: +32 실기기에서 §6 "구독 중인 밴드 삭제 시도" |
| B6 | 중간 | 자동 갱신 구독자에게도 30·7·1일 전 "프리미엄이 N일 남았어요… 사진·영상이 사라져요" 푸시, 홈에는 모든 멤버에게 끝난다는 배너 | `PlanExpiryReminderService`·`plan_expiry_banner.dart` 가 `canceled`·`store` 를 안 본다 | 응답에 `autoRenewing` 추가, 예고는 해지 예약·쿠폰 밴드에만 | ✅ 2026-09-28 — 서버: 예고 대상 조회(`findPremiumExpiringBetween`)에 `(store is null or subscriptionRef is null)` — 해지 예약·쿠폰 PREMIUM 만. 앱: 홈 배너가 `autoRenewing`(B5 에서 추가)이면 안 뜬다. 테스트: `PlanExpiryReminderIntegrationTest` 자동 갱신 제외·쿠폰 예고 2건(기존 건은 해지 예약 상태로), `plan_expiry_banner_test.dart` 5건. 쿠폰 PREMIUM 인데 요금제 화면이 "자동 갱신" 이라고 하는 것은 B7 |
| B7 | 중간 | 결제 중인 밴드에 쿠폰 → 다음 갱신 때 스토어 만료일로 덮임. 쿠폰 기간 중 결제 → 쿠폰 잔여일 소멸. 구독 만료 시 쿠폰 일수도 사라짐 | `applyStoreRenew` 가 `expiresAt` 을 덮어씀, EXPIRED 즉시 강등 | 스토어 구독 밴드는 쿠폰 409, 쿠폰 PREMIUM 에서 구매 시 "남은 쿠폰 기간은 사라집니다" 안내 | 🟡 2026-09-28 — **사용자 결정: 막지 않고 쌓는다.** Play Developer API `purchases.subscriptionsv2.defer` 로 스토어 결제일 자체를 미룬다(DB 만 늘리면 다음 갱신 알림이 덮는다). ① 결제 중 쿠폰: `PlanCouponService` 가 사용 기록·차감을 먼저 커밋 → 트랜잭션 밖에서 쿠폰 일수만큼 defer → 스토어가 준 새 만료일로 맞춤, 거절(409 `COUPON_STORE_REJECTED`)·장애(503 `COUPON_STORE_UNAVAILABLE`)면 사용 기록·차감을 되돌린다. ② 쿠폰 중 결제: `PlanMutationService.applyStoreRenewCarryingCoupon` 이 행 잠금 안에서 남은 쿠폰 기간을 돌려주고(결제 확인·웹훅이 겹쳐도 한 번), 확인 처리 뒤 그만큼 defer. 실패하면 결제는 그대로 두고 로그. ③ 구독 만료 때 쿠폰 소멸은 ①로 스토어 날짜가 늘어 해소. 쿠폰 표시 `BandPlan.isCouponPeriod()`(`coupon-` 접두사) — 보류 뒤 남은 토큰 위 쿠폰도 자동 갱신으로 보지 않는다(B5·B6 조회 포함), FREE 에 쿠폰을 쓸 때 남은 스토어 토큰을 지우지 않는다(B1 복구 보존). 앱: 쿠폰 프리미엄에 "자동 갱신" 대신 쿠폰 안내 + 결제 버튼. 테스트: 쿠폰 통합 4건·BandPlan 2건. 서비스 계정 "주문 및 구독 관리" 권한 있음(사용자 확인 2026-09-29). **남은 것: 라이선스 테스터로 결제 중 쿠폰 → Play 구독 화면의 다음 결제일이 밀리는지 확인** |
| B8 | 중간 | 갱신 알림 지연·유실·유예기간이면 야간 배치가 결제한 밴드를 강등하고, 토큰이 지워져 B1 로 이어진다 (2026-09-28 B1 수정으로 토큰은 남는다 — 늦은 RENEWED 가 오면 복구된다. 그 사이 FREE 로 보이는 것은 그대로) | `PlanService.expireOverdue` 가 DB 만료일만 본다 | 스토어 밴드는 강등 전 `subscriptionsv2.get` 재조회, ACTIVE·IN_GRACE 면 만료일만 갱신 | ✅ 2026-09-28 — `PlanService.expireOverdue` 가 강등 전에 `StoreSubscriptionService.recheckBeforeExpiry` 를 부른다: 스토어 결제 밴드면 `subscriptionsv2` 재조회 → ACTIVE·IN_GRACE·CANCELED 이고 스토어 만료일이 미래면 그 날짜로 연장(CANCELED 면 해지 예약 표시도), 끝났으면 강등, 스토어가 일시 장애면 DB 만료 3일까지는 건너뛰고 그 뒤엔 강등(무기한 PREMIUM 방지). 쿠폰 밴드는 예전처럼 바로 강등. 테스트: `PlanExpirationIntegrationTest` 3건(유효→연장, 짧은 장애→보류, 긴 장애→강등), 기존 강등 테스트는 스토어도 끝난 상태(`expired-` 토큰)로 |
| B9 | 낮음 | 밴드장 한 명의 환불로 다른 멤버의 오래된 사진·영상까지 즉시 만료 | `applyRevoke` 유예 0일(약관 제12조 7항에 명시) | 약관상 위반은 아님. 7일 유예 검토 | ➖ 2026-09-29 **사용자 결정: 지금처럼 즉시.** 결제 → 대용량 업로드 → 환불로 저장소를 공짜로 쓰는 악용을 막는 쪽. 약관 제12조 7항에 이미 적혀 있다 |
| B10 | 낮음 | 웹훅 시크릿이 URL(`?token=`)이라 Nginx 접근 로그에 남음 | `WebhookAuthenticator` | 이미 구현된 OIDC 켜기(`PLAN_BILLING_PUBSUB_AUDIENCE`·`_SA`), 공유 시크릿 끄기 | ⬜ |
| B11 | 낮음 | 한 달만 구독해 정기 규칙을 만들고 해지해도 규칙은 계속 회차를 만든다 | `RecurringRuleService` 는 생성만 막음 | 의도면 ➖, 아니면 강등 시 규칙 일시정지 | 🟡 2026-09-29 — **사용자 결정: 일시정지.** 회차 연장 배치(`RecurringRuleService.extendRule`)가 FREE 밴드의 규칙은 건너뛴다(스키마 변경 없이 요금제로 판단 — PREMIUM 으로 돌아오면 다음 배치부터 저절로 재개). 이미 만든 회차(앞으로 8주분)는 그대로. 재개 때 멈춰 있던 과거 날짜는 채우지 않고 오늘부터. 앱 정기 일정 화면의 무료 안내에 "새 회차를 만들지 않아요, 프리미엄이면 다시 이어져요" 추가. 예전 "내려가도 계속 돈다" 테스트를 새 결정으로 바꾸고 일시정지·재개 테스트 추가. **남은 것: +32 에서 무료 밴드의 정기 일정 화면 문구 확인** |
| B12 | 낮음 | 결제 후 앱을 3일 동안 한 번도 열지 않으면 검증·확인 처리가 안 돼 Google 이 자동 환불한다 (B2 로 "앱을 다시 열면 복구" 까지는 됨) | 웹훅 PURCHASED 는 토큰으로만 밴드를 찾고, 못 찾으면 1시간 뒤 버린다 | 웹훅에서 밴드를 못 찾으면 스토어 조회 → `PurchaseBandTag` 로 밴드 결정 → PREMIUM + acknowledge (2026-09-28 B2 작업 중 발견) | 🟡 2026-09-29 — `StoreSubscriptionService.grantByPurchaseTag`: 부여형 알림(PURCHASED·RENEWED 등)인데 토큰이 어느 밴드에도 안 붙었으면 스토어를 조회해 구매에 적힌 밴드(`band-{id}`)로 PREMIUM + 확인 처리. 앱 verify 와 겹쳐도 `grantPremium` 이 연장으로 이어 감. 적힌 밴드가 없어졌으면 200 으로 끝내고 자동 환불에 맡김(재전송 폭풍 없음), 스토어 일시 장애면 1시간 안에서만 재전송. 밴드가 안 적힌 옛 구매는 예전대로 verify 대기. 테스트: 웹훅 통합 3건. **남은 것: §7 결제 시나리오 "결제 직후 강제 종료 → 다시 열지 않음"** |

---

## 5. 사용성·버그

| ID | 심각도 | 증상 | 원인 | 해결방안 | 상태 |
|---|---|---|---|---|---|
| U1 | 높음 | 로그아웃해도 그 폰으로 이전 계정의 일정·정산 푸시가 계속 온다(공용 폰이면 다른 사람에게 노출) | `AuthController.logout()` 이 토큰 저장소를 먼저 비움 → `app.dart` 의 `push.stop()` 이 인증 없이 `DELETE /device-tokens` → 401 → 서버에 토큰 남음 | 로그아웃에서 `push.stop()` 을 먼저 await. 더 튼튼하게는 `/auth/logout` 본문에 `deviceToken` 을 실어 서버가 삭제 | 🟡 2026-09-28 — 둘 다 했다. 서버: `POST /auth/logout` 에 선택 필드 `deviceToken`, 받으면 `DeviceTokenService.forgetDevice` 로 그 토큰 행을 지운다(인증·소유자 확인 없이 — refresh 가 만료된 강제 로그아웃도 정리돼야 해서. 토큰 값을 아는 것이 곧 그 기기). 앱: `AuthController.logout()` 이 저장소를 비우기 **전에** 기기 토큰을 로그아웃 요청에 싣고 `push.stop(unregister: false)`, 세션 만료 강제 로그아웃도 같은 요청을 보낸다. `PushService.stop()` 은 끝에 `FirebaseMessaging.deleteToken()` 으로 기기 FCM 토큰을 폐기 — 서버 요청이 실패해도 이 기기로는 안 온다. 테스트: `DeviceTokenIntegrationTest` 3건, `client/test/logout_push_test.dart`. 남은 것: +32 실기기 확인 |
| U2 | 중간 | 글을 본 지 10분 뒤 같은 글을 다시 열면 사진이 깨지고 영상 재생 실패 | presigned GET 10분 + `postDetailProvider` 가 autoDispose 아님 | autoDispose 로, 재생 오류 시 상세 재조회 후 1회 재시도, TTL 30~60분 검토(방침 "10분" 문구도 같이). 실기기 확인 | 🟡 2026-09-28 — **TTL 은 10분 유지**(BUILD_PLAN 이 5~15분으로 정했고 방침 제9조 문구도 그대로 맞다). 대신 앱이 실패를 만료로 보고 새 주소를 받는다: `postDetailProvider` 를 autoDispose 로(닫으면 버림 — 알림으로 다시 열어도 새로 받음), 상세의 사진이 깨지면 상세 재조회, 전체화면 사진·영상은 실패 시 상세를 다시 받아 **새 주소로 한 번 더** 시도(`_FullImage`·`_FullVideo`), 피드 썸네일이 깨지면 피드 새로고침. 정말 깨진 파일에서 무한 재조회하지 않게 대상별 간격 제한(`core/media/stale_url_guard.dart`, 상세 60초·피드 2분). 테스트: `stale_url_guard_test.dart` 3건. **남은 것: 실기기에서 글 상세·피드를 연 채 10분 넘게 둔 뒤 사진·영상을 눌러 정상 표시되는지(§7)** |
| U3 | 중간 | 스토어 AAB 를 손 명령으로 만든다. `dart_defines.json` 의 `API_BASE_URL` 이 localhost 라 옵션 하나 빠뜨리면 아무 데도 못 붙는 앱이 올라간다. `BUILD_LABEL` 이 없으면 "개발 빌드" 표시 | AAB 용 스크립트 없음 | `client/tools/release_store.py` | ✅ 2026-09-27 첫 실행 통과(서버 주소·16KB·targetSdk·업로드 키 서명 OK), AAB 0.1.0+30 생성 |
| U4 | 낮음 | 구독 종료일이 "9월 10일 (목)" 처럼 연도 없이 나옴 | `Fmt.dateKoUtc` | 연도 포함 포매터 | ✅ 2026-09-29 — `Fmt.dateKoWithYearUtc`("2027년 9월 10일 (금)")를 만들어 요금제 화면 "구독기간 종료" 에 씀. 초대 만료(며칠 뒤)·일정 날짜는 올해라 그대로. 테스트 `formatters_test.dart` |
| U5 | 낮음 | 첫 실행이 오프라인이면 기본 폰트, 매번 Google 폰트 서버 접속 | `google_fonts` 런타임 다운로드 | 폰트를 assets 에 넣고 `allowRuntimeFetching = false` | ➖ 2026-09-29 **사용자 결정: 그대로.** 굵기마다 첫 사용 때 한 번 받고 기기에 캐시된다 — 영향은 "오프라인으로 처음 켠 그 한 번만 기본 글꼴" 뿐. 본문 Noto Sans KR 을 굵기 5종(500~900) 넣으면 앱이 크게 는다 |
| U6 | 낮음 | 알림 눌러도 해당 화면으로 안 감, 지도가 첫 합주실에 고정, 특정 영상 압축 멈춤 | `NEXT.md` §3·§1-E | 출시 후 업데이트 후보 | 🔧 2026-09-29 영상 압축 멈춤은 U7 에서 라이브러리 교체로 대응(실기기 확인 남음). 알림 이동·지도는 ⬜ |
| U7 | 중간 | 빌드 경고: `kakao_flutter_sdk_common`·`video_compress` 가 Kotlin Gradle Plugin 을 직접 적용한다 — "향후 Flutter 버전은 이런 플러그인이 있으면 빌드 실패". 지금 빌드는 된다 | Flutter 가 Built-in Kotlin 으로 옮겨 가는 중 | 두 플러그인 새 버전 확인 후 올리기. `video_compress` 는 압축 교착(U6)도 있어 교체 후보(`NEXT.md` §1-E). **Flutter 업그레이드 전에 처리** | 🟡 2026-09-29 — **사용자 승인(라이브러리 교체).** ① `kakao_flutter_sdk_user` ^1.9.6 → ^2.0.1(AGP 9 지원): `await KakaoSdk.init`, 매니페스트 리다이렉트 액티비티 `com.kakao.sdk.flutter.auth.AuthCodeHandlerActivity`(옛 이름이면 빌드는 되고 로그인 뒤 앱으로 못 돌아옴), 2.x 에서 없어진 `KakaoSdk.origin`(디버그 키 해시 출력) 제거. ② `video_compress` → `v_video_compressor` ^2.2.3(MIT, Media3 Transformer, 720p·1.8Mbps `medium`, 원본보다 크면 원본). 90초 멈춤 감시 유지, 트랜스코더 proguard 규칙 제거. 권한은 기존과 같은 READ/WRITE_EXTERNAL_STORAGE 뿐(READ_MEDIA_* 없음 — P10 영향 없음). ③ Client CI 에 `android-build` 잡(`flutter build apk --release --flavor prod` — Gradle·R8 까지, 실패하면 "What went wrong" 을 주석으로)과 analyze 오류 주석. **`android.builtInKotlin=true` 는 못 켰다** — `kakao_map_sdk` 1.3.x 가 KGP 를 무조건 적용해 CI 에서 "Failed to apply plugin 'org.jetbrains.kotlin.android'"(최신 main 도 Kotlin 1.9·AGP 8.5). 지금 Flutter 3.47.2 에선 false 로 문제없다. **남은 것: +32 에서 카카오 로그인(카카오톡·카카오계정 둘 다)·영상 첨부 압축(예전에 멈추던 131MB HEVC 영상 포함) 확인, `pubspec.lock` 은 +32 빌드 때 갱신·커밋, kakao_map_sdk 가 Built-in Kotlin 을 지원하면 true 로 켜고 CI android-build 확인** |
| U8 | 낮음 | 빌드 경고 "CupertinoIcons 폰트를 찾지 못함" | 앱 코드는 `CupertinoIcons` 를 쓰지 않는다 — 의존성 쪽 참조 | 화면에 빈 아이콘이 보이면 `cupertino_icons` 추가, 아니면 무시 | ➖ 앱 코드 미사용 (2026-09-27) |

릴리스 빌드(ProGuard)로 지도·로그인·푸시를 실기기에서 본 기록도 없다 — P2 와 같은 설치본으로 함께 확인한다.

---

## 6. 법적 문서·개인정보

| ID | 심각도 | 문제 | 해결방안 | 상태 |
|---|---|---|---|---|
| L1 | 높음 | 방침 제6·7조가 Cloudflare 에는 "사진·영상"만 간다고 적었지만 매일 DB 백업이 R2(`s3://bandule-prod/db-backups/`)에 올라가 회원정보 전체가 간다. 서버 호스팅(Vultr)·메일 발송(Resend)도 빠졌다 | 제6조에 Vultr(서버·DB), Resend(메일 발송) 추가, Cloudflare 항목에 DB 백업 추가, 제7조 국외이전도 같이. 제3조에 "백업 7일 보관, 그동안 삭제된 데이터가 백업에 남을 수 있음". `site/build.py` 로 재생성·재배포 | ✅ 2026-09-28 — 메일 업체를 서버에서 확인(운영 점검 워크플로에 "메일 발신 호스트" 주석 추가 → `smtp.resend.com`). 방침 제3조에 DB 백업 7일 보관(삭제된 정보도 최대 7일 남음), 제6조에 Vultr(The Constant Company, LLC — 서울)·Resend(Plus Five Five, Inc.) 추가, Cloudflare 항목에 DB 백업·IP·문의 메일 전달 추가, 제7조 국외 이전에 Cloudflare 백업·문의 메일과 Resend 추가, 서버가 서울이라 이전 아님 명시. 시행일 2026-09-28 개정 내역에 기록, `site/` 재생성. 쓰지 않는 예전 발신 설정은 설정 기본값·견본·문서에서 모두 걷어 냈다 |
| L2 | 중간 | 연락처가 둘이다 — 스토어 `qkrwkddjs777@naver.com`, 방침·약관·계정 삭제 페이지 `notice@bandule.com` | 하나로 통일, `notice@bandule.com` 을 쓰면 외부에서 실제 수신 확인 | ✅ 2026-09-29 — `notice@bandule.com` 으로 통일(약관·방침·계정 삭제 안내·앱의 정지 안내는 이미 이 주소, `store-listing.md` 의 설명 "■ 문의"·연락처 이메일도 교체). ① 2026-09-29 사용자가 외부 메일에서 보내 **수신 확인** ② 같은 날 사용자가 Play Console 스토어 설정 연락처 이메일·자세한 설명 "■ 문의" 교체 |
| L3 | 확인 | 유료 판매자로서 통신판매업 신고(또는 면제), 판매자 정보 표시 의무, 약관 제12조 6항 "직접 환불해 드릴 수 없으며" 가 청약철회 규정과 충돌하지 않는지 | 세무·법률 상담. 문구는 "환불 요청은 Google Play 를 통해 처리되며, 문의는 연락처로 받습니다" 처럼 완화 | 🟡 2026-09-29 — 사용자가 통신판매업 신고(제2026-서울성북-1209호, 성북구청)를 마치고 Play Console "한국 개발자 추가 정보"에 사업자등록번호·신고번호·신고 기관 입력. 약관 제12조 제6항을 "결제·환불은 Google Play 가 처리, 환불은 Google Play 에서 요청, 문의는 notice@bandule.com, **관련 법령의 청약철회 권리를 제한하지 않는다**" 로 고침(시행 2026-10-06). 판매자 정보(상호 밴듈·대표·사업자등록번호·신고번호·주소·전화·이메일)를 **bandule.com 모든 쪽 하단**(`site/build.py` `BUSINESS`)과 **앱 요금제 화면 구독 안내**(`plan_screen.dart` `_sellerLine`)에 표시. **남은 것: 앱 표시는 +32 빌드부터. 법률 검토는 받지 않음.** 이전 기록: 2026-09-27 Play Console › 계정 세부정보 "한국 개발자 추가 정보"에 사업자등록번호 입력. Google 은 **유료 앱·인앱 구매를 배포하는 사업자**에게 통신판매 신고번호·신고 기관을 요구한다. **남은 것: 정부24 통신판매업 신고(2~4일) → 신고번호·신고 기관(시/군/구청) 입력, 프로덕션 출시 전까지.** 약관 문구 검토는 별도 |
| L4 | 낮음 | 방침의 "게시글 및 댓글", "게시글을 길게 눌러 차단" — 댓글 없음, 차단은 글 상세 메뉴 | 실제 동작대로 수정 후 재배포 | ✅ 2026-09-29 — 방침 "게시글의 내용", 차단은 "게시글 상세 화면 오른쪽 위 메뉴(⋮)" 로 고치고 사이트 재생성. Play 데이터 안전 답안의 "댓글" 은 P9 |
| L5 | 낮음 | B4(계정당 한 밴드 구독)·B5(삭제·탈퇴해도 구독 유지)가 약관에 없다 | 제12조에 한 줄씩, 시행일 7일 전 게시(제3조 3항) | ✅ 2026-09-29 — B4 가 바뀌어(한 계정으로 최대 5개 밴드) 제12조 제10항 "구독은 밴드마다 따로, 한 계정당 최대 5개 밴드", 제11항 "밴드 삭제·탈퇴해도 구독은 자동 해지되지 않음, 자동 갱신 중이면 먼저 해지해야 밴드 삭제 가능". 시행 2026-10-06(P7 의 제14조 개정과 함께, 그래서 P7 시행일도 10-05 → 10-06) |
| L6 | 낮음 | 가입 때 남기는 약관·방침 동의 버전(`app.terms.version`)이 `2026-09-06` 에 멈춰 있었다 — 09-09·09-17·09-28 개정 때 안 올려서, 그 뒤 가입자의 동의 기록이 옛 문서를 가리킨다 | 문서 시행일과 함께 올린다 | ✅ 2026-09-29 — 기본값을 `2026-10-06`(지금 게시하는 개정본)으로. `docker-compose.prod.yml` 이 `TERMS_VERSION` 을 넘기지 않아 운영도 기본값을 쓴다(저장소 기준 — 서버 `.env.prod` 는 조회하지 않음). 이미 가입한 사람의 기록은 고치지 않는다(그때 본 문서를 알 수 없음) |

---

## 7. 제출 직전 확인

**내부/비공개 트랙 설치본(구글 재서명)으로**

- [x] `python tools/release_store.py` 통과 — 서버 주소·16KB·targetSdk·서명 (P5·P6·U3) — 2026-09-27, 0.1.0+30
- [ ] 카카오 로그인·카카오맵·푸시·초대 링크·영상 재생 (P2, ProGuard 포함)
- [ ] 결제 시나리오 (라이선스 테스터 계정, 테스트 카드)
- [ ] 로그아웃 → 다른 폰에서 그 밴드에 일정 등록 → 로그아웃한 폰에 푸시가 **안 옴** (U1)
- [ ] 사진·영상 있는 글을 연 채 11분 → 사진·영상 정상 표시·재생, 피드도 11분 띄운 뒤 썸네일 다시 보임 (U2)
- [ ] 요금제 화면 구독 안내 맨 아래에 판매자 정보 한 줄 (L3)
- [ ] 카카오 로그인 — 카카오톡 앱으로 한 번, 카카오계정(브라우저)으로 한 번. 로그인 뒤 앱으로 돌아와야 한다 (U7, SDK 2.x)
- [ ] 폰을 가로로 돌리거나(또는 태블릿·폴더블) 홈·캘린더·정산·게시판·요금제 — 가운데 기둥으로 보이고 잘리거나 겹치는 곳이 없는지 (P11)
- [ ] 무료 밴드(쿠폰 끝난 밴드 등)의 정기 일정 화면 — "새 회차를 만들지 않아요" 안내 (B11)
- [ ] 사진 첨부 여러 장 — 저장소 권한을 뺐으니 그대로 되는지 (P10)
- [ ] 영상 첨부 — 몇 분짜리 영상을 올려 "압축 N%" 가 오르고 끝나는지, 예전에 33% 에서 멈추던 영상도 (U7·U6)

| 시나리오 | 기대 결과 | 관련 | 결과 |
|---|---|---|---|
| 결제 직후 앱 강제 종료 → 재실행 | 다른 화면에 있어도 PREMIUM, acknowledge 완료 | B2 | ⬜ |
| 결제 직후 앱 강제 종료 → **다시 열지 않고** 5분 뒤 다른 멤버 폰으로 그 밴드 요금제 확인 | PREMIUM (웹훅이 올림). Play 주문 내역에 "환불" 이 3일 뒤에도 안 생김 | B12 | ⬜ |
| 밴드 A 결제 중 네트워크 끊기 → 밴드 B 로 전환 후 재검증 | A 가 PREMIUM, B 는 그대로 | B3 | ⬜ |
| 테스트 카드 "항상 거절"로 갱신 실패 → 계정 보류 → 카드 변경 | 보류 중 FREE, 복구 후 PREMIUM | B1 | ⬜ |
| 해지 → 만료 → Play 스토어에서 재구독 | 앱을 열면 PREMIUM | B1·B2 | ⬜ |
| 밴드 2개의 밴드장이 두 번째 밴드 결제 | 이해할 수 있는 안내 | B4 | ⬜ |
| 구독 중인 밴드 삭제 시도 | 차단 + 해지 안내 | B5 | ⬜ |
| 쿠폰 PREMIUM 중 결제 / 결제 중 쿠폰 | 안내대로 동작, 결제 중 쿠폰이면 Play 구독 화면의 다음 결제일이 쿠폰 일수만큼 밀림 | B7 | ⬜ |
| 밴드 두 개를 각각 결제 | 두 밴드 모두 PREMIUM, 여섯 번째는 안내 | B4 | ⬜ |
| 환불 + 사용 권한 취소 | 즉시 FREE | B9 | ✅ 2026-09-09 확인 |

**콘솔·서버 (앱 설치 없이)**

- [ ] 저장소 폴더에서 `sh delete_branches.sh` — 합쳐진 원격 브랜치 59개 정리(커밋을 살린 2개 포함). 그 뒤 새로 생긴 작업 브랜치는 GitHub 브랜치 화면에서 지운다 (G2, 스크립트는 2026-09-29 대화로 전달)
- [ ] 참석 버튼·납부 체크를 누르면 **바로** 바뀌는지, 비행기 모드에서 누르면 되돌아가며 안내가 뜨는지 (G2 에서 살린 코드)

- [x] Play Console › 사용자 및 권한 › 결제 검증 서비스 계정에 **"주문 및 구독 관리"** 권한 — 2026-09-29 있음 — 없으면 결제 중 쿠폰이 "스토어가 거절" 로 실패 (B7)
- [x] `python tools/moderate.py reports` 가 표를 출력 — 서버 접속 경로 확인, 읽기만 한다 (P7) — 2026-09-29
- [ ] 2026-10-06 이후 테스트 계정 하나를 `suspend --days 1` → 로그인 화면 안내 확인 → `unsuspend` (P7, 선택)

**제출 양식**

- [ ] 심사자용 이메일 계정 — 밴드장, 일정·정산·사진 데이터, 앱 액세스 권한 안내문
- [x] 스토어 설명 반영 (P3), 방침 L1·L4 반영 후 사이트 재배포, 연락처 통일 (L2) — 2026-09-29
- [x] 카테고리 (P8 ✅ 2026-09-27), 데이터 안전 답안 "댓글" (P9 — 콘솔 답엔 없었음, 문서만 정정 2026-09-29)
- [ ] 스크린샷 2장 이상 (`store-listing.md` 순서)

---

## 8. 점검 범위와 한계

- 코드 읽기로 한 점검이다. 운영 서버·Play Console·카카오 콘솔은 보지 않았다.
- 백엔드는 GitHub main(`982e528`), 앱·문서·설정은 PC 의 현재 파일을 읽었다. 폴더가 깊어 가져오지 못한
  로컬 미커밋 백엔드 파일 25개는 점검 1~3차 문서로 대신했다.
- 게시판 동시성은 재현 테스트를 하지 않았다. B2 의 "Android 는 미처리 구매를 다시 보내지 않는다" 와
  U2 는 동작 원리로 판단한 것이라 §7 시나리오로 실측한다.

**출처** — [Target API 요건](https://support.google.com/googleplay/android-developer/answer/11926878) ·
[Billing Library 지원 중단 일정](https://developer.android.com/google/play/billing/deprecation-faq) ·
[in_app_purchase_android 변경 기록](https://pub.dev/packages/in_app_purchase_android/changelog) ·
[개인 개발자 계정 테스트 요건](https://support.google.com/googleplay/android-developer/answer/14151465) ·
[아동 안전 표준 정책](https://support.google.com/googleplay/android-developer/answer/14747720) ·
[16KB 페이지 크기](https://developer.android.com/guide/practices/page-sizes)
