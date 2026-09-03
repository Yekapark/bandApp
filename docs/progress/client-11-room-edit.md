# 클라이언트 C11 — 합주실 수정/삭제

## 1. 한 줄 요약

합주실을 **수정·삭제**할 수 있게 했다. 그동안 등록만 가능했다.
백엔드 변경 없음(`3. 합주실`의 `PUT`/`DELETE /bands/{id}/rooms/{roomId}`).

## 2. 무엇을 만들었나

| 파일 | 바뀐 것 |
|---|---|
| `features/reservation/data/room_repository.dart` | `update({roomId, name, address?, phone?, memo?})`, `delete({roomId})` 추가 |
| `features/reservation/presentation/room_form_screen.dart` | `existing`(Room) 파라미터로 **수정 모드 겸용**. 프리필, 문구 분기, `_isEdit` 이면 `update` 호출 |
| `features/reservation/presentation/widgets/room_picker_sheet.dart` | 각 합주실 타일에 ⋯ → 수정/삭제. 삭제 확인 다이얼로그 |
| `features/reservation/presentation/map_screen.dart` | 목록 타일에도 ⋯ → 수정/삭제 (`_RoomList` 에 `onEdit`/`onDelete` 스레드) |
| `routing/app_router.dart` | `/cal/rooms/:roomId/edit`(extra 로 Room), `Routes.editRoom()` |

## 3. 어떻게 동작하나

- 수정: 시트/지도의 ⋯ → "수정" → `/cal/rooms/:id/edit` (Room 을 `extra` 로) → 폼이 값 채워짐 →
  저장 시 `PUT` → `roomsProvider` 무효화 → 목록 갱신. 주소가 실제로 바뀌면 서버가 좌표 재계산.
- 삭제: ⋯ → "삭제" → 확인 다이얼로그(이미 등록된 일정엔 영향 없음 안내) → `DELETE` → 목록에서 사라짐.

## 4. 직접 확인하는 법

```powershell
cd C:\band\bandApp\client
& C:\src\flutter\bin\flutter.bat analyze   # 에러 0
& C:\src\flutter\bin\flutter.bat test      # 18개 통과
& C:\src\flutter\bin\flutter.bat build web # √ Built build\web
```

앱: 일정 등록 → 합주실 선택 시트 → 합주실 ⋯ → 수정(이름·메모 바꿔 저장) / 삭제.
지도 탭의 목록에서도 동일.

## 5. 검증 결과

- `flutter analyze` 에러 0 · `flutter test` 18개 · `flutter build web` 성공. `dart format` 적용.
- end-to-end 미검증.

## 6. 알려진 제약

- 삭제 권한(등록자/밴드장)은 서버가 판단 — 권한 없으면 서버 메시지를 그대로 노출.
- 수정 화면을 딥링크로 바로 열면 `extra`(Room) 가 없어 빈 폼. 정상 경로(시트/지도의 ⋯)로 진입하면 프리필.

## 7. 커밋

`feat(client): 합주실 수정/삭제` (branch `feat/client-remaining`)
