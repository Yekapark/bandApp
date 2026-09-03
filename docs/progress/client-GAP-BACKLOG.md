# 클라이언트 갭 백로그 — 백엔드에 있는데 클라이언트에 없는 기능

> **다른 PC에서 이 파일만 보고 바로 이어서 구현할 수 있게 쓴 실행용 체크리스트.**
> 먼저 `client-DEVLOG.md`(현재 상태·환경 함정)와 `client-SCREENS.md`(화면 표)를 읽는다.
> 각 항목: 백엔드 API · 만들 파일 · 완료 기준 · 체크박스. 구현하면 `[ ]`→`[x]` 로 바꾸고
> 슬라이스 문서(`client-1N-*.md`)를 남기고 `client-DEVLOG.md`·`client-SCREENS.md`·`README.md` 갱신.
>
> 브랜치: `feat/client-remaining` (C6~C9 이어서). 매 슬라이스마다
> `flutter analyze`(에러 0) · `flutter test` · `flutter build web` 통과 후 커밋.
> 이 PC Flutter: `C:\src\flutter\bin` / 저장소: `C:\band\bandApp`.
>
> 마지막 갱신: **2026-09-04** (C10~C15 전부 완료 — 백엔드엔 있는데 클라에 없던 기능 정리 끝)

---

## 진행 요약 — ✅ 전부 완료

| 슬라이스 | 항목 | 상태 |
|---|---|---|
| C10 | 초대코드 발급/조회/무효화 (멤버 초대 화면) | ✅ 완료 — `client-10-invite.md` |
| C11 | 합주실 수정/삭제 | ✅ 완료 — `client-11-room-edit.md` |
| C12 | 셋리스트 재정렬 + 곡 수정 | ✅ 완료 — `client-12-setlist.md` |
| C13 | 정기 일정 규칙 상세 (회차 목록) | ✅ 완료 — `client-13-recurring-detail.md` |
| C14 | 밴드 요금제 (FREE/PREMIUM) | ✅ 완료 — `client-14-plan.md` |
| C15 | 미디어 신고 · 캘린더 취소건 표시 · 초대 링크 프리필 | ✅ 완료 — `client-15-misc.md` |

**남은 것은 "백엔드에도 없어서 못 하는 것"(맨 아래 목록)과 OS 레벨 딥링크 네이티브 등록뿐.**

---

## C10 — 초대코드 발급/조회/무효화 (`4. 초대`)

**문제**: 밴드장이 초대코드·공유링크를 볼 화면이 없다. 코드를 **입력해서 참여**하는 쪽
(`/band-gate/join`)만 있어서 초대 플로우가 실질적으로 끊겨 있다.

**백엔드 API** (전부 밴드장만, Bearer):
- `POST /api/v1/bands/{bandId}/invites` — 발급/재발급. body 선택 `IssueInviteRequest{maxUses?, ttlDays?}`
  (생략 시 만료 7일·무제한). 재발급하면 기존 활성 코드 즉시 무효화. → `InviteResponse{code, link, expiresAt, maxUses, usedCount, revoked}`. 비밴드장 403 `NOT_BAND_LEADER`.
- `GET /api/v1/bands/{bandId}/invites/current` — 현재 활성 코드. 없으면 404 `INVITE_NOT_FOUND`.
- `DELETE /api/v1/bands/{bandId}/invites/current` — 무효화(204, 멱등).

**만들 것**:
- `features/band/data/invite_models.dart` — `BandInvite{code, link, expiresAt, maxUses, usedCount, revoked}`
- `features/band/data/invite_repository.dart` — `current()`, `issue({maxUses?, ttlDays?})`, `revoke()`
  (404 INVITE_NOT_FOUND 는 "코드 없음"으로 정상 처리 — null 반환)
- `features/band/application/invite_providers.dart` — `currentInviteProvider` family(bandId)
- `features/band/presentation/invite_screen.dart` — `/band/invite`
  - 코드가 있으면: 코드(모노스페이스 크게) + 만료·사용횟수, "코드 복사" / "링크 공유"(Clipboard),
    "재발급"(확인 다이얼로그 — 기존 코드 무효화 경고), "무효화"
  - 코드가 없으면: "초대코드 만들기" 버튼 → issue
  - 밴드장이 아니면: "초대코드는 밴드장만 발급할 수 있어요" 안내
