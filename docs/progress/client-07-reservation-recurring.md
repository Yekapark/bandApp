# 클라이언트 C7 — 일정 수정·승인/거절 + 정기 일정

## 1. 한 줄 요약

일정 상세를 보강했다 — **일정 수정(PUT)**, 밴드장의 **승인/거절** 버튼.
그리고 **정기 일정** 화면(규칙 목록·등록·삭제)을 새로 붙였다. **백엔드 변경 없음**
(Phase 4 `/reservations/{id}` PUT·approve·reject, Phase 5 `/recurring-rules` 사용).

## 2. 이 단계의 목표

- `client-DEVLOG.md` §5 의 C7: 일정 수정, 밴드장 승인/거절, 정기(반복) 일정.
- 백엔드는 이미 있었다 — 정기 일정은 Phase 5(`phase-05-recurring.md`), 일정 수정·승인은
  Phase 4(`phase-04-reservation.md`). 클라이언트에서만 안 붙어 있던 상태.

## 3. 무엇을 만들었나

### 일정 수정 / 승인 / 거절

| 파일 | 바뀐 것 |
|---|---|
| `features/reservation/data/reservation_repository.dart` | `update()`(PUT 전체 교체), `approve()`, `reject()` 추가 |
| `features/reservation/presentation/reservation_form_screen.dart` | `existing` 파라미터로 **수정 모드** 겸용. 기존 값 프리필, 버튼/제목 문구 분기, 저장 후 `context.pop()` |
| `features/reservation/presentation/reservation_detail_screen.dart` | `PENDING + 밴드장`이면 **승인/거절** 버튼. `수정 가능(활성) + 등록자·밴드장`이면 **일정 수정** 버튼(→ `/reservations/:id/edit`, `extra`로 `Reservation` 전달) |
| `routing/app_router.dart` | `/reservations/:rid/edit`(`:rid` 앞에 등록), `Routes.editReservation()` |

- 거절은 확인 다이얼로그를 거친다(합주실 usageCount 되돌림 · 등록자 알림 안내).
- `APPROVAL_REQUIRED` 밴드에서 확정 일정의 **시간·합주실**을 수정하면 백엔드가 다시 `PENDING`으로
  돌린다 — 그 경우 상세를 재조회하면 승인 버튼이 다시 뜬다.

### 정기 일정 (신규 feature: `client/lib/features/recurring/`)

| 파일 | 역할 |
|---|---|
| `data/recurring_models.dart` | `RecurringRule` · `RecurringWriteResult`(규칙+회차수+겹침). `RecurringFrequency`(weekly/biweekly/monthly) enum, 요일·시간 wire 헬퍼 |
| `data/recurring_repository.dart` | `list` · `create` · `delete` |
| `application/recurring_providers.dart` | `recurringRulesProvider`(family, bandId) |
| `presentation/recurring_list_screen.dart` | `/cal/recurring` — 규칙 카드 목록, 삭제(등록자·밴드장), "정기 일정 추가" FAB |
| `presentation/recurring_form_screen.dart` | `/cal/recurring/new` — 합주실·주기(매주/격주/매월)·요일·시작/종료 시각·시작일·(선택)종료일·비용·메모 |

- 진입점: **캘린더 화면 AppBar 의 반복(↻) 아이콘** → `/cal/recurring`.
- 규칙 등록 후 결과 다이얼로그로 "생성된 회차 수 + 겹치는 기존 일정"을 안내하고, 겹쳐도 성공.
- 규칙 삭제는 **아직 시작하지 않은 회차만** 취소(백엔드 동작). 개별로 수정해 둔 미래 회차·과거 회차는 유지.
- 개별 회차의 수정·취소는 일반 일정 상세 화면(`/reservations/:id`)을 그대로 쓴다 — 규칙은 유지된다.

## 4. 어떻게 동작하나

- **수정**: 상세 "일정 수정" → 폼이 기존 값으로 채워짐 → 저장 시 `PUT /reservations/{id}` →
  `monthReservationsProvider`·`upcomingReservationsProvider`·`reservationDetailProvider` 무효화 → 상세로 복귀.
