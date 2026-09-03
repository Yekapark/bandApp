# 클라이언트 C13 — 정기 일정 규칙 상세 (회차 목록)

## 1. 한 줄 요약

정기 일정 규칙 카드를 탭하면 **규칙 요약 + 다가오는 회차 목록**을 보는 상세 화면이 뜬다.
회차를 탭하면 일반 일정 상세로 이동한다. 백엔드 변경 없음
(`GET /bands/{id}/recurring-rules/{ruleId}`).

## 2. 무엇을 만들었나

| 파일 | 역할 |
|---|---|
| `features/recurring/data/recurring_models.dart` | `RecurringRuleDetail{rule, occurrenceCount, occurrences: List<Reservation>}` |
| `features/recurring/data/recurring_repository.dart` | `detail({bandId, ruleId})` |
| `features/recurring/application/recurring_providers.dart` | `recurringRuleDetailProvider` family(`(bandId, ruleId)`) |
| `features/recurring/presentation/recurring_detail_screen.dart` | `/cal/recurring/:ruleId` — 규칙 요약(주기·요일·시간·합주실·기간·메모) + 회차 리스트 + 삭제 |
| `recurring_list_screen.dart` | 규칙 카드에 `onTap` → 상세 |
| `routing/app_router.dart` | `/cal/recurring/:ruleId`(`new` 다음에 등록), `Routes.recurringDetail()` |

## 3. 어떻게 동작하나

- 백엔드는 오늘 − horizonWeeks 이후의 회차만(취소 포함, `start_at` 오름차순) 준다.
  그 이전 회차는 화면 안내대로 캘린더에서 본다.
- 취소·거절된 회차는 회색 + 취소선. 회차 탭 → `/reservations/:id` (일반 일정 상세) →
  거기서 개별 수정·취소 가능(규칙은 유지).
- 삭제(등록자/밴드장): 목록 화면과 같은 로직 — `DELETE .../recurring-rules/{ruleId}` →
  `recurringRulesProvider`·`monthReservationsProvider`·`upcomingReservationsProvider` 무효화 → 목록으로 pop.

## 4. 직접 확인하는 법

```powershell
cd C:\band\bandApp\client
& C:\src\flutter\bin\flutter.bat analyze   # 에러 0
& C:\src\flutter\bin\flutter.bat test      # 19개 통과
& C:\src\flutter\bin\flutter.bat build web # √ Built build\web
```

앱: 캘린더 AppBar ↻ → 정기 일정 목록 → 규칙 카드 탭 → 회차 목록 확인 → 회차 탭 →
일반 일정 상세로 이동. 상세에서 "정기 일정 삭제"도 가능.

## 5. 검증 결과

- `flutter analyze` 에러 0 · `flutter test` 19개(신규 `RecurringRuleDetail` 1개) · `flutter build web` 성공.
- end-to-end 미검증.

## 6. 알려진 제약

- 규칙 **수정** UI 없음(백엔드 미제공 — 삭제 후 재등록).
- 회차 목록은 "다가오는 8주"만. 과거 이력은 캘린더로.

## 7. 커밋

`feat(client): 정기 일정 규칙 상세(회차 목록) 화면` (branch `feat/client-remaining`)