- 라우트: `Routes.invite = '/band/invite'`, `app_router.dart` 에 GoRoute 추가
- 진입점: ① 설정 → 밴드 설정 상단에 "멤버 초대" 항목, ② 밴드 홈 헤더 또는 멤버 레일 근처에 "초대" 버튼

**완료 기준**:
- [x] 밴드장으로 코드 발급 → 코드·링크 표시, 복사 동작
- [x] 재발급 시 이전 코드가 바뀜, 무효화 시 "코드 없음" 상태로
- [x] 일반 멤버는 읽기 전용 안내
- [x] `flutter analyze` 에러 0 · `flutter test` · `flutter build web` 통과
- [x] `client-10-invite.md` 작성, 상단 표·DEVLOG·SCREENS·README 갱신

✅ **완료 (2026-09-04)** — `features/band/{data/invite_*,application/invite_providers,presentation/invite_screen}.dart`,
`/band/invite`, 진입점 설정 허브·밴드 설정. 만료일수·사용횟수 옵션 폼은 미노출(기본값 발급).

---

## C11 — 합주실 수정/삭제 (`3. 합주실`)

**문제**: 합주실은 **등록만** 가능. 이름 오타 수정·폐업한 방 삭제 불가.

**백엔드 API**:
- `GET /api/v1/bands/{bandId}/rooms/{roomId}` — 상세 (`RoomResponse`)
- `PUT /api/v1/bands/{bandId}/rooms/{roomId}` — 수정(PUT 전체 교체) `UpdateRoomRequest{name, address?, phone?, memo?}`.
  주소가 실제로 바뀐 경우에만 지오코딩 재시도.
- `DELETE /api/v1/bands/{bandId}/rooms/{roomId}` — 삭제(soft). (권한: 등록자/밴드장으로 추정 — 실제 서버 응답 확인)

**만들 것**:
- `features/reservation/data/room_repository.dart` — `update({roomId, name, address?, phone?, memo?})`, `delete(roomId)`
- `features/reservation/presentation/room_form_screen.dart` — `existing` (Room) 파라미터로 **수정 모드 겸용**
  (reservation_form_screen 이 이미 이 패턴 — 참고). 주소검색·필드 프리필, 버튼 문구 분기.
- 라우트: `/cal/rooms/:roomId/edit` (extra 로 Room 전달). `Routes.editRoom(int)`
- 진입점: 합주실 선택 시트(`room_picker_sheet.dart`)의 각 항목에 ⋯ → 수정/삭제,
  그리고 지도 화면(`map_screen.dart`) 목록 항목에도.
- 삭제 확인 다이얼로그(사용 중인 일정에는 영향 없음 안내). 삭제 후 `roomsProvider` 무효화.

**완료 기준**:
- [x] 시트/지도에서 합주실 수정 → 반영, 삭제 → 목록에서 사라짐
- [x] `analyze`/`test`/`build web` 통과
- [x] `client-11-room-edit.md` + 문서 갱신

✅ **완료 (2026-09-04)** — `room_repository.update/delete`, `room_form_screen` 수정 모드,
`room_picker_sheet`·`map_screen` 목록에 ⋯ 메뉴, `/cal/rooms/:roomId/edit`.

---

## C12 — 셋리스트 재정렬 + 곡 수정 (`10. 셋리스트`)

**문제**: 일정 상세 셋리스트가 추가·삭제만 됨.

**백엔드 API** (일정 상세 화면 컨텍스트, `.../reservations/{id}/setlist`):
- `PUT .../setlist/reorder` — `ReorderSetlistRequest{itemIds: List<Long>}` — 그 일정의 **모든** 항목 id를
  원하는 순서로. 빠지거나 중복이면 400. orderNo 가 1..N 재부여.
- `PUT .../setlist/{itemId}` — `UpdateSetlistItemRequest{title, artist?, referenceUrl?}` (PUT 전체 교체)

**만들 것**:
- `features/reservation/data/reservation_repository.dart` — `reorderSetlist({bandId, reservationId, itemIds})`,
  `updateSetlistItem({bandId, reservationId, itemId, title, artist?, referenceUrl?})`
- `reservation_detail_screen.dart` `_SetlistBlock`:
  - `ReorderableListView`(또는 드래그 핸들) 로 순서 변경 → onReorder 시 새 순서 id 리스트로 reorder 호출
    (낙관적 업데이트 후 실패 시 `reservationDetailProvider` 무효화로 롤백)
  - 각 곡에 "수정" (제목·아티스트·참고 URL 다이얼로그 — 기존 `_AddSongDialog` 재사용/확장)
