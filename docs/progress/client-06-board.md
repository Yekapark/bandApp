# 클라이언트 C6 — 게시판 (사진/영상 피드)

## 1. 한 줄 요약

밴드 멤버가 합주 사진·영상을 공유하는 **게시판**을 만들었다 — 피드(무한 스크롤) ·
글 상세 · 글 작성/수정 · 첨부 업로드(R2 직접 업로드) · 신고 · 작성자 차단.
하단 탭바에 "게시판" 탭이 실제 화면으로 붙었다. **백엔드 변경 없음**(Phase 8 API 사용).

## 2. 이 단계의 목표

`docs/BACKLOG.md` §2 요구 화면 중 미착수였던 **#11 게시판(사진/영상 피드)**, **#12 게시글 상세**.
백엔드는 Phase 8(`docs/progress/phase-08-board-media-report.md`)에서 이미 완성돼 있었다.

## 3. 무엇을 만들었나

경로: `client/lib/features/board/`

| 파일 | 역할 |
|---|---|
| `data/board_models.dart` | `PostSummary`(목록 한 줄) · `PostPage`(커서 페이지) · `PostDetail`(본문+첨부) · `PostMedia` · `UploadTicket`. `MediaKind`(image/video) · `MediaState`(pending/ready/expired) enum |
| `data/board_repository.dart` | 목록/상세/작성/수정/삭제, 첨부 업로드(3단계), 첨부 삭제, 신고, 차단 |
| `application/board_providers.dart` | `boardFeedProvider`(밴드별 피드 + `loadMore`/`refresh`) · `postDetailProvider`(family) |
| `presentation/board_screen.dart` | 피드. 당겨서 새로고침 + 아래로 스크롤 시 다음 페이지 자동 로드. "글쓰기" FAB |
| `presentation/post_detail_screen.dart` | 제목·작성자·본문·첨부 갤러리. 우상단 ⋮ 메뉴(수정/삭제 또는 신고/차단). 이미지 탭 시 전체화면 뷰어 |
| `presentation/post_compose_screen.dart` | 작성/수정 겸용. 새 글은 등록 직후 같은 화면에서 첨부를 이어 올리게 수정 모드로 전환 |

라우팅(`client/lib/routing/`):

- `app_router.dart` — `StatefulShellRoute`에 **브랜치 3 = `/board`** 추가. 풀스크린 라우트
  `/board/new`, `/board/:postId/edit`(수정), `/board/:postId`(상세). 경로 매칭 순서상
  `new`·`edit`를 `:postId`보다 먼저 등록.
- `tab_shell.dart` — 하단 탭 "게시판"을 `showSoon` 스낵바 → 실제 브랜치로 승격.
  남은 `showSoon` 탭은 "정산" 하나(정산은 일정 상세에서 진입하는 화면이라 탭 자체는 보류).

### 첨부 업로드 흐름 (백엔드를 지나지 않음)

1. `image_picker`로 사진/영상 선택 → MIME·크기 검증(이미지 10MB / 영상 50MB / 글당 10개).
2. `POST .../media/upload-url` → presigned PUT URL 발급(`PENDING` 첨부 선생성).
3. **인터셉터 없는 별도 Dio**로 그 URL에 파일 바이트를 직접 PUT
   (Authorization 헤더가 붙으면 서명이 깨지므로 `_plain` Dio 사용).
4. `POST .../media/{id}/complete` → 백엔드가 R2 HEAD로 실제 크기·형식 확인 후 `READY` 확정.

## 4. 어떻게 동작하나

- **피드**: `boardFeedProvider(bandId)`가 첫 페이지를 로드하고, 스크롤이 끝에서 320px 이내로
  들어오면 `loadMore()`가 `nextCursor`로 다음 페이지를 받아 목록에 이어 붙인다.
  글 작성/수정/삭제, 차단 후에는 `refresh()`로 첫 페이지부터 다시 로드.
- **차단 관계**: 백엔드가 목록·상세에서 "내가 차단했거나 나를 차단한" 사용자의 글을 이미
  걸러 주므로 클라이언트는 별도 필터를 두지 않는다.
- **권한**: 상세 응답의 `editable`(작성자 본인 또는 밴드장)로 수정/삭제 메뉴를 노출.
  본인 글에는 신고/차단 메뉴를 숨긴다.

