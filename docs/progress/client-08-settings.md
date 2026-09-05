# 클라이언트 C8 — 설정 (밴드 · 계정 · 차단 해제)

## 1. 한 줄 요약

설정 화면을 완성했다 — **설정 허브** + **밴드 설정**(일정 등록 권한 모드 · 멤버 관리 ·
밴드 나가기) + **계정**(내 정보 · 회원 탈퇴) + **차단한 사용자**(목록 · 해제) + 로그아웃.
**백엔드 변경 없음**(Phase 1~3 · 8 API).

## 2. 이 단계의 목표

`docs/BACKLOG.md` §2 #13 "설정" 중 알림 외 나머지: 밴드 설정, 계정 관리, 차단 해제.
관련 백엔드는 전부 존재 — 밴드 설정/위임(`3. 밴드`), 멤버 추방/탈퇴(`5. 밴드 멤버`),
회원 탈퇴(`2. 내 계정`), 차단 목록/해제(`15. 사용자 차단`).

## 3. 무엇을 만들었나

경로: `client/lib/features/settings/`

| 파일 | 역할 |
|---|---|
| `presentation/settings_home_screen.dart` | `/settings` 허브 — 알림 / 밴드 설정 / 차단한 사용자 / 계정 / 로그아웃 |
| `presentation/band_settings_screen.dart` | `/settings/band` — 일정 등록 권한 모드(라디오), 멤버 목록(밴드장 위임·추방), 밴드 나가기 |
| `presentation/account_screen.dart` | `/settings/account` — 이름·로그인 방식·이메일 표시, 회원 탈퇴(이메일 계정은 비밀번호 재확인) |
| `presentation/blocked_users_screen.dart` | `/settings/blocks` — 차단 목록 + 해제 |
| `application/settings_providers.dart` | `blockedUsersProvider`(`GET /users/me/blocks`) |

리포지토리·컨트롤러 추가:

| 파일 | 추가 |
|---|---|
| `features/band/data/band_repository.dart` | `band()`(GET /bands/{id}), `updateSettings()`, `delegateLeadership()`, `kickMember()`, `leaveBand()` |
| `features/band/application/band_providers.dart` | `bandDetailProvider`(family), `reservationPermissionLabel/Hint()` |
| `features/board/data/board_repository.dart` | `listBlocks()`, `unblock()` |
| `features/board/data/board_models.dart` | `BlockedUser` |
| `features/auth/data/auth_repository.dart` | `withdraw({password})`(POST /users/me/withdraw) |
| `features/auth/application/auth_controller.dart` | `withdraw()`(성공 시 로컬 세션 정리 → 로그인 화면으로 redirect), `isEmailAccount` |

진입점: **홈 헤더의 톱니(⚙) 아이콘** — 기존 "멤버" 사각 버튼을 설정 진입으로 교체.
멤버 관리는 설정 → 밴드 설정 안에 있다. 홈의 마지막 `showSoon`("멤버 관리")이 사라졌다.

## 4. 어떻게 동작하나

- **일정 등록 권한**: `bandDetailProvider`로 현재 모드를 읽고, 밴드장이 다른 모드를 탭하면
  `PUT /bands/{id}/settings` → provider 무효화. 일반 멤버는 읽기 전용.
- **밴드장 위임**: `POST /bands/{id}/leader {newLeaderUserId}` → 나는 MEMBER 로 강등.
  `bandMembersProvider`·`bandDetailProvider`·`myBandsProvider` 무효화 → 화면에서 밴드장 배지가 옮겨간다.
- **추방**: `DELETE /bands/{id}/members/{userId}`.
- **밴드 나가기**: `POST /bands/{id}/members/leave`. 밴드장이면 서버가 409
  `LEADER_MUST_DELEGATE_BEFORE_LEAVING` — 메시지를 그대로 노출(다이얼로그에서도 미리 안내).
  성공 시 `selectedBandIdProvider` 클리어 + `myBandsProvider` 무효화 + `/home`으로.
- **회원 탈퇴**: 이메일 계정은 비밀번호 필드가 있는 다이얼로그(`POST /users/me/withdraw {password}`),
  소셜 계정은 확인만(`POST /users/me/withdraw {}`). 성공 시 `AuthController.withdraw`가
  토큰을 지우고 상태를 `signedOut`으로 → go_router redirect 가 로그인 화면으로 보낸다.
- **차단 해제**: `DELETE /users/me/blocks/{id}` → `blockedUsersProvider` 무효화.

## 5. 직접 확인하는 법

```powershell
cd C:\band\bandApp\client
& C:\src\flutter\bin\flutter.bat analyze     # 에러 0
& C:\src\flutter\bin\flutter.bat test        # 16개 통과
& C:\src\flutter\bin\flutter.bat build web   # √ Built build\web
```

앱에서:

1. 홈 우상단 ⚙ → 설정 허브.
2. **밴드 설정**(밴드장 계정): 권한 모드를 `ANYONE`↔`APPROVAL_REQUIRED`로 바꿔 보고,
   멤버 ⋯ → "밴드장 위임" / "내보내기".
3. **계정** → "회원 탈퇴". 이메일 계정이면 비밀번호를 요구하고, 틀리면 서버 메시지가 뜬다.
4. **차단한 사용자**: 게시판에서 누군가를 차단한 뒤 여기서 "해제".

문제 해결:

- 밴드장이 "밴드 나가기"를 누르면 409 → 먼저 위임하라는 메시지. 정상 동작.
- 권한 모드 변경이 403 `NOT_BAND_LEADER`면 일반 멤버 계정. 밴드장으로 로그인.
- 탈퇴 후에도 화면이 안 넘어가면 redirect 리스너 문제 — `flutter run` 재시작으로 확인.

## 6. 검증 결과

- `flutter analyze` → **에러 0**(전체 249 issues, 전부 기존과 동일 계열 info: `require_trailing_commas`,
  `unawaited_futures`, `use_build_context_synchronously`). `dart format` 적용.
- `flutter test` → **16개 전부 통과**(신규 `BlockedUser` 케이스 2개 포함).
- `flutter build web` → JS 빌드 성공.
- 백엔드 붙인 end-to-end 는 **미검증**.

## 7. 알려진 이슈 / 제약

| 항목 | 상태 |
|---|---|
| 밴드 이름 변경 | 백엔드 미제공 → UI 없음 |
| 밴드 삭제 | ✅ 구현 (2026-09-05). 밴드장에게만 보이고, 밴드 이름을 정확히 입력해야 버튼이 살아난다. 일정·정산·게시글·사진/영상(R2 포함)·합주실·셋리스트·정기규칙·초대·요금제·알림이력을 전부 지운다 |
| 초대코드 재발급/조회 | 이 화면엔 없음(온보딩 `join` 쪽만). 밴드 설정에 넣을지는 추후 |
| 프로필 편집(이름·아바타) | 백엔드에 수정 API 없음 → 표시만 |
| 알림 설정 | 기존 `/settings/notifications` 화면을 허브에서 링크만 |

## 8. 커밋 · CI

- 커밋: `feat(client): 설정 — 밴드 설정(권한·멤버 관리)·계정(탈퇴)·차단 해제·설정 허브` (branch `feat/client-remaining`)
- 신규 의존성 없음.

## 9. 다음 단계 예고

**C9 — 알림 수신부(FCM) + 클라이언트 CI**: `firebase_core`+`firebase_messaging` 로
디바이스 토큰을 받아 `POST /notifications/device-tokens` 등록(설정 파일 없으면 no-op),
포그라운드 메시지 처리. GitHub Actions 에 `flutter analyze` + `flutter test` 워크플로 추가.