- `editable`(활성 일정)일 때만.

**완료 기준**:
- [x] 드래그로 순서 바뀌고 서버 반영, 곡 정보 수정 반영
- [x] `analyze`/`test`/`build web` 통과
- [x] `client-12-setlist.md` + 문서 갱신

✅ **완료 (2026-09-04)** — `reservation_repository.reorderSetlist/updateSetlistItem`,
`_SetlistBlock` 을 `ReorderableListView`(onReorderItem) 로, `_SongDialog` 추가/수정 겸용
+ 참고 링크 칸. 낙관적 재정렬(실패 시 invalidate 롤백).

---

## C13 — 정기 일정 규칙 상세 (회차 목록) (`8. 정기 일정`)

**문제**: 규칙 목록 카드만 있고, 규칙을 눌러 생성된 회차들을 보는 화면이 없다.

**백엔드 API**:
- `GET /api/v1/bands/{bandId}/recurring-rules/{ruleId}` →
  `RecurringRuleDetailResponse{rule: RecurringRuleResponse, occurrenceCount, occurrences: List<ReservationResponse>}`
  (occurrences = 오늘 − horizonWeeks 이후 회차, 취소 포함, start_at 오름차순)

**만들 것**:
- `features/recurring/data/recurring_repository.dart` — `detail({bandId, ruleId})`
- `features/recurring/data/recurring_models.dart` — `RecurringRuleDetail{rule, occurrenceCount, occurrences: List<Reservation>}`
  (`Reservation.fromJson` 재사용)
- `features/recurring/application/recurring_providers.dart` — `recurringRuleDetailProvider` family
- `features/recurring/presentation/recurring_detail_screen.dart` — `/cal/recurring/:ruleId`
  - 규칙 요약(주기·요일·시간·기간·합주실·메모) + 회차 리스트(날짜·상태, 탭 시 `/reservations/:id` 상세로)
  - 규칙 삭제 버튼(목록 화면과 동일 로직 — 등록자/밴드장)
- 진입점: `recurring_list_screen.dart` 의 규칙 카드 탭 → 상세

**완료 기준**:
- [x] 규칙 탭 → 회차 목록 표시, 회차 탭 → 일정 상세로 이동
- [x] `analyze`/`test`/`build web` 통과
- [x] `client-13-recurring-detail.md` + 문서 갱신

✅ **완료 (2026-09-04)** — `RecurringRuleDetail` 모델, `recurringRuleDetailProvider`,
`recurring_detail_screen.dart` (`/cal/recurring/:ruleId`), 목록 카드 탭 진입.
규칙 수정 UI 는 백엔드 미제공이라 없음.

---

## C14 — 밴드 요금제 FREE/PREMIUM (`16. 요금제`, Phase 10)

**문제**: 요금제 관련 화면 전무. 미디어 보관기한(FREE 30일 / PREMIUM 무제한) 안내도 없음.

**백엔드 API** (`/api/v1/bands/{bandId}/plan`, 조회는 멤버 / 전환은 밴드장):
- `GET` → `PlanResponse{tier: "FREE"|"PREMIUM", mediaRetentionDays: int?(FREE=30, PREMIUM=null), startedAt, expiresAt?}`
- `POST /subscribe` — FREE→PREMIUM. 이미 PREMIUM 409 `PLAN_ALREADY_PREMIUM`, 결제 실패 402 `PAYMENT_FAILED`.
- `POST /cancel` — PREMIUM→FREE. 이미 FREE 409 `PLAN_ALREADY_FREE`. (미디어 30일 유예 후 만료 안내)
- `POST /renew` — PREMIUM 구독기간 연장. FREE 면 409 `PLAN_ALREADY_FREE`.

> 실제 결제는 앱스토어/구글플레이 모듈 몫이고 백엔드는 no-op 게이트웨이. 클라이언트도 결제 연동 없이
> 버튼만 — "이 릴리스에서는 결제 없이 전환됩니다" 정도로 안내.

