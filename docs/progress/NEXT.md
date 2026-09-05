# 다음에 이어서 할 일

> 살아있는 문서. 끝난 항목은 지우고, 새로 생긴 건 여기에 적는다.
> 오늘까지의 작업 내용은 [2026-09-05-brand-notifications-settlement-video.md](2026-09-05-brand-notifications-settlement-video.md)
> , [2026-09-05-plan-lifecycle-and-media-fix.md](2026-09-05-plan-lifecycle-and-media-fix.md),
> [2026-09-05-band-delete.md](2026-09-05-band-delete.md), [phase-11-deploy.md](phase-11-deploy.md).
> 마지막 갱신: **2026-09-06**

---

## 0. 지금 상태 (이어받을 때 먼저 볼 것)

| | |
|---|---|
| 백엔드 | **Phase 0~11 전부 완료.** PR 스택(#39~#41)은 머지 순서가 엇갈려 ②③이 `main` 에 안 들어갔었고, [#42](https://github.com/Yekapark/bandApp/pull/42) 로 바로잡았다 |
| 진행 중 | `phase-11-deploy` — 운영 compose·Nginx·Let's Encrypt·GitHub Actions 배포·DB 백업/복구. 배포 방법은 [docs/DEPLOY.md](../DEPLOY.md) |
| 마이그레이션 | `V13` 까지. **`docker compose up -d --build app` 으로 다시 띄워야** 반영된다 |
| 남은 것 | **출시까지 순서는 [docs/LAUNCH_CHECKLIST.md](../LAUNCH_CHECKLIST.md) 를 본다** (도메인 → 서버 → 배포 → 카카오 콘솔 → 약관 → 서명 → 스토어). 이 문서는 코드로 남은 것만 다룬다 |
| 실기기 | 갤럭시 S24(`R3CX40J7QJE`) USB 테더링 + `adb reverse tcp:8080` |

실행:

```powershell
cd C:\band\bandApp; docker compose up -d
& "$env:LOCALAPPDATA\Android\sdk\platform-tools\adb.exe" -s R3CX40J7QJE reverse tcp:8080 tcp:8080
cd C:\band\bandApp\client; flutter run -d R3CX40J7QJE --dart-define-from-file=dart_defines.json
```

> `adb reverse` 는 USB 를 뽑으면 사라진다. 백엔드 **코드**를 고쳤으면
> `docker compose up -d --build app` (그냥 `up -d` 는 옛 이미지를 그대로 쓴다).

---

## 1. 못 끝낸 것

### 1-A. "BOTTOM OVERFLOWED BY 63 PIXELS" — 원인 미확인 ❗

제보만 받고 **끝내 재현하지 못했다.** 어느 화면인지 특정하지 못한 채 남아 있다.

- 시도한 것: logcat 에서 `RenderFlex overflowed` 를 찾았으나 버퍼가 이미 지나갔고,
  실시간 감시를 걸어 뒀지만 그 사이 재현되지 않아 타임아웃으로 끝났다.
- 기능에는 영향이 없고 **디버그 빌드에서만** 노란 줄무늬로 보인다(릴리스에서는 안 보임).
  다만 레이아웃이 잘리는 건 맞으니 고쳐야 한다.

**다음에 할 것** — 앱을 켜 두고 아래를 돌린 뒤, 화면을 돌아다니다 줄무늬가 뜨면
위젯·파일·줄번호가 그대로 찍힌다:

```powershell
& "$env:LOCALAPPDATA\Android\sdk\platform-tools\adb.exe" -s R3CX40J7QJE logcat -c
& "$env:LOCALAPPDATA\Android\sdk\platform-tools\adb.exe" -s R3CX40J7QJE logcat | Select-String -Pattern "overflowed|RenderFlex"
```

짐작 가는 후보(확인 안 함): 키보드가 올라올 때의 폼 화면, 바텀시트, 다이얼로그.
어느 화면이었는지 기억나면 그것부터 열어 보는 게 빠르다.

### 1-B. 실기기 end-to-end 미검증

빌드·테스트는 통과했지만 **실제 기기에서 눈으로 확인한 것은 아니다.**

- [ ] 앱 아이콘(런처에서 Stick Check), 스플래시·로그인의 브랜드 마크와 `BANDULE` 워드마크
- [ ] 정산 탭 — 납부 체크 후 **다른 화면 갔다 와도 유지되는지**(오늘 고친 버그)
- [ ] 알림 목록 — 홈 종 배지 숫자, 목록 열면 배지가 0 이 되는지
- [ ] 영상 첨부 — 5~6분 영상으로 압축 진행률(%)이 돌고, 등록 시 함께 올라가는지
      (**이게 그동안 안 됐다** — DB 제약이 50MB 에 머물러 있어 압축한 영상도 500 이 났다.
      `V12` 에서 200MB 로 올렸으니 이제 실제로 확인이 된다)
- [ ] 사진 첨부 — 고화질 사진을 올린 뒤 저장 크기가 1MB 아래인지(긴 변 2048px 로 축소한다)
- [ ] 요금제 쿠폰 — 쿠폰을 SQL 로 넣고 앱에서 입력 → PREMIUM 전환·기간 가산
- [ ] 밴드 삭제 — 사진·영상·일정·정산이 있는 테스트 밴드를 지우고 R2 버킷과 각 테이블이 비는지
- [ ] 게시글 영상 재생 — 전체화면, 탭 play/pause, 진행바 스크러빙
- [ ] 합주실 등록 폼 지도 — 검색 후보가 핀으로 뜨고 고른 좌표가 저장되는지

### 1-C. 카카오 로그인 keyHash

마지막으로 본 상태는 `Android keyHash validation failed` 였다. 값은 확인된 게 있다:

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
- **릴리스 서명 설정 없음** — `build.gradle.kts` 의 `release` 블록이 아직 디버그 키로 서명한다.
- **ProGuard 가 꺼져 있다** — `isMinifyEnabled` 미설정. 카카오맵 규칙은 미리 넣어 뒀지만
  아직 동작하지 않는다. 켤 때 릴리스 빌드로 지도·로그인을 다시 확인해야 한다.
- 릴리스 키스토어의 **키 해시도 카카오 콘솔에 추가**해야 한다(디버그 것과 다르다).

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
| 네이버 로그인 | 버튼만 있고 "준비 중" 스낵바 |
| 밴드 장르·파트 | "추후 지원 예정" 안내문만 |
| 약관 동의 기록 | 클라 게이트만, 백엔드 없음 |

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
