# 클라이언트 C15 — 미디어 신고 · 캘린더 취소건 표시 · 초대 링크 프리필

## 1. 한 줄 요약

갭 백로그의 자잘한 3가지를 채웠다: 게시판 **개별 첨부(미디어) 신고**,
캘린더에서 **취소·거절된 일정 보기 토글**, **초대 링크(`?code=`)로 열면 코드 자동 입력**.
백엔드 변경 없음.

## 2. 무엇을 만들었나

### 15a. 미디어 신고 (`14. 신고`)
- `post_detail_screen.dart` `_MediaBlock` 을 `GestureDetector(onLongPress: onReport)` 로 감쌌다.
- 첨부를 **길게 누르면** 기존 신고 다이얼로그(`_report`)가 `targetType: 'MEDIA', targetId: media.id` 로 열린다.
- 미디어 목록 아래에 "첨부를 길게 누르면 신고할 수 있어요" 안내.

### 15b. 캘린더 취소건 표시
- `calendar_providers.dart` 에 `showCancelledReservationsProvider`(`Notifier<bool>`, 기본 false).
  `monthReservationsProvider` 가 이를 watch 해서 `list(..., includeInactive: on)` 호출.
- `calendar_screen.dart` 날짜 헤더 우측에 "취소 포함" 토글 칩.
- `_DayReservationTile`: 비활성(취소·거절) 건은 회색 + 합주실명 취소선, 왼쪽 바·상태 라벨도 흐리게.

### 15c. 초대 링크 프리필
- `join_band_screen.dart` 에 `initialCode` 파라미터 — `A-Z0-9` 만 남기고 8자로 잘라 입력칸에 프리필.
- `app_router.dart` 의 `/band-gate/join` 라우트가 `?code=` 쿼리를 읽어 전달.
- **OS 딥링크(Android intent-filter / iOS associated domains) 네이티브 등록은 후속** —
  `client/android`·`ios` 가 gitignore 라 별도 작업. `app_links` 패키지 도입 여부는 그때 결정.
  지금은 `https://.../band-gate/join?code=XXXX` 형태로 열리면 동작.

## 3. 직접 확인하는 법

```powershell
cd C:\band\bandApp\client
& C:\src\flutter\bin\flutter.bat analyze   # 에러 0
& C:\src\flutter\bin\flutter.bat test      # 22개 통과
& C:\src\flutter\bin\flutter.bat build web # √ Built build\web
```

- 게시글 상세 → 사진/영상 길게 누르기 → 신고 사유 입력 → 접수.
- 캘린더 → "취소 포함" 체크 → 취소된 일정이 취소선으로 나타남.
- 웹에서 `http://localhost:<port>/band-gate/join?code=ABCD2345` 로 접속 → 코드가 채워져 있음.

## 4. 검증 결과

- `flutter analyze` 에러 0 · `flutter test` 22개 · `flutter build web` 성공. `dart format` 적용.
- end-to-end 미검증.

## 5. 알려진 제약 / 후속

- OS 레벨 딥링크(앱 설치 시 링크로 앱이 열리는 것) 네이티브 설정은 안 됨 — 쿼리 프리필만.
- 미디어 신고는 길게 누르기만(전용 버튼 없음).

## 6. 커밋

`feat(client): 미디어 신고 · 캘린더 취소건 토글 · 초대 링크 코드 프리필` (branch `feat/client-remaining`)