- **승인/거절**: 상세에서 `POST …/approve` 또는 `…/reject` → 같은 provider 들 무효화 → 배너·버튼 갱신.
- **정기 등록**: `POST /bands/{id}/recurring-rules` → 8주분 회차가 `Reservation`으로 생성 →
  캘린더·홈 provider 무효화 → 새 회차가 바로 보인다.

## 5. 직접 확인하는 법

```powershell
cd C:\band\bandApp\client
& C:\src\flutter\bin\flutter.bat analyze     # 에러 0
& C:\src\flutter\bin\flutter.bat test        # 14개 통과(recurring_models_test 5개 포함)
& C:\src\flutter\bin\flutter.bat build web   # √ Built build\web
```

앱에서:

1. **일정 수정**: 캘린더 → 일정 탭 → 상세 → "일정 수정"(등록자/밴드장) → 시간·합주실·메모 바꿔 저장.
   상세로 돌아와 반영 확인.
2. **승인/거절**: 밴드 설정에서 일정 권한을 `APPROVAL_REQUIRED`로 바꾸고(← C8에서 UI 제공 예정,
   지금은 `PUT /bands/{id}/settings`로) 일반 멤버가 일정을 등록하면 `PENDING`.
   밴드장 계정으로 상세 진입 → "승인"/"거절".
3. **정기 일정**: 캘린더 AppBar ↻ 아이콘 → "정기 일정 추가" → 매주 토 19:00–22:00 등록 →
   결과 다이얼로그의 회차 수 확인 → 캘린더에 회차 점이 찍히는지.

문제 해결:

- `/reservations/:id/edit`를 딥링크·새로고침으로 바로 열면 `extra`(Reservation)가 없어
  빈 폼이 된다. 정상 경로(상세의 "일정 수정")로 진입하면 프리필된다. (알려진 제약)
- 정기 규칙 등록 시 400 `INVALID_RECURRING_TIME`/`INVALID_RECURRING_DATE_RANGE`는
  종료 시각/종료일이 시작보다 앞설 때. 폼에서도 1차로 막지만 서버 메시지를 그대로 노출.
- `ANYONE`이 아닌 밴드에서 일반 멤버가 정기 규칙을 등록하면 403 `NOT_BAND_LEADER`.

## 6. 검증 결과

- `flutter analyze` → **에러 0**(전체 204 issues, 전부 기존과 동일 계열 info/warning). `dart format` 적용.
- `flutter test` → **14개 전부 통과**(신규 `recurring_models_test.dart` 5개 포함).
- `flutter build web` → JS 빌드 성공.
- 백엔드 붙인 end-to-end 는 **미검증**(이 세션에서 앱 미실행).

## 7. 알려진 이슈 / 제약

| 항목 | 상태 |
|---|---|
| 일정 수정 딥링크 | `extra` 없이 열면 빈 폼(위 §5). id로 재조회하도록 개선 여지 |
| 정기 규칙 수정 | 백엔드가 미제공(삭제 후 재등록). 클라이언트도 수정 UI 없음 |
| 정기 규칙 상세 | `GET /recurring-rules/{id}`(회차 목록 포함)는 아직 화면 없음 — 목록 카드 + 캘린더로 충분하다고 판단 |
| 셋리스트 재정렬 | 여전히 추가·삭제만(재정렬 API는 있으나 UI 보류) |

## 8. 커밋 · CI

- 커밋: `feat(client): 일정 수정·승인/거절 + 정기 일정 규칙 등록/목록/삭제` (branch `feat/client-remaining`)
- 신규 의존성 없음.

## 9. 다음 단계 예고

**C8 — 설정 나머지**: 설정 허브 · 밴드 설정(일정 권한 모드·밴드장 위임·멤버 추방·밴드 나가기) ·
계정(내 정보·회원 탈퇴) · 차단 해제.
