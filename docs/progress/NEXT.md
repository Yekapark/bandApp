# 다음에 이어서 할 일

> 살아있는 문서. 끝난 항목은 지우고, 새로 생긴 건 여기에 적는다.
> 오늘까지의 작업 내용은 [2026-09-05-brand-notifications-settlement-video.md](2026-09-05-brand-notifications-settlement-video.md)
> 와 [2026-09-05-plan-lifecycle-and-media-fix.md](2026-09-05-plan-lifecycle-and-media-fix.md).
> 마지막 갱신: **2026-09-05**

---

## 0. 지금 상태 (이어받을 때 먼저 볼 것)

| | |
|---|---|
| 브랜치 | `feat/kakao-map-in-room-form` — `main` 대비 커밋 20개, 워킹트리 깨끗 |
| PR | **아직 안 올렸다.** 브랜치가 길어졌으니 정리해서 올릴지 결정 필요 |
| 백엔드 | 최신 코드로 재빌드해 둠(V11 마이그레이션·정산 목록 API 포함). `docker compose ps` 로 healthy 확인 |
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
- [ ] 게시글 영상 재생 — 전체화면, 탭 play/pause, 진행바 스크러빙
- [ ] 합주실 등록 폼 지도 — 검색 후보가 핀으로 뜨고 고른 좌표가 저장되는지

### 1-C. 카카오 로그인 keyHash

마지막으로 본 상태는 `Android keyHash validation failed` 였다. 값은 확인된 게 있다:

```
패키지명   com.example.bandapp_client
키 해시    ahCJ5a5dXyiPh3x9ksny6yMbjzk=
```

폰에 설치된 APK 의 서명 인증서에서 직접 뽑아 대조한 값이라 확실하다. 그래도 거부되면
**앱이 스스로 찍는 값**을 쓴다 — 디버그 빌드는 시작할 때 로그에
`kakao keyHash (콘솔에 등록할 값): ...` 을 남긴다. 콘솔에서 확인할 것:
① 지금 보고 있는 앱의 네이티브 키가 `dart_defines.json` 의 것과 같은지,
② 입력 후 **저장 버튼**을 눌렀는지.

---

## 2. 우선순위가 높은 남은 작업

### 2-A. 출시 전 필수

- **패키지명이 `com.example.bandapp_client`** — 구글 플레이가 `com.example.` 로 시작하는
  패키지를 거부한다. 바꾸면 **카카오 콘솔 플랫폼 등록(패키지명·키 해시)도 다시** 해야 한다.
  이름이 밴듈로 정해졌으니 지금이 바꿀 타이밍이다.
- **릴리스 서명 설정 없음** — `build.gradle.kts` 가 디버그 키로 서명 중(`TODO` 주석 그대로).
- **ProGuard 가 꺼져 있다** — `isMinifyEnabled` 미설정. 카카오맵 규칙은 미리 넣어 뒀지만
  아직 동작하지 않는다. 켤 때 릴리스 빌드로 지도·로그인을 다시 확인해야 한다.
- 릴리스 키스토어의 **키 해시도 카카오 콘솔에 추가**해야 한다(디버그 것과 다르다).

### 2-B. 신고 레이트리밋 테스트 안정화

`ReportIntegrationTest.reports_are_rate_limited_per_user()` 가 전체 실행에서 간헐적으로 실패한다.
상세는 [2026-09-05 문서 §9-A](2026-09-05-brand-notifications-settlement-video.md).
(별도 세션에서 진행 중)

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
cd client && flutter test && flutter analyze lib/
cd C:\band\bandApp && ./gradlew test
```

- 저장(쓰기)을 하는 화면을 만들면 **반드시 관련 프로바이더를 `ref.invalidate`** 한다.
  이 앱의 provider 는 autoDispose 가 아니라, 안 비우면 화면을 벗어났다 오는 순간
  옛 값이 다시 그려진다(오늘 4곳에서 났다). 위 검사 스크립트가 잡아 준다.
- 아이콘을 바꾸려면 `client/brand/*.svg` 를 고치고
  `cd client && python tools/render_icons.py`.
