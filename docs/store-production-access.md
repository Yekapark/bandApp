# 프로덕션 액세스 신청 — 답변 초안

> 개인 개발자 계정은 **테스터 12명 이상이 14일 연속 참여한 비공개 테스트** 뒤에 프로덕션을 신청한다
> (LAUNCH_REVIEW P1). 신청서는 Play Console › 대시보드 › "프로덕션 신청" 에 있고, 심사는 보통 7일 이내다.
> 이 문서는 그 신청서의 답을 미리 써 둔 것이다. `⟨⟩` 는 신청 직전에 사실대로 채운다.
>
> **답의 근거는 테스터 의견과 출시 전 점검(`docs/LAUNCH_REVIEW.md`) 두 가지다.** 테스터들이 밴드를 만들고 초대코드로
> 가입하며 써 보고 의견을 줬고(아래 "받은 의견"), 전체 기능 검증은 개발자가 실기기·라이선스 테스트 계정으로 직접 했다. 신청서 답변은 Google 에
> 내는 진술이라 **있었던 일만 쓴다** — 테스터 사용량·의견을 부풀리지 않는다. 사실과 다른 답은 거절 사유가 되고
> 개발자 계정 정책(허위 정보 제공)에도 걸릴 수 있다. 대신 "무엇을 찾아 어떻게 고쳤는지" 를 구체적으로 쓴다.
> 질문 출처: [비공개 테스트 요건](https://support.google.com/googleplay/android-developer/answer/14151465) (2026-09-30 확인).
>
> 마지막 갱신: 2026-09-30

---

## 신청 전에 확인할 것

- [ ] 테스터 **12명 이상이 14일 연속** 참여 중인가 — 대시보드의 "현재 참여를 선택한 테스터" 숫자.
      **중간에 참여를 취소한 사람은 세지 않고, 다시 들어오면 그 사람의 14일이 처음부터다.** 테스터에게 "앱을
      지우거나 테스트에서 나가지 말아 달라" 고 미리 말해 둔다.
- [ ] 비공개 테스트 트랙에 **+32(1.0.0)** 이 올라가 있고, **LAUNCH_REVIEW §7 실기기 확인을 끝냈는가** — 이것이
      "모든 기능을 써 봤다" 의 실제 근거다. 결제는 라이선스 테스트 계정으로 직접 한다.
- [ ] 테스터 몇 명에게라도 +32 로 업데이트해 한두 번 써 봐 달라고 부탁했는가 — 필수는 아니지만, 받은 의견이
      한 건이라도 있으면 Part 1-3 에 "있었던 일" 로 쓸 수 있다. 없으면 없다고 쓴다.
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

받은 의견은 Part 1-3 에 **받은 그대로** 적는다. 개발자가 직접 점검한 내용(Part 3)으로 보강한다.

### 받은 의견 (받는 대로 추가)

| 날짜 | 기기 | 의견(받은 그대로) | 처리 |
|---|---|---|---|
| 2026-09-30 | — | 밴드에 들어가 있으면 새 밴드를 만들 방법이 없다 | 밴드 전환 화면에 "새 밴드 만들기" 추가 (LAUNCH_REVIEW U12, +32) |
| 2026-09-30 | 갤럭시 S25+ | "밴드 이름 만들기 누르면 키패드가 안 뜨고 반복해서 누르면 앱이 튕겨요. 가입하기의 초대코드 입력란도 똑같아요" | 원인 조사 중 (LAUNCH_REVIEW U13) |

---

## Part 1 — 비공개 테스트

**1. 테스터를 얼마나 쉽게 모집했나요?** (보기 중 선택)

⟨실제대로 고른다 — 지인에게 부탁해 모았다⟩

**2. 테스터가 앱의 모든 기능을 사용했나요? / 사용 방식이 실제 사용자와 비슷했나요?**

보기는 실제대로 고른다. 설명 칸:

> 테스터들은 밴드를 만들고 초대코드로 가입하는 등 실제 사용 흐름대로 써 보고 문제를 알려 줬습니다(Part 1-3).
> 이와 함께 비공개 테스트 기간 동안 개발자가 직접 실기기에서 전체 기능을 체크리스트로 점검했습니다 — 가입·카카오 로그인, 밴드 생성과 초대 링크 참여, 합주 일정 등록·참석 응답,
> 비용 정산, 게시판 사진/영상 업로드, 합주실 지도, 푸시 알림, 계정 삭제. 유료 구독은 라이선스 테스트 계정으로
> 결제·해지·복구·쿠폰 적용까지 확인했습니다. 점검 항목은 ⟨N⟩개였고, 찾은 문제는 모두 고쳐 1.0.0(+32)에 넣었습니다.

(영문)
> Testers used the app through the real flow — creating a band and joining with an invite code — and reported the
> problems they hit (see question 3). In addition, the developer tested every
> feature on real devices during the closed test using a written checklist: sign-up and Kakao login, creating a band
> and joining via invite link, scheduling rehearsals and responding to attendance, cost splitting, photo/video posts,
> the studio map, push notifications, and account deletion. The paid subscription was tested with license-tester
> accounts, including purchase, cancellation, recovery and coupons. The checklist had ⟨N⟩ items, and every issue found
> is fixed in version 1.0.0 (+32).

**3. 받은 의견과 모은 방법**

위 "받은 의견" 표에서 채운다:

> ⟨방법: 단톡방 / 직접 대화⟩로 테스터 의견을 받았습니다. 예를 들어 "이미 밴드에 들어가 있으면 새 밴드를 만들 수
> 없다" 는 의견으로 밴드 전환 화면에 밴드 만들기를 추가했고, 갤럭시 S25+ 에서 입력칸에 키보드가 뜨지 않는다는
> 의견으로 ⟨고친 내용⟩. ⟨그 밖의 의견⟩. 이와 별도로 개발자가 출시 전 점검에서 문제 ⟨M⟩건을 찾아 고쳤습니다(Part 3).

(영문)
> We collected tester feedback through ⟨group chat / direct conversation⟩. For example, a tester pointed out that there
> was no way to create a new band once you had joined one, so we added "Create a band" to the band switcher; another
> reported that the keyboard did not appear in text fields on a Galaxy S25+, which we ⟨fixed by …⟩. Separately, the
> developer ran a pre-launch review and found and fixed ⟨M⟩ usability, billing, policy and stability issues (Part 3).

`⟨N⟩`·`⟨M⟩` 세는 법: `docs/LAUNCH_REVIEW.md` 영역별 표의 행 수(N)와 그중 ✅·🟡(M). 2026-09-30 기준 44행, ✅ 20·🟡 19.

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

비공개 테스트 기간(2026-09-27~)의 출시 전 점검(`docs/LAUNCH_REVIEW.md`)에서 개발자가 찾아 고친 것. 모두 실제로
고친 것이고 근거(PR·테스트)가 LAUNCH_REVIEW 각 행에 있다. 테스터 의견으로 고친 것을 맨 앞에 둔다.

> - (테스터 의견) 이미 밴드에 속해 있어도 새 밴드를 만들 수 있게 했습니다.
> - (테스터 의견) ⟨갤럭시 S25+ 입력칸 키보드 문제 — 고친 뒤 적는다⟩
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
> - (Tester feedback) You can now create a new band even if you already belong to one.
> - (Tester feedback) ⟨Galaxy S25+ keyboard issue — fill in after the fix⟩
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

> 14일 이상의 비공개 테스트 기간에 테스터 의견을 받고 전체 기능을 실기기에서 점검했으며, 찾은 문제는 모두 고쳐 1.0.0(+32)으로 다시
> 배포했습니다. 서버는 자동 테스트·배포와 매일 상태 점검(백업·디스크·오류·인증서)을 돌리고 있고, 결제는 Play 결제
> 알림으로 갱신·해지·환불을 반영합니다. 개인정보처리방침·이용약관·계정 삭제 안내를 웹에 게시했고, 앱 안에 신고·차단과
> 운영자 조치 절차가 있습니다.

(영문)
> During the 14+ day closed test we collected tester feedback and checked every feature on real devices, and every issue found in that period is
> fixed in the 1.0.0 (+32) build now on the test track. The backend runs automated tests and deployment plus
> a daily health check (backups, disk, errors, TLS certificate), and subscriptions are kept in sync through Play
> real-time developer notifications (renewals, cancellations, refunds). The privacy policy, terms of service and
> account-deletion page are published on the web, and the app has in-app reporting, blocking and an operator
> moderation process.