**만들 것**:
- `features/plan/data/plan_models.dart` — `BandPlan{tier, mediaRetentionDays?, startedAt, expiresAt?}` + `isPremium`
- `features/plan/data/plan_repository.dart` — `view(bandId)`, `subscribe(bandId)`, `cancel(bandId)`, `renew(bandId)`
- `features/plan/application/plan_providers.dart` — `bandPlanProvider` family
- `features/plan/presentation/plan_screen.dart` — `/settings/plan`
  - 현재 티어 배지, FREE/PREMIUM 비교(미디어 보관기한), PREMIUM 이면 구독기간·연장·해지 / FREE 이면 구독 시작
  - 전환은 밴드장만, 확인 다이얼로그
- 라우트 `Routes.plan = '/settings/plan'`, 설정 허브에 "요금제" 항목
- (선택) 게시판/미디어 화면에 FREE 30일 보관 안내 배지 — 여유 되면

**완료 기준**:
- [x] 요금제 조회, 밴드장이 구독/해지/연장, 상태 갱신
- [x] 일반 멤버는 읽기 전용
- [x] `analyze`/`test`/`build web` 통과
- [x] `client-14-plan.md` + 문서 갱신

✅ **완료 (2026-09-04)** — `features/plan/*`, `/settings/plan`, 설정 허브 "요금제" 진입.
비교표(미디어 보관기한), 밴드장 전환 버튼. 스토어 결제 연동은 없음(버튼만). 미디어 화면 보관 배지는 미추가.

---

## C15 — 잔여: 미디어 신고 · 캘린더 취소건 표시 · 초대 링크 프리필

### 15a. 미디어(개별 첨부) 신고 (`14. 신고`)
- `POST /api/v1/reports` 에 `targetType: "MEDIA"`, `targetId: <mediaId>` 지원됨. 현재 UI는 POST·USER만.
- `post_detail_screen.dart` `_MediaBlock` 에 롱프레스/오버플로 → "이 첨부 신고" → 기존 `_report` 재사용
  (`targetType: 'MEDIA', targetId: media.id`).
- [ ] 개별 사진/영상에서 신고 접수

### 15b. 캘린더에서 취소·거절된 일정 보기
- `GET .../reservations?...&includeInactive=true` 로 취소·거절 포함 가능(현재 항상 false).
- `calendar_providers.dart` `monthReservationsProvider` 에 플래그, `calendar_screen.dart` 에 "취소된 일정 표시" 토글.
  취소·거절 건은 흐리게/취소선 표시(`reservation_models.dart` 의 status 활용).
- [ ] 토글 on 시 취소건이 흐리게 보임

### 15c. 초대 링크로 열면 코드 자동 입력
- 백엔드가 `GET /invite/{code}` 랜딩 + AASA/assetlinks 제공. 앱이 그 링크(또는 커스텀 스킴)로 열렸을 때
  `/band-gate/join?code=XXXX` 로 라우팅 + 코드 프리필.
- 최소 구현: `join_band_screen.dart` + 라우트가 `?code=` 쿼리를 받아 입력칸 프리필 & 자동 포커스.
  실제 OS 딥링크(Android intent-filter / iOS associated domains) 등록은 네이티브 설정이라 **후속**
  (`client/android`·`ios` 가 gitignore — 별도 작업). `app_links` 패키지 도입 여부는 그때 결정.
- [ ] `/band-gate/join?code=ABC12345` 로 들어가면 코드가 채워져 있음
- [ ] (후속으로 남김) OS 딥링크 네이티브 등록

### 완료 기준
- [x] 위 3개 반영, `analyze`/`test`/`build web` 통과
- [x] `client-15-misc.md` + 문서 갱신

✅ **완료 (2026-09-04)** — 15a: `_MediaBlock` onLongPress → MEDIA 신고. 15b:
`showCancelledReservationsProvider` + "취소 포함" 토글, 취소건 취소선. 15c: `join_band_screen`
`initialCode` + `/band-gate/join?code=` 라우트. OS 딥링크 네이티브 등록은 후속.

---

## 백엔드에도 없어서 못 하는 것 (참고 — 구현 대상 아님)

- 정산 총액 변경 API 없음
- 밴드 이름 변경·삭제 API 없음
- 프로필(이름·아바타) 편집 API 없음
- 정기 규칙 **수정** API 없음 (삭제 후 재등록만)
- 정산 미납 독촉 알림 트리거 API 없음
- 캘린더 주간 뷰는 프론트 작업 (백엔드 무관, 여력 되면)
