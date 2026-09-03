# 클라이언트 C10 — 멤버 초대 (초대코드 발급/조회/무효화)

## 1. 한 줄 요약

밴드장이 **초대코드·공유 링크를 발급/재발급/무효화**하는 "멤버 초대" 화면을 추가했다.
그동안 코드를 **입력해서 참여**하는 쪽(`/band-gate/join`)만 있어서 초대 플로우가 끊겨 있었다.
백엔드 변경 없음(`4. 초대` API).

## 2. 무엇을 만들었나

| 파일 | 역할 |
|---|---|
| `features/band/data/invite_models.dart` | `BandInvite{code, link, expiresAt?, maxUses?, usedCount, revoked}` (+ `isUnlimited`, `remainingUses`) |
| `features/band/data/invite_repository.dart` | `current()`(404→null), `issue({maxUses?, ttlDays?})`, `revoke()` |
| `features/band/application/invite_providers.dart` | `currentInviteProvider` family(bandId) |
| `features/band/presentation/invite_screen.dart` | `/band/invite` — 코드/링크 표시, 복사, 재발급(확인), 무효화(확인). 밴드장 아니면 안내만 |
| `routing/app_router.dart` | `Routes.invite = '/band/invite'` |

**진입점**: ① 설정 허브 → "멤버 초대", ② 밴드 설정 → 멤버 섹션 헤더의 "초대" 버튼.

## 3. 어떻게 동작하나

- `GET /bands/{id}/invites/current` — 없으면 404 `INVITE_NOT_FOUND` → repo 가 null 로 변환 →
  화면은 "초대코드 만들기" 상태.
- 코드가 있으면: 8자 코드(모노스페이스 크게) + "무제한/남은 N회" + 만료일, "코드 복사"/"링크 복사"
  (`Clipboard`, `share_plus` 미도입), "코드 새로 발급"(재발급 시 기존 코드 무효화 경고), "무효화".
- `POST /bands/{id}/invites` (본문 생략 → 만료 7일·무제한), `DELETE …/invites/current`.
- 전부 밴드장만 — 비밴드장은 화면 진입 시 안내 문구만.

## 4. 직접 확인하는 법

```powershell
cd C:\band\bandApp\client
& C:\src\flutter\bin\flutter.bat analyze   # 에러 0
& C:\src\flutter\bin\flutter.bat test      # 18개 통과(invite_models_test 2개 포함)
& C:\src\flutter\bin\flutter.bat build web # √ Built build\web
```

앱: 밴드장 계정 → 설정 → 멤버 초대 → "초대코드 만들기" → 코드/링크 복사 → 다른 계정에서
`/band-gate/join` 에 코드 입력해 참여되는지. 재발급 후 이전 코드로는 안 되는지.

## 5. 검증 결과

- `flutter analyze` 에러 0 · `flutter test` 18개 통과 · `flutter build web` 성공. `dart format` 적용.
- 백엔드 붙인 end-to-end 미검증.

## 6. 알려진 제약

- 만료일수·사용횟수 옵션은 UI 노출 안 함(기본값: 7일·무제한)으로 발급. 필요 시 폼 추가.
- "링크 공유"는 클립보드 복사만(OS 공유 시트는 `share_plus` 필요 → 미도입).

## 7. 커밋

`feat(client): 멤버 초대 화면 — 초대코드 발급/재발급/무효화` (branch `feat/client-remaining`)
