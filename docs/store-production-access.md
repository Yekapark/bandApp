# 프로덕션 액세스 신청 — 답변 초안

> 개인 개발자 계정은 **테스터 12명 이상이 14일 연속 참여한 비공개 테스트** 뒤에 프로덕션을 신청한다
> (LAUNCH_REVIEW P1). 신청서는 Play Console › 대시보드 › "프로덕션 신청" 에 있고, 심사는 보통 7일 이내다.
> 이 문서는 그 신청서의 답을 미리 써 둔 것이다. `⟨⟩` 는 신청 직전에 사실대로 채운다.
> 질문 출처: [비공개 테스트 요건](https://support.google.com/googleplay/android-developer/answer/14151465) (2026-09-30 확인).
>
> 마지막 갱신: 2026-09-30

---

## 신청 전에 확인할 것

- [ ] 테스터 **12명 이상이 14일 연속** 참여 중인가 — 대시보드의 "현재 참여를 선택한 테스터" 숫자.
      **중간에 참여를 취소한 사람은 세지 않고, 다시 들어오면 그 사람의 14일이 처음부터다.** 테스터에게 "앱을
      지우거나 테스트에서 나가지 말아 달라" 고 미리 말해 둔다.
- [ ] 비공개 테스트 트랙에 **+32(1.0.0)** 이 올라가 있고, 테스터 몇 명이 실제로 써 봤는가 — 신청서가 "모든 기능을
      써 봤는지" 를 묻는다. 결제는 라이선스 테스터만 공짜로 할 수 있으니, 결제 시나리오는 직접 한다(LAUNCH_REVIEW §7).
- [ ] **테스터 의견을 모아 둔다** — 아래 "의견 모으기". 신청서가 "어떤 방법으로 모았는지" 를 묻는다.
- [ ] 심사자 계정(앱 콘텐츠 › 로그인 세부정보, 2026-09-10 입력)이 **지금도 로그인되고, 밴드에 속해 있고,
      일정·게시글이 있는지.** 심사자는 카카오 로그인을 못 쓴다.
- [ ] 프로덕션 액세스가 열리면 **출시 국가는 대한민국만** — 약관·개인정보처리방침·통신판매업 신고가 한국 기준이다
      (비공개 테스트 트랙도 국가 1개). 프로덕션 트랙은 액세스 전에는 잠겨 있어 미리 못 정한다(2026-09-30 확인).

## 의견 모으기

테스터 단톡방이 있으면 거기서, 없으면 구글 폼 하나로 받는다. 질문은 짧게:

1. 써 본 기능 (일정 등록 / 참석 응답 / 정산 / 게시판 사진·영상 / 합주실 지도 / 초대 / 요금제)
2. 불편했거나 안 된 것
3. 있었으면 하는 것
4. 계속 쓸 생각이 있는지 (예 / 아니요 / 모르겠음)

받은 의견과 그걸로 고친 것을 아래 Part 3 에 적는다. 의견이 적어도 괜찮다 — "받은 의견이 없다" 보다
"받은 의견 N건, 그중 이것을 고쳤다" 가 낫다.

---

## Part 1 — 비공개 테스트

**1. 테스터를 얼마나 쉽게 모집했나요?** (보기 중 선택)

⟨실제대로. 지인·밴드 동료로 모았다면 "쉬움~보통"⟩

**2. 테스터가 앱의 모든 기능을 사용했나요? / 사용 방식이 실제 사용자와 비슷했나요?**

> 테스터는 실제로 밴드 활동을 하는 사람들이라 실제 사용자와 같은 방식으로 썼습니다. 밴드를 만들고 초대 링크로
> 멤버를 모은 뒤 합주 일정 등록·참석 응답·비용 정산·게시판 사진/영상 공유를 했습니다. 유료 구독은 라이선스
> 테스트 계정으로 결제·해지·복구·쿠폰 적용까지 확인했습니다. ⟨안 써 본 기능이 있으면 솔직히 적는다⟩

(영문)
> Our testers are real amateur band members, so they used the app the way production users will: creating a band,
> inviting members by link, scheduling rehearsals, responding to attendance, splitting rehearsal costs, and sharing
> photos/videos on the band board. We tested the paid subscription with license-tester accounts, including purchase,
> cancellation, recovery and coupons.

**3. 받은 의견과 모은 방법**

> ⟨방법: 테스터 단톡방 / 구글 폼 / 직접 대화⟩로 의견을 받았습니다. 주요 의견: ⟨예: 참석 체크가 늦게 반영된다,
> 오래 열어 둔 글의 사진이 깨진다, 알림을 눌러도 해당 화면으로 안 간다⟩.

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

비공개 테스트(2026-09-27~)와 출시 전 점검(`docs/LAUNCH_REVIEW.md`)에서 찾아 고친 것. 테스터 의견으로 고친 것이
있으면 맨 앞에 둔다.

> - 결제 직후 앱이 꺼지거나 네트워크가 끊겨도, 앱을 다시 열거나 앱을 열지 않아도 결제가 해당 밴드에 반영되게 했습니다.
> - 한 사람이 밴드 여러 개를 각각 구독할 수 있게 했습니다.
> - 구독 중인 밴드를 삭제하거나 탈퇴할 때 구독 해지를 안내하고, 자동 갱신 중인 구독자에게는 만료 경고를 보내지 않습니다.
> - 쿠폰 기간과 결제 기간이 겹치면 사라지던 기간을 이어 붙이게 했습니다.
> - 로그아웃한 기기로 이전 계정의 알림이 가던 문제를 고쳤습니다.
> - 글을 오래 열어 두면 사진·영상이 깨지던 문제를 고쳤습니다.
> - 알림을 누르면 해당 일정·정산·요금제 화면으로 가게 했습니다.
> - 참석·납부 체크가 누르는 즉시 반영되게 했습니다.
> - 영상 압축이 특정 영상에서 멈추던 문제를 압축 방식 교체로 고쳤습니다.
> - 신고 처리와 계정 이용 정지 절차를 만들고 약관에 근거를 넣었습니다.
> - 태블릿·폴더블·가로 화면에서 화면이 늘어나지 않게 했습니다.
> - 구독 화면에 가격·자동 갱신·해지 방법과 판매자 정보를 표시했습니다.

(영문)
> - Purchases are now credited to the right band even if the app is closed or offline right after paying, or never reopened.
> - One user can subscribe separately for several bands.
> - Deleting or leaving a band with an active subscription now explains how to cancel; auto-renewing subscribers no longer get expiry warnings.
> - Coupon periods and paid periods now stack instead of overwriting each other.
> - Fixed push notifications still arriving on a device after logout.
> - Fixed photos/videos breaking when a post stayed open for a while.
> - Tapping a notification now opens the related rehearsal, settlement or plan screen.
> - Attendance and payment checks now update instantly.
> - Replaced the video compressor that could hang on some videos.
> - Added a reporting/moderation process with account suspension, backed by the Terms of Service.
> - Layouts no longer stretch on tablets, foldables or landscape.
> - The subscription screen shows price, auto-renewal, how to cancel, and seller information.

**2. 프로덕션 준비가 됐다고 판단한 근거**

> 테스터 ⟨N⟩명이 14일 이상 실제 밴드 활동에 사용했고, 그 기간에 나온 문제는 모두 고쳐 +32(1.0.0)로 다시
> 배포했습니다. 서버는 자동 테스트·배포와 매일 상태 점검(백업·디스크·오류·인증서)을 돌리고 있고, 결제는 Play 결제
> 알림으로 갱신·해지·환불을 반영합니다. 개인정보처리방침·이용약관·계정 삭제 안내를 웹에 게시했고, 앱 안에 신고·차단과
> 운영자 조치 절차가 있습니다.

(영문)
> ⟨N⟩ testers used the app for their real band activities for more than 14 days, and every issue found in that
> period is fixed in the 1.0.0 (+32) build now on the test track. The backend runs automated tests and deployment plus
> a daily health check (backups, disk, errors, TLS certificate), and subscriptions are kept in sync through Play
> real-time developer notifications (renewals, cancellations, refunds). The privacy policy, terms of service and
> account-deletion page are published on the web, and the app has in-app reporting, blocking and an operator
> moderation process.
