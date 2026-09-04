# 클라이언트 화면 구현 현황

> 지금까지 만든 Flutter 화면을 한눈에 보는 표. 진행하면서 갱신한다.
> 마지막 갱신: **2026-09-04** (C9 FCM 수신부 + 클라 CI — 요구 화면 13개 완료)
> 상세 배경은 단계별 문서(`client-01`~`client-03`, `client-DEVLOG.md`) 참조.

범례: ✅ 구현 완료 · 🟡 부분 구현 · ⛔ 미구현

---

## 1. 요구사항 13개 화면 대비 (출처: `docs/BACKLOG.md` §2)

| # | 요구 화면 | 상태 | 실제 라우트 / 파일 | 메모 |
|---|---|---|---|---|
| 1 | 로그인 / 회원가입 (카카오 중심) | ✅ | `/login` `login_screen.dart`, `/signup` `signup_screen.dart` | 이메일 로그인·가입 동작. 카카오 SDK 배선 완료 — 앱 키 넣으면 동작, 미설정 시 "준비 중" 스낵바 |
| 2 | 초대코드 입력 | ✅ | `/band-gate/join` `join_band_screen.dart` | 코드 입력해 참여. **코드 발급(밴드장)**은 `/band/invite` `invite_screen.dart` (C10) |
| 3 | 밴드 미소속 상태 | ✅ | `/band-gate` `band_gate_screen.dart` | "밴드 만들기 / 초대코드로 참여" 선택 |
| 4 | 밴드 홈 | ✅ | `/home` (탭) `home_screen.dart` | 밴드 전환 스위처, 다가오는 합주, 멤버 레일, 요약 카드 |
| 5 | 합주 일정 캘린더 | ✅ | `/cal` (탭) `calendar_screen.dart` | 월간 뷰. "취소 포함" 토글(C15). 주간 뷰 없음 |
| 6 | 일정 등록/수정 폼 | ✅ | `/cal/new`, `/reservations/:id/edit` `reservation_form_screen.dart` | 합주실·날짜·시간·비용·메모. 수정(PUT) 겸용. **반복은 별도 정기 일정 화면**(#아래) |
| 7 | 일정 상세 | 🟡 | `/reservations/:id` `reservation_detail_screen.dart` | 참석 체크·멤버별 현황·셋리스트(추가·삭제·**재정렬**·**수정**, C12)·**일정 수정**·**밴드장 승인/거절**(C7). 셋리스트 완료체크 없음 |
| 8 | 합주실 목록 / 지도 | 🟡 | `/map` (탭) `map_screen.dart` | **카카오맵**(`kakao_map_sdk`) 마커 + 하단 목록. **ARM 기기 전용** — 웹·x86 에뮬레이터에서는 목록만(2026-09-04). 로그인과 같은 `KAKAO_NATIVE_APP_KEY` 사용 |
| 9 | 합주실 등록/수정 폼 | ✅ | `/cal/rooms/new`, `/cal/rooms/:id/edit` `room_form_screen.dart` | 이름·주소검색·연락처·메모. **검색 후보가 지도에 핀으로 뜨고, 고른 좌표가 그대로 저장된다**(2026-09-04). 수정·삭제는 합주실 선택 시트·지도 목록의 ⋯ 메뉴 (C11) |
| 10 | 정산 화면 | ✅ | `/reservations/:id/settlement` `settlement_screen.dart` | 1인당 금액·진행바·납부 체크리스트·재계산. 균등/참석자만 토글. 본인 몫만 셀프 체크 |
| 11 | 게시판 (사진/영상 피드) | ✅ | `/board` (탭) `board_screen.dart` | 커서 무한스크롤. 글쓰기 FAB. 대표 이미지 썸네일 (C6) |
| 12 | 게시글 상세 | 🟡 | `/board/:postId` `post_detail_screen.dart` | 본문·첨부 갤러리·이미지 전체화면 뷰어·수정/삭제·신고(글·작성자·**첨부 길게눌러 C15**)·차단. **영상 인앱 재생**(`video_player`, 탭하면 전체화면·스크러빙, 2026-09-04). 작성/수정은 `post_compose_screen.dart` — **쓰면서 사진·영상을 고르고 등록할 때 함께 올린다**(2026-09-04) |
| 13 | 설정 (알림·밴드 설정·계정) | ✅ | `/settings` `settings_home_screen.dart` (+ `/settings/band`, `/settings/account`, `/settings/blocks`, `/settings/notifications`) | 허브 + 밴드 설정(일정 권한·밴드장 위임·멤버 추방·나가기) + 계정(탈퇴) + 차단 해제 + 알림. FCM 디바이스 토큰 등록·포그라운드 수신은 C9(`push_service.dart`, 설정 파일 없으면 no-op) |

추가로 만든 화면(C6 게시판 · C7 정기 일정):

| 화면 | 라우트 / 파일 | 메모 |
|---|---|---|
| 글 작성/수정 | `/board/new`, `/board/:postId/edit` `post_compose_screen.dart` | 새 글 등록 직후 같은 화면에서 첨부 추가. `image_picker` → R2 presigned PUT |
| 정기 일정 목록 | `/cal/recurring` `recurring_list_screen.dart` | 규칙 카드(탭 → 상세)·삭제. 캘린더 AppBar ↻ 아이콘에서 진입 |
| 정기 일정 등록 | `/cal/recurring/new` `recurring_form_screen.dart` | 주기(매주/격주/매월)·요일·시간·기간·비용·메모. 등록 시 8주분 회차 자동 생성 |
| 정기 일정 상세 | `/cal/recurring/:ruleId` `recurring_detail_screen.dart` | 규칙 요약 + 다가오는 회차 목록(탭 → 일정 상세). 삭제 (C13) |
| 설정 허브 | `/settings` `settings_home_screen.dart` | 홈 헤더 ⚙ 아이콘. 알림/밴드/차단/계정/로그아웃 |
| 밴드 설정 | `/settings/band` `band_settings_screen.dart` | 일정 등록 권한 모드, 멤버 목록(밴드장 위임·추방), 밴드 나가기 |
| 계정 | `/settings/account` `account_screen.dart` | 내 정보, 회원 탈퇴(이메일 계정은 비밀번호 재확인) |
| 차단한 사용자 | `/settings/blocks` `blocked_users_screen.dart` | 차단 목록 + 해제 |
| 멤버 초대 | `/band/invite` `invite_screen.dart` | 밴드장이 초대코드·링크 발급/재발급/무효화 (C10) |
| 요금제 | `/settings/plan` `plan_screen.dart` | FREE/PREMIUM 조회·비교표·전환(밴드장). 실제 결제 연동 없음 (C14) |

추가로 만든 화면(요구 목록엔 없지만 흐름상 필요):

| 화면 | 라우트 / 파일 | 메모 |
|---|---|---|
| 스플래시 | `/` `splash_screen.dart` | 토큰 있으면 `GET /users/me`로 자동 로그인 판정 |
| 약관 동의 | `/terms` `terms_screen.dart` | 클라 게이트(백엔드 없음) |
| 밴드 만들기 | `/band-gate/create` `create_band_screen.dart` | `POST /bands` |
| 합주실 선택 시트 | (모달) `widgets/room_picker_sheet.dart` | 일정 폼에서 호출. 비어 있으면 등록 폼으로 |

---

## 2. 라우트 표 (실제 `app_router.dart` 기준)

| 경로 | 화면 | 네비게이터 | 진입점 |
|---|---|---|---|
| `/` | 스플래시 | 루트 | 앱 시작 |
| `/login` `/signup` `/terms` | 로그인·가입·약관 | 루트 | 미인증 redirect |
| `/band-gate` `/band-gate/create` `/band-gate/join` | 밴드 게이트·생성·가입 | 루트 | 밴드 없을 때 |
| `/home` | 밴드 홈 | **탭 셸 브랜치 0** | 하단 탭 "홈" |
| `/cal` | 예약 캘린더 | **탭 셸 브랜치 1** | 하단 탭 "캘린더", 홈 카드 |
| `/map` | 합주실 지도 | **탭 셸 브랜치 2** | 하단 탭 "지도" |
| `/cal/new?date=` | 일정 등록 폼 | 루트 (풀스크린) | 캘린더 "＋ 등록", 홈 "일정 추가" |
| `/cal/rooms/new` | 합주실 등록 폼 | 루트 (풀스크린) | 합주실 선택 시트 |
| `/cal/recurring` | 정기 일정 목록 | 루트 (풀스크린) | 캘린더 AppBar ↻ 아이콘 |
| `/cal/recurring/new` | 정기 일정 등록 | 루트 (풀스크린) | 정기 일정 목록 "＋" |
| `/cal/recurring/:ruleId` | 정기 일정 상세 | 루트 (풀스크린) | 정기 일정 목록 카드 탭 |
| `/reservations/:id` | 일정 상세 | 루트 (풀스크린) | 캘린더·홈 일정 타일 |
| `/reservations/:id/edit` | 일정 수정 | 루트 (풀스크린) | 일정 상세 "일정 수정" |
| `/reservations/:id/settlement` | 정산 | 루트 (풀스크린) | 일정 상세 "정산 보기" |
| `/board` | 게시판 피드 | **탭 셸 브랜치 3** | 하단 탭 "게시판" |
| `/board/new` | 글 작성 | 루트 (풀스크린) | 게시판 "글쓰기" FAB |
| `/board/:postId` | 게시글 상세 | 루트 (풀스크린) | 피드 카드 탭 |
| `/board/:postId/edit` | 글 수정 | 루트 (풀스크린) | 상세 ⋮ → 수정 |
| `/settings` | 설정 허브 | 루트 (풀스크린) | 홈 헤더 ⚙ 아이콘 |
| `/settings/band` `/settings/account` `/settings/blocks` | 밴드·계정·차단 | 루트 (풀스크린) | 설정 허브 |
| `/settings/notifications` | 알림 설정 | 루트 (풀스크린) | 홈 헤더 종 아이콘 · 설정 허브 |

하단 탭바(`routing/tab_shell.dart`, `StatefulShellRoute.indexedStack`):
**홈·캘린더·지도·게시판**은 브랜치 전환(스택·스크롤 각자 보존), **정산**만 화면이 없어
`showSoon` 스낵바(정산은 일정 상세에서 진입).

---

## 3. 남은 화면 / 다음 작업

우선순위는 `client-DEVLOG.md` §5 참조.

1. ~~하단 탭바 `ShellRoute` 전환~~ ✅ 2026-09-04
2. ~~**합주실 지도** `/map` (#8)~~ 🟡 2026-09-04 — 카카오맵으로 교체. 웹·x86 에뮬은 목록만, 실기기 end-to-end 미검증
3. ~~알림 설정 (#13 일부)~~ 🟡 2026-09-03 — 푸시 on/off·리마인더 시점. `client-05-notification-settings.md`.
   남은 것: FCM 수신부(디바이스 토큰·`firebase_messaging`), 미납 독촉 → `client-PROBLEMS-2026-09-03.md`
4. ~~게시판·게시글 상세 (#11·#12)~~ ✅ 2026-09-04 (C6) — `client-06-board.md`. 영상 재생·차단 해제도 완료
5. ~~일정 상세 보강 — 수정(PUT)·밴드장 승인/거절 + 정기(반복) 일정~~ ✅ 2026-09-04 (C7) — `client-07-reservation-recurring.md`
6. ~~설정 나머지 (#13)~~ ✅ 2026-09-04 (C8) — `client-08-settings.md`
7. ~~알림 수신부(FCM 디바이스 토큰) + 클라이언트 CI~~ ✅ 2026-09-04 (C9) — `client-09-fcm-ci.md`
8. ~~영상 재생~~ ✅ 2026-09-04 · ~~셋리스트 재정렬~~ ✅ (C12) · ~~차단 해제~~ ✅ (C8, `/settings/blocks`)
9. **(다음)** 알림 목록 화면(백엔드 조회 API 없음 — §4 참조), 정기 규칙 상세/수정,
   알림 딥링크, 캘린더 주간 뷰, 셋리스트 완료 체크, 미납 독촉(백엔드 API 없음),
   PREMIUM 만료 자동 강등(백엔드 TODO), 출시 전 패키지명·릴리스 서명

---

## 4. 알려진 제약 (목업 ↔ 구현 차이)

| 항목 | 목업 | 구현 |
|---|---|---|
| 반복 일정 | 폼에 반복 요일·횟수 | 제외 (백엔드 API 없음) |
| 예약 방법 태그 | 전화/카톡 칩 선택 | 자유 텍스트 "예약 메모" 한 칸 (`note`만 존재) |
| 초대코드 | 6자리 숫자 키패드 | 8자 영숫자 텍스트 입력 |
| 캘린더 뷰 | 월/주 토글 | 월간만 |
| 셋리스트 | 드래그 정렬 + 완료 체크 | 추가·삭제만 |
| 날짜/시간 피커 | 커스텀 다크 | Flutter 기본 Material (한국어 로케일 적용, 다크 색상 추후) |
| 홈 "이번 달 정산" 카드 | 밴드 단위 합계 | 값 `—` (집계 API 없음) |
| 홈 알림 배지 | 안 읽은 알림 수 | 항상 `0` — **알림 목록 화면이 없다**. 백엔드에 발송 이력 조회 API 없음(`NotificationDispatch` 는 멱등 키라 문구도 저장 안 함) |
| 네이버 로그인 | 소셜 로그인 | 버튼만 있고 "준비 중" 스낵바 |
| 밴드 장르·파트 | 생성 시 선택 | "추후 지원 예정" 안내문만 |
| 요금제 결제 | 실제 결제 | `PaymentGateway` no-op. 기한 지난 PREMIUM 자동 강등 배치도 없음(`PlanService` TODO) |
| 약관 동의 | 동의 기록 | 클라 게이트만 (백엔드 없음) |
| 정기 일정 | 규칙 상세·수정 | 등록·목록·삭제만 |
| 알림 딥링크 | 알림 눌러 해당 화면 | 미구현 (push data 에 bandId·reservationId 는 이미 실려 온다) |
