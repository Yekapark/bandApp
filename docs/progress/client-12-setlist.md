# 클라이언트 C12 — 셋리스트 재정렬 + 곡 수정

## 1. 한 줄 요약

일정 상세의 셋리스트에서 **드래그로 순서 변경**과 **곡 정보 수정**(곡명·아티스트·참고 링크)을
할 수 있게 했다. 그동안 추가·삭제만 됐다. 백엔드 변경 없음(`10. 셋리스트`의
`PUT .../setlist/reorder`, `PUT .../setlist/{itemId}`).

## 2. 무엇을 만들었나

| 파일 | 바뀐 것 |
|---|---|
| `features/reservation/data/reservation_repository.dart` | `updateSetlistItem({...title, artist?, referenceUrl?})`, `reorderSetlist({itemIds})` |
| `reservation_detail_screen.dart` `_SetlistBlock` | `StatelessWidget` → `StatefulWidget`. 곡이 2개 이상이면 `ReorderableListView`(드래그 핸들, `onReorderItem`). 각 행 탭 → 수정 다이얼로그 |
| `reservation_detail_screen.dart` `_AddSongDialog` → `_SongDialog` | 추가/수정 겸용. 참고 링크(`referenceUrl`) 입력칸 추가 |
| `_ReservationDetailScreenState` | `_editSong()`, `_reorderSetlist()` 추가. `_addSong` 이 referenceUrl 전달 |

## 3. 어떻게 동작하나

- **재정렬**: 드래그로 놓으면 `_SetlistBlock` 이 로컬 순서를 즉시 갱신(애니메이션)하고
  새 id 순서를 `onReorder` 로 전달 → `PUT .../setlist/reorder {itemIds}` → 성공 시 `reservationDetailProvider`
  무효화. 실패하면 무효화로 서버 순서로 되돌린다. `didUpdateWidget` 이 id 순서가 달라졌을 때만
  로컬 목록을 다시 맞춰 add/delete/외부 새로고침과 충돌하지 않는다.
- **수정**: 행 탭 → `_SongDialog(initial: item)` → `PUT .../setlist/{id}` → 무효화.
- 활성 일정(`editable`)일 때만. 취소·거절된 일정은 읽기 전용.

## 4. 직접 확인하는 법

```powershell
cd C:\band\bandApp\client
& C:\src\flutter\bin\flutter.bat analyze   # 에러 0
& C:\src\flutter\bin\flutter.bat test      # 18개 통과
& C:\src\flutter\bin\flutter.bat build web # √ Built build\web
```

앱: 일정 상세 → 셋리스트에 곡 2개 이상 추가 → 우측 드래그 핸들로 순서 변경 →
새로고침 후에도 순서 유지. 곡을 탭 → 곡명·아티스트·참고 링크 수정.

## 5. 검증 결과

- `flutter analyze` 에러 0(`ReorderableListView.onReorder` deprecation 은 `onReorderItem` 으로 회피).
- `flutter test` 18개 · `flutter build web` 성공. `dart format` 적용. end-to-end 미검증.

## 6. 알려진 제약

- 참고 링크는 저장·표시만(인앱 열기는 `url_launcher` 필요 → 미도입).
- 재정렬은 낙관적 업데이트 — 서버 실패 시 잠깐 어긋났다가 되돌아온다.

## 7. 커밋

`feat(client): 셋리스트 재정렬(드래그) + 곡 정보 수정` (branch `feat/client-remaining`)
