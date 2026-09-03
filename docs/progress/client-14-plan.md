# 클라이언트 C14 — 밴드 요금제 (FREE / PREMIUM)

## 1. 한 줄 요약

밴드 요금제 화면을 추가했다 — 현재 티어 표시, FREE/PREMIUM 비교(미디어 보관기한),
밴드장의 **구독 시작 / 해지 / 연장**. Phase 10 전체가 클라에 없던 상태였다.
백엔드 변경 없음(`16. 요금제`). **실제 결제 연동은 없음** — 백엔드가 no-op 게이트웨이라 버튼만.

## 2. 무엇을 만들었나

경로: `client/lib/features/plan/`

| 파일 | 역할 |
|---|---|
| `data/plan_models.dart` | `BandPlan{tier, mediaRetentionDays?, startedAt, expiresAt?}` (+ `isPremium`, `retentionLabel`) |
| `data/plan_repository.dart` | `view()`, `subscribe()`, `cancel()`, `renew()` |
| `application/plan_providers.dart` | `bandPlanProvider` family(bandId) |
| `presentation/plan_screen.dart` | `/settings/plan` — 현재 요금제 카드, FREE/PREMIUM 비교표, 전환 버튼(밴드장) |
| `routing/app_router.dart` | `Routes.plan = '/settings/plan'` |

**진입점**: 설정 허브 → "요금제".

## 3. 어떻게 동작하나

- `GET /bands/{id}/plan` → 티어·미디어 보관일수(FREE=30, PREMIUM=null=무제한)·구독기간 종료.
- 밴드장:
  - FREE → "PREMIUM 시작" (`POST .../plan/subscribe`) — 확인 다이얼로그(보관기한 무제한 안내)
  - PREMIUM → "구독기간 연장"(`.../renew`) / "PREMIUM 해지"(`.../cancel`, 30일 유예 안내)
  - 성공 시 `bandPlanProvider` 무효화 → 카드 갱신
- 일반 멤버: 조회만, "밴드장만" 안내.
- 서버 예외(`PLAN_ALREADY_PREMIUM`/`PLAN_ALREADY_FREE`/`PAYMENT_FAILED`)는 메시지 그대로 노출.

## 4. 직접 확인하는 법

```powershell
cd C:\band\bandApp\client
& C:\src\flutter\bin\flutter.bat analyze   # 에러 0
& C:\src\flutter\bin\flutter.bat test      # 22개 통과(plan_models_test 3개 포함)
& C:\src\flutter\bin\flutter.bat build web # √ Built build\web
```

앱: 밴드장 → 설정 → 요금제 → "PREMIUM 시작" → 카드가 PREMIUM 으로, 보관기한 "무제한" →
"PREMIUM 해지" → 다시 FREE.

## 5. 검증 결과

- `flutter analyze` 에러 0 · `flutter test` 22개 · `flutter build web` 성공. `dart format` 적용.
- end-to-end 미검증.

## 6. 알려진 제약

- 실제 결제(스토어 인앱결제) 연동 없음 — 이번 릴리스는 버튼만으로 전환.
- 미디어 화면에 "FREE 30일 보관" 배지는 아직 없음(요금제 화면에서만 안내).

## 7. 커밋

`feat(client): 밴드 요금제(FREE/PREMIUM) 화면` (branch `feat/client-remaining`)
