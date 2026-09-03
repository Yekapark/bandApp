# 클라이언트 3단계 — 일정 정산(N빵)

## 1. 한 줄 요약

일정 상세에서 들어가는 **정산 화면**을 만들었다. 총비용을 멤버 몫으로 나누고
(전원 균등 / 참석자만), 각자 납부 여부를 셀프 체크하며, 멤버·참석자가 바뀌면 재계산한다.
Phase 7 정산 API에 그대로 연결.

## 2. 범위

`client-DEVLOG.md` §5 "다음 할 일"의 정산 항목. 캘린더 2단계(PR #34)에 이어지는 조각으로,
**별도 브랜치·PR**(`feat/client-settlement`).

- 뺀 것: 정산 생성/재계산 시 총액·분배방식을 한 화면에서 편집하는 UI는 최소화
  (생성은 폼, 재계산은 "값 유지 + 사람만 다시 반영" 한 버튼). 미납자 알림 보내기(목업의 버튼)는
  알림 화면 단계로.

## 3. 무엇을 만들었나 (`client/lib/features/settlement/`)

| 파일 | 내용 |
|---|---|
| `data/settlement_models.dart` | `SplitType`(equal/attendeesOnly), `SettlementShare`(userId·name·role·amount·paid), `Settlement`(총액·분배방식·집계·shares, `paidRatio`/`shareOf`) |
| `data/settlement_repository.dart` | `get`(없으면 **null** = 404 SETTLEMENT_NOT_FOUND), `create`, `recalculate`, `markPaid` |
| `application/settlement_providers.dart` | `settlementProvider` family `(bandId, reservationId)` → `Settlement?` |
| `presentation/settlement_screen.dart` | `/reservations/:rid/settlement` |

### 화면 동작

- **정산 없음** → 생성 폼: 총액(일정의 `cost`가 있으면 미리 채움) + 분배 방식 토글
  (멤버 전원 / 참석자만). 등록자·밴드장만 "정산 만들기"(그 외에는 안내 문구, 백엔드도 403).
- **정산 있음** → 현황:
  - 퍼플 그라디언트 카드 — "1인당" 금액(나머지가 있으면 `~` 표기), 총액·인원, 납부 진행바,
    "N/M명 납부 · 남은 ₩X".
  - 납부 체크리스트 — 각 멤버 몫. **본인 행만** 탭해서 납부 토글(`PUT .../shares/{내 id}`).
  - 등록자·밴드장이면 "멤버·참석자 바뀜 → 재계산"(확인 후 `POST .../recalculate`, 값 유지).
- 참석/납부 변경은 응답으로 받은 현황으로 화면을 갱신(낙관적, 재조회 없음).

### 연결

- `routing/app_router.dart` — `/reservations/:rid/settlement` 라우트, `Routes.settlement(id)`.
- `reservation_detail_screen.dart` — 멤버별 참석 현황 아래에 "정산 (N빵) 보기 · 만들기" 링크 →
  정산 화면으로 `push`.

## 4. 검증

- `flutter analyze` 에러 0(경고 20 = 기존 repository의 `unawaited_return_in_try_block` 패턴,
  info ~98). `flutter build web` 성공.
- **런타임 스모크**(`docker compose`, 로컬 백엔드): 신규 유저 → 밴드 → 합주실 → 일정(cost 90000) →
  `GET settlement` 404 → `POST` EQUAL 생성(shareCount 1, 90000) → `PUT shares/{me}` paid
  (paidCount 1, outstanding 0) → `POST recalculate` (paid 유지) 모두 정상.
- **미검증**: 클라 UI를 실제로 클릭한 end-to-end(웹/에뮬). 멤버 2명 이상일 때 나머지 1원 분배 표시.

## 5. 직접 확인하는 법

1. 일정 상세 화면 → "정산 (N빵) 보기 · 만들기".
2. (정산 없음) 총액 입력 → 분배 방식 선택 → "정산 만들기".
3. (정산 있음) 내 행을 탭 → 체크 표시 + 진행바·"남은" 금액 갱신.
4. 등록자/밴드장 계정: 참석 응답을 바꾼 뒤 "재계산" → 몫 재분배, 기존 납부 체크 유지.

문제 해결
- **"정산은 일정을 등록한 사람이나 밴드장이…"**: 정상 — 생성 권한 없음(백엔드 `NOT_SETTLEMENT_MANAGER`).
- **내 행이 안 눌림**: 그 행이 본인 몫이 아님. 본인 몫만 셀프 체크 가능.

## 6. 알려진 이슈 / 제약

| 목업 | 실제 구현 |
|---|---|
| "1인당" 단일 금액 | 나머지 1원은 밴드장이 더 냄 → 값이 다를 수 있어 `약 ₩X ~` 로 표기, 실제 몫은 리스트에 |
| 미납 멤버에게 알림 보내기 버튼 | 제외 — 알림 화면 단계 |
| 총액/분배방식 인라인 편집 | 생성 폼에서만. 이후 변경은 재계산(사람만) — 총액 변경 UI는 추후 |
| "참석자만" 분배에서 참석자 0명 | 백엔드 409 `SETTLEMENT_NO_ATTENDEES` → 스낵바로 노출(전용 안내는 아님) |

## 7. 커밋 · CI

- PR: (이 문서와 함께 생성) — `feat/client-settlement` → `main`.
- 백엔드 변경 없음(Phase 7 API 그대로 사용). 클라이언트 CI 워크플로는 아직 없음.

## 8. 다음 단계 예고

- 하단 탭바 `ShellRoute` 전환.
- 합주실 지도(`/map`) — 지도 SDK/키 결정 필요.
- 카카오 로그인 SDK — 네이티브 키·설정 필요.
- 알림 화면 + 미납 리마인더.
