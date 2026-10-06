# 프로덕션 액세스 신청 — 답변 초안

> 마지막 사실 대조: 2026-10-06 (QALIST, QA_CHECKLIST §37~§42). 실기기 증거는 +32~+38 누적이며 +38 지정 회귀5건을 S20 Play 설치본에서 확인했다.

> 개인 개발자 계정은 **테스터 12명 이상이 14일 연속 참여한 비공개 테스트** 뒤에 프로덕션을 신청한다
> (LAUNCH_REVIEW P1). 신청서는 Play Console › 대시보드 › "프로덕션 신청" 에 있고, 심사는 보통 7일 이내다.
> 이 문서는 그 신청서의 답을 미리 써 둔 것이다. `⟨⟩` 는 신청 직전에 사실대로 채운다.
>
> **답의 근거는 테스터 의견과 실기기 QA 기록(`docs/QA_CHECKLIST.md`)·출시 전 점검(`docs/LAUNCH_REVIEW.md`)이다.**
> 신청서 답변은 Google 에 내는 진술이라 **있었던 일만 쓴다** — 테스터 사용량·의견·검증 범위를 부풀리지 않는다.
> 사실과 다른 답은 거절 사유가 되고 개발자 계정 정책(허위 정보 제공)에도 걸릴 수 있다.
> - "고쳤다"(PR 머지·배포)와 "실기기에서 확인했다"(QA 결과 ✅/🟡)를 구분해 쓴다. 빌드·머지만 된 것을 확인했다고 쓰지 않는다.
> - 숫자는 **QALIST의 고유 ID 집계**를 쓴다. 실기기 누적 증거와 🤖/👤 확인 예외를 포함한 전체 집계임을 밝히며, 최신 빌드 하나의 실기기 실행 수로 표현하지 않는다.
>
> 질문 출처: [비공개 테스트 요건](https://support.google.com/googleplay/android-developer/answer/14151465) (2026-09-30 확인).

### 2026-10-06 기준 사실 (근거)

- 빌드: Play +35~+37(Samsung S20/S24)의 실기기 증거가 있다(QA_CHECKLIST §23~§38). **+38은 S20에서 versionCode38·Play 설치자를 실제 확인**했고 지정 회귀5건을 진행했다. Play Console 처리/승인 상태는 이 세션에서 조회하지 않았다.
  U40~U44는 #205 수정 후 +38 지정 회귀를 통과했다(§41). 관련 QA ID 전체 완료와 구분한다.
- QA 집계(QALIST, §39~§65 반영): 고유 ID **208개** 중 일부라도 진행 **178개(85.6%)**, 모든 명시 조건 확인 **118개(56.7%)**.
  상태 ✅118·🟡57·❌3·⬜30. **실기기 +32~+39 누적 결과·OPS-08 문서 대조와 🤖 클로드 38개·👤 사용자 1개 확인 예외를 포함한다.** 118개를 +39 실기기 통과 수로 쓰지 않는다. 전체 QA 완료가 아니다.
- +35~+37 누적 실기기에서 확인된 것: 푸시 실제 수신·클릭 이동, 앱 오류 기록(Crashlytics) 수신, 약관·개인정보처리방침 링크,
  영상 전체 화면 가로 회전, 합주실 지도 핀, 앱 종료 상태에서 초대 링크(로그인 전후 코드 유지), 오프라인 로그아웃,
  Play 테스트 카드 결제 승인·거절, 결제자가 밴드를 나가면 서버 구독 자동 해지, 5분 영상 압축·업로드, 정산 밴드장 확인.
- 추가로 확인된 것: 일회용 이메일 계정 실제 탈퇴·옛 토큰 차단·밴드 이탈·빈 밴드 삭제(§31, PRIV-03 🤖), 인증메일 실제 수신·재발송·코드 검증(API, AUTH-06), 업로드 강제 종료 뒤 같은 글에서 재첨부 성공(§37), 4인 정산 변경·납부 보존·재분배·탭 합계(§38).앱 인증 배너 제거·모든 실패 재시도 조합까지 완료했다는 뜻은 아니다.
- **아직 확인 안 된 것**: 카카오톡 미설치 로그인·카카오 연결 해제 실제 탈퇴, 인증 폼 오류/배너 제거, 결제 보류 화면 최신화·재구독·실제 환불·쿠폰·여러 밴드 구독·검증 전 종료/웹훅 승급,
  Play 쪽 해지 표시·추방 푸시, 신고 처리·이용 정지 앱 표시, 업로드 통신 전환/취소·지연 응답 경합, 태블릿·S25+ 기기.
  **+38 지정 회귀 통과 5건:** QA-R07 초대코드 갱신, R08 정산 뒤로 복귀 시 옛 값, R09 다른 밴드 알림 뒤 저장/상세 이동, R10 나간 밴드 OS푸시, R11 밴드 5개 이상 전환 스크롤.연결 QA ID 중 BAND-01/BAND-06은 §42에서 모든 조건 완료, UI-06은 지연응답 시험환경 대기 🟡, PUSH-07은 앱내 탈퇴알림 결함 QA-R12/U45로 ❌이다. §43 실제 유예·보류·카드복구 서버 정상 및 BILL-30 전체 통과. BILL-10은 열린/재진입 요금제의 옛 PREMIUM 표시 QA-R13/U46로 ❌. 시험75/76 정상삭제·구독해지·원래68 복원. §48 +39에서 QA-R12/U45 재검증 통과(PUSH-07은 실행 불가4조건으로 🟡). QA-R13/U46은 결제 재현 대기, §49 CAL-11 QA-R17/U50과 §53~§54 CAL-16 QA-R21/U54가 전체 ❌. 그 밖의 수정 설치본 대기 결함은 QALIST 현재 결함표에서 관리한다. §55~§56 CAL-09·CAL-19 기존 +39 명시 조건/정리 대조 후 ✅. §58~§60 S20 Play+39 PUSH-15/BAND-18/MEDIA-08 전조건 ✅·사진/피드11분 자연만료 복구 및 요청안정 확인. 영상은 기존 §20 +33 증거. 시험88/89·사진3객체삭제·기타/67/설정복원. §63~§65 PUSH-20/BAND-15/CAL-15 전조건 완료·91/92삭제·권한/기타67복원.
- 테스터 의견 U13(갤럭시 S25+ 키보드·앱 종료): 테스터가 해결됐다고 알려 왔으나 **겨냥한 수정은 없고 원인 미확정**,
  S25+ 재확인 미실행(LAUNCH_REVIEW U13, QA UI-09).
- OPS-12는 §31 실제 공개 HTTP 확인으로 ✅이다. 이 문서 대조에서 운영 서버를 새로 조회한 것은 아니다.

---

## 신청 전에 확인할 것

- [ ] 테스터 **12명 이상이 14일 연속** 참여 중인가 — 대시보드의 "현재 참여를 선택한 테스터" 숫자.
      **중간에 참여를 취소한 사람은 세지 않고, 다시 들어오면 그 사람의 14일이 처음부터다.** 테스터에게 "앱을
      지우거나 테스트에서 나가지 말아 달라" 고 미리 말해 둔다. (2026-09-30 12명 참여 → 14일은 10-14 전후)
- [ ] 비공개 테스트 트랙의 최신 빌드 번호 ⟨신청 때 콘솔·설치본으로 확인⟩와 **QALIST 최신 집계**를 확인했는가. 빌드별 증거는 QA_CHECKLIST에서 확인한다.
      답변의 "확인한 것 / 남은 것" 을 그 집계에 맞춰 고친다. 결제는 라이선스 테스트 계정으로 직접 한다.
- [ ] 테스터 몇 명에게라도 최신 빌드로 업데이트해 한두 번 써 봐 달라고 부탁했는가 — 필수는 아니지만, 받은 의견이
      한 건이라도 있으면 Part 1-3 에 "있었던 일" 로 쓸 수 있다. 없으면 없다고 쓴다.
- [ ] 심사자 계정(앱 콘텐츠 › 로그인 세부정보, 2026-09-10 입력)이 **지금도 로그인되고, 밴드에 속해 있고,
      일정·게시글이 있는지.** 심사자는 카카오 로그인을 못 쓴다.
- [ ] 프로덕션 액세스가 열리면 **출시 국가는 대한민국만** — 약관·개인정보처리방침·통신판매업 신고가 한국 기준이다
      (비공개 테스트 트랙도 국가 1개). 프로덕션 트랙은 액세스 전에는 잠겨 있어 미리 못 정한다(2026-09-30 확인).

### 숫자 채우는 법

| 자리 | 어디서 | 2026-10-06 값 |
|---|---|---|
| ⟨테스터 수⟩·⟨참여 일수⟩ | Play Console 대시보드 "현재 참여를 선택한 테스터"·비공개 테스트 시작일 | 12명(09-30)·— |
| ⟨빌드⟩ | 신청 직전 콘솔·설치본 실측 | +39 S20 Play 설치 확인(§58~§60), 신청 직전 재확인 |
| ⟨시나리오 수⟩·⟨실행 수⟩·⟨통과 수⟩ | `docs/QALIST.md` 진행 현황 | 208 · 178 · 118 (누적·🤖/👤 예외 포함) |
| ⟨미확인 항목⟩ | 같은 집계의 ⬜·🟡 중 위 "아직 확인 안 된 것" | 위 목록 |

## 의견 모으기

테스터 단톡방이 있으면 거기서, 없으면 구글 폼 하나로 받는다. 질문은 짧게:

1. 써 본 기능 (일정 등록 / 참석 응답 / 정산 / 게시판 사진·영상 / 합주실 지도 / 초대 / 요금제)
2. 불편했거나 안 된 것
3. 있었으면 하는 것
4. 계속 쓸 생각이 있는지 (예 / 아니요 / 모르겠음)

받은 의견은 Part 1-3 에 **받은 그대로** 적는다. 개발자가 직접 점검한 내용(Part 3)으로 보강한다.

### 받은 의견 (받는 대로 추가)

| 날짜 | 기기 | 의견(받은 그대로) | 처리 |
|---|---|---|---|
| 2026-09-30 | — | 밴드에 들어가 있으면 새 밴드를 만들 방법이 없다 | 밴드 전환 화면에 "새 밴드 만들기" 추가 (LAUNCH_REVIEW U12, +32) |
| 2026-09-30 | 갤럭시 S25+ | "밴드 이름 만들기 누르면 키패드가 안 뜨고 반복해서 누르면 앱이 튕겨요. 가입하기의 초대코드 입력란도 똑같아요" | 2026-10-02 테스터가 해결됐다고 알려 옴. 겨냥한 수정 없음·원인 미확정, S25+ 재확인 전 (LAUNCH_REVIEW U13) |

---

## Part 1 — 비공개 테스트

**1. 테스터를 얼마나 쉽게 모집했나요?** (보기 중 선택)

⟨실제대로 고른다 — 지인에게 부탁해 모았다⟩

**2. 테스터가 앱의 모든 기능을 사용했나요? / 사용 방식이 실제 사용자와 비슷했나요?**

보기는 실제대로 고른다 — 테스터 사용량이 적었으면 "모든 기능" 을 고르지 않는다. 설명 칸:

> 테스터 ⟨테스터 수⟩명은 밴드를 만들고 초대코드로 가입하는 흐름으로 써 보고 문제를 알려 줬습니다(3번).
> 테스터 사용량이 많지 않아, 개발자가 Play 에서 설치한 테스트 빌드 ⟨빌드⟩로 실기기 시나리오 점검을 따로 했습니다.
> 누적 QA 항목 ⟨시나리오 수⟩개 중 ⟨실행 수⟩개를 일부라도 진행했고 ⟨통과 수⟩개는 모든 명시 조건을 확인했습니다. 이 수치는 여러 빌드의 실기기 결과와 실제 DB 통합 테스트·운영 실행·사용자 확인 예외를 포함하며, 최신 빌드만의 실기기 통과 수가 아닙니다. 확인한 것: 이메일 로그인·로그아웃,
> 카카오 로그인(카카오톡 설치 기기), 앱 종료 상태에서 초대 링크로 가입, 일정 등록·승인, 비용 정산, 사진·5분 영상 업로드와 재생,
> 합주실 지도, 푸시 알림 수신과 이동, 테스트 카드 구독 결제(승인·거절). 아직 확인하지 않은 것: ⟨미확인 항목 — 예: 카카오 연결 해제 탈퇴,
> 결제 보류·복구·쿠폰, 신고 처리⟩. 출시 전에 이어서 점검합니다.

(영문)
> ⟨tester count⟩ testers used the app through the real flow — creating a band and joining with an invite code — and
> reported problems (see question 3). Because tester usage was limited, the developer also ran a scenario checklist on
> real devices with test build ⟨build⟩ installed from Play. Across builds, ⟨executed⟩ of ⟨scenario count⟩ QA items were at least partly exercised and
> ⟨passed⟩ had every stated condition verified. These cumulative counts include real-device evidence, actual-database integration tests, operational checks and user-confirmed exceptions; they are not real-device pass counts for the latest build alone. Verified: email login/logout, Kakao login (with KakaoTalk installed), joining via an
> invite link from a closed app, creating and approving rehearsals, cost splitting, photo and 5-minute video upload and
> playback, the studio map, receiving and opening push notifications, and a test-card subscription purchase (approved
> and declined). Not yet verified: ⟨remaining items — e.g. Kakao unlink during account deletion, billing hold/recovery/coupons,
> report handling⟩. We are continuing these checks before launch.

**3. 받은 의견과 모은 방법**

위 "받은 의견" 표에서 채운다:

> ⟨방법: 단톡방 / 직접 대화⟩로 테스터 의견을 받았습니다. "이미 밴드에 들어가 있으면 새 밴드를 만들 수 없다" 는 의견으로
> 밴드 전환 화면에 밴드 만들기를 추가했습니다. 갤럭시 S25+ 에서 입력칸에 키보드가 뜨지 않고 앱이 꺼진다는 의견도 받았는데,
> 이후 같은 테스터가 해결됐다고 알려 왔습니다. 원인을 확정하지 못해 앱 오류 기록을 켜 두고 재발을 지켜보고 있습니다.
> ⟨그 밖의 의견⟩. 이와 별도로 개발자가 출시 전 점검과 실기기 점검에서 찾은 문제를 고쳤습니다(Part 3).

(영문)
> We collected tester feedback through ⟨group chat / direct conversation⟩. A tester pointed out that there was no way
> to create a new band once you had joined one, so we added "Create a band" to the band switcher. Another reported that
> the keyboard did not appear in text fields and the app closed on a Galaxy S25+; the same tester later told us it no
> longer happens. We could not confirm the cause, so crash reporting is enabled and we are watching for a recurrence.
> ⟨other feedback⟩. Separately, the developer fixed issues found in a pre-launch review and real-device testing (Part 3).

---

## Part 2 — 앱 소개

**1. 타겟층**

> 한국에서 활동하는 아마추어·직장인·대학 밴드(만 14세 이상). 밴드 단위로 합주 일정과 비용을 관리하는 사람들.

(영문) Amateur, hobby and college bands in South Korea (ages 14+) who manage rehearsal schedules and costs as a group.

**2. 가치 제안**

> 합주 일정은 보통 단톡방에서 잡고, 합주실 비용은 누군가 대신 내고 계좌이체로 나눕니다. 밴듈은 밴드 단위로
> 합주 일정·참석 여부·합주실 정보·비용 정산·합주 사진/영상을 한 곳에 모아, 누가 오는지·누가 아직 안 냈는지·
> 다음 합주가 언제인지를 단톡방을 뒤지지 않고 볼 수 있게 합니다. 기본 기능은 무료이고, 사진·영상 무제한 보관과
> 정기 합주 자동 등록은 밴드 단위 연간 구독입니다.

(영문)
> Bands usually schedule rehearsals in group chats and split studio costs by bank transfer. Bandule keeps a band's
> rehearsal schedule, attendance, studio info, cost splitting and rehearsal photos/videos in one place, so members can
> see who is coming, who hasn't paid yet and when the next rehearsal is without scrolling through chat history. Core
> features are free; unlimited media storage and recurring rehearsals are a per-band annual subscription.

**3. 첫해 예상 설치 수** (보기 중 선택)

⟨가장 낮은 구간 — 입소문으로 시작하는 개인 서비스라 부풀리지 않는다⟩

---

## Part 3 — 출시 준비

**1. 비공개 테스트에서 알게 된 것으로 바꾼 점**

비공개 테스트 기간(2026-09-27~)의 출시 전 점검(`docs/LAUNCH_REVIEW.md`)과 실기기 QA(`docs/QA_CHECKLIST.md`)에서 찾아 고친 것.
모두 실제로 고친 것이고(근거 PR 은 LAUNCH_REVIEW 각 행), **실기기 확인 여부를 나눠 적었다.** 신청 직전 QA 최신 결과로
"확인 남음" 항목을 옮기거나 그대로 둔다. 테스터 의견으로 고친 것을 맨 앞에 둔다.

> 고치고 실기기(+32~+37 누적)에서 확인한 것:
> - (테스터 의견) 이미 밴드에 속해 있어도 새 밴드를 만들 수 있게 했습니다.
> - 릴리스 빌드에서 푸시 알림과 앱 오류 기록이 꺼지던 문제를 고쳤습니다(실제 푸시 수신·오류 수신 확인).
> - 실제 확인한 일정 알림은 상세로 이동합니다. 나간 밴드 OS알림 실행중/콜드스타트와 밴드 전환 뒤 일정저장 상세도 +38에서 확인했습니다.
> - 앱이 꺼진 상태에서 초대 링크를 열어도, 로그인 후까지 초대코드가 유지됩니다.
> - 가입 동의 화면·로그인 화면에서 약관·개인정보처리방침 전문을 열 수 있게 했습니다.
> - 전체 화면 영상을 가로로 돌려도 영상과 재생 막대가 잘리지 않습니다.
> - 합주실 지도에서 핀이 가장자리에 잘리지 않습니다.
> - 결제한 멤버가 밴드를 나가면 자동 결제를 해지합니다(서버 상태 확인, Play 화면 표시는 확인 남음).
> - 밴드장이 현금으로 받은 몫을 대신 "냈음" 처리할 수 있게 했습니다.
> - 영상 압축이 멈추던 문제를 압축 방식 교체로 고쳤습니다(5분 영상 압축·업로드 확인).
>
> 별도 누적 확인: 이메일 계정 탈퇴·옛 토큰 차단과 업로드 종료 후 같은 글 재첨부를 실제 확인했습니다. 카카오 연결 해제·모든 실패 조합은 남았습니다.
>
> 현재 미해결: +38에서 탈퇴한 밴드의 앱내 알림을 누르면 안내 대신 오류 상세를 여는 QA-R12/U45가 발견됐습니다. 수정·설치본 재검증이 필요합니다.
>
> 고쳤지만 실기기 확인이 남은 것: ⟨신청 직전 QA 결과로 정리⟩
> - 결제 직후 앱이 꺼지거나 네트워크가 끊겨도 결제가 해당 밴드에 반영되게 했습니다.
> - 한 사람이 밴드 여러 개를 각각 구독할 수 있게 했습니다.
> - 결제 보류 중인 밴드를 FREE 로 보여 두 번 결제되던 경로를 막았습니다.
> - 쿠폰 기간과 결제 기간이 겹치면 사라지던 기간을 이어 붙이게 했습니다.
> - +32에서 로그아웃 뒤 이전 계정의 푸시가 오지 않음을 확인했습니다. 토큰 회전·재설치는 별도 조건이 남았습니다.
> - 신고 처리와 계정 이용 정지 절차를 만들었습니다. 서버/운영 확인과 앱 표시 확인 범위는 QALIST 해당 ID대로 구분합니다.
> - 휴대폰 가로 화면 일부는 확인했습니다. 태블릿·폴더블·전체 SafeArea는 남았습니다.

(영문)
> Fixed and verified on real devices (cumulative evidence from builds +32 to +37):
> - (Tester feedback) You can now create a new band even if you already belong to one.
> - Fixed push notifications and crash reporting being disabled in release builds (push delivery and crash reports confirmed).
> - Tested rehearsal notifications open their details. QA-R09/R10 targeted paths also passed on +38, including running-app and cold-start notifications for a band already left.
> - Invite links opened while the app is closed keep the invite code through login.
> - The full Terms of Service and Privacy Policy can be opened from the consent and login screens.
> - Full-screen video no longer cuts off the video or controls in landscape.
> - Studio map pins are no longer clipped at the edge.
> - When the paying member leaves a band, auto-renewal is cancelled (confirmed on our server; the Play-side display is not yet checked).
> - Band leaders can mark a member's share as paid when they received cash.
> - Replaced the video compressor that could hang (5-minute video compression and upload confirmed).
>
> Additional cumulative evidence verifies email-account deletion, old-token rejection and reattaching to the same post after upload termination. Kakao unlink and other failure combinations remain.
>
> Unresolved: QA-R12/U45 on +38 opens an error detail from an in-app notification for a band already left. A fix and installed-build verification are still required.
>
> Fixed, real-device verification still pending: ⟨update from the latest QA before submitting⟩
> - Purchases are credited to the right band even if the app closes or goes offline right after paying.
> - One user can subscribe separately for several bands.
> - A band whose payment is on hold no longer looks free, preventing a double charge.
> - Coupon periods and paid periods now stack instead of overwriting each other.
> - Build +32 verified that the previous account no longer receives pushes after logout. Token rotation and reinstall checks remain.
> - Added reporting, moderation and suspension procedures. Backend/operational evidence and remaining app-display checks are separated in QALIST.
> - Some phone landscape layouts were verified. Tablet, foldable and full SafeArea checks remain.

**2. 프로덕션 준비가 됐다고 판단한 근거**

> ⟨참여 일수⟩일간의 비공개 테스트에서 테스터 의견을 받았고, 출시 전 점검에서 찾은 문제를 고쳐 1.0.0(⟨빌드⟩)으로 다시 배포했습니다.
> 여러 빌드의 실기기와 서버/운영 확인을 포함한 누적 QA ⟨시나리오 수⟩개 중 ⟨실행 수⟩개를 일부라도 진행했고 ⟨통과 수⟩개는 명시 조건을 모두 확인했습니다. 핵심 흐름(로그인·초대 가입·일정·정산·사진/영상·푸시·
> 테스트 결제)은 동작을 확인했습니다. QA-R07~R11은 +38 지정 회귀를 통과했고, ⟨미확인 항목⟩을 확인한 뒤 출시 여부를 판단합니다. 아직 이 답변을 제출할 준비가 완료됐다는 뜻은 아닙니다. 서버는 자동 테스트·배포와 매일 상태 점검
> (백업·디스크·오류·인증서)을 돌리고 있고, 결제는 Play 결제 알림으로 갱신·해지·환불을 반영하도록 만들었습니다.
> 개인정보처리방침·이용약관·계정 삭제 안내를 웹에 게시했고, 앱 안에 신고·차단과 운영자 조치 절차가 있습니다.

(영문)
> During a ⟨days⟩-day closed test we collected tester feedback, fixed the issues found in a pre-launch review, and
> redeployed as 1.0.0 (⟨build⟩). Across builds and real-device/backend/operational checks, we at least partly exercised ⟨executed⟩ of ⟨scenario count⟩ QA items; ⟨passed⟩ had all stated conditions verified. We
> confirmed the core flows work: login, joining by invite, rehearsals, cost splitting, photos/videos, push
> notifications and a test subscription purchase. QA-R07 through QA-R11 passed targeted checks on Play-installed +38; we will decide launch readiness after checking ⟨remaining items⟩. This draft is not yet ready for submission.
> The backend runs automated tests and deployment plus a daily health check (backups, disk, errors, TLS certificate),
> and subscriptions are designed to stay in sync through Play real-time developer notifications (renewals,
> cancellations, refunds). The privacy policy, terms of service and account-deletion page are published on the web,
> and the app has in-app reporting, blocking and an operator moderation process.

### +38 지정 회귀 결과 — 2026-10-06 §41

> U40~U44는 S20 Play +38에서 지정 회귀를 확인했습니다: 다른 초대 링크의 코드 갱신, 변경 정산 상세에서 뒤로 복귀, 다른 밴드 알림 후 원래 밴드에 저장·상세 이동, 탈퇴한 밴드 알림(실행 중·콜드 스타트), 5개 이상 밴드의 마지막 밴드·초대 버튼 스크롤. 저장 뒤 저장한 A 밴드로 돌아오는 것은 의도된 동작입니다. QA-F01은 계정 교체·나감/추방 fallback, QA-F02는 코드 갱신·재로그인 등 남은 조건이 있습니다. QA-F03 지도 전체 핀은 +35 확인 완료, U13은 테스터 해결 제보로 종료됐으며 원인은 미확정입니다.

(영문)
> U40–U44 passed their targeted regression checks on an S20 running Play-installed +38: updating the code after a different invite link, returning to an existing settlement detail, saving and opening details after another band's notification, opening notifications for a band already left (running app and cold start), and scrolling to the last band and invite button with at least five bands. Returning to saved band A after saving is intentional. QA-F01 still needs account-change and left/kicked-band fallback checks; QA-F02 needs code-update and re-login paths. QA-F03's full-map pins were verified on +35. U13 was closed following the tester's report of resolution, with its cause unconfirmed.