## 5. 직접 확인하는 법

사전 준비: 백엔드 실행(`docker compose up -d`), 클라이언트 실행은
`client-DEVLOG.md` §4 참조. 이 PC의 Flutter 경로는 `C:\src\flutter\bin`
(`& C:\src\flutter\bin\flutter.bat <명령>`).

```powershell
cd C:\band\bandApp\client
& C:\src\flutter\bin\flutter.bat analyze     # 에러 0
& C:\src\flutter\bin\flutter.bat test        # board_models_test 포함 전부 통과
& C:\src\flutter\bin\flutter.bat build web   # JS 빌드 성공(√ Built build\web)
```

앱에서:

1. 로그인 → 밴드 홈 → 하단 탭 **게시판**.
2. **글쓰기** → 제목·본문 입력 → **등록**. 화면이 "사진 추가" 모드로 바뀌면 ＋로
   사진/영상 첨부 → **완료**.
3. 피드에서 글 탭 → 상세. ⋮ → 수정/삭제(본인·밴드장) 또는 신고/차단(타인).
4. 스크롤을 내려 20개 이상이면 다음 페이지가 자동으로 붙는지 확인.

문제 해결:

- **첨부가 안 올라감**: 백엔드에 R2 키(`R2_*`)가 없으면 `upload-url`이 503
  `MEDIA_STORAGE_NOT_CONFIGURED`. 로컬에 R2 미설정이면 텍스트 글만 테스트한다.
- **웹에서 첨부 PUT 실패(CORS)**: R2 버킷 CORS에 개발 도메인이 없으면 브라우저가 PUT을
  막는다. 네이티브(Android/iOS)에서는 영향 없음. (알려진 제약 §7)
- **이미지가 깨져 보임**: presigned GET URL은 만료가 짧다. 상세를 다시 열면(=재조회) 갱신된다.

## 6. 검증 결과

- `flutter analyze` → **에러 0**. 신규 코드의 info/warning은 기존 코드와 동일 계열
  (`require_trailing_commas`, `unawaited_return_in_try_block`, `unawaited_futures`,
  `use_build_context_synchronously`). `dart format` 적용.
- `flutter test` → **7개 전부 통과**(신규 `test/board_models_test.dart` 4개 포함).
- `flutter build web` → JS 빌드 성공. (`flutter_secure_storage_web`의 Wasm dry-run
  경고는 기존과 동일 — 기본 JS 빌드에는 영향 없음)
- 백엔드 붙인 UI end-to-end는 **미검증**(로컬 R2 미설정 · 이 세션에서 앱 미실행).

## 7. 알려진 이슈 / 제약

| 항목 | 상태 |
|---|---|
| 영상 재생 | 상세에서 영상은 "재생 준비 중" 타일로만 표시(플레이어 미탑재). 이미지는 전체화면 뷰어 O |
| 웹 첨부 업로드 | R2 버킷 CORS 설정이 있어야 브라우저 PUT이 통과. 네이티브는 무관 |
| 미디어 신고 | 신고 대상은 현재 게시글(POST)·작성자(USER)만. 개별 첨부(MEDIA) 신고 UI 없음 |
| 차단 목록 관리 | 차단은 되지만 "차단 해제" 화면은 C8 설정에서 |
| 게시글 목록 썸네일 | 백엔드가 대표 이미지 1장만 `thumbnailUrl`로 준다(영상만 있으면 없음) |
| image_picker | 스펙에 없던 신규 의존성 — 사용자 승인받고 추가(2026-09-04) |

## 8. 커밋 · CI

- 커밋: `feat(client): 게시판(사진/영상 피드) — 피드·상세·작성/수정·첨부 업로드·신고·차단` (main)
- 신규 의존성: `image_picker: ^1.1.2`
- 클라이언트 CI(`flutter analyze`+`test`)는 C9에서 GitHub Actions로 추가 예정.

## 9. 다음 단계 예고

**C7 — 일정 상세 보강 + 정기 일정**: 일정 수정(PUT) · 밴드장 승인/거절 버튼 ·
정기 일정 규칙 등록/목록/삭제(Phase 5 API).
