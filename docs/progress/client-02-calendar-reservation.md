# 클라이언트 2단계 — 예약 캘린더 · 합주실 · 일정 등록 · 일정 상세

## 1. 한 줄 요약

밴드 홈 다음 화면들을 만들었다: **월간 캘린더 → 합주실 선택/등록 → 일정 등록 폼
(겹침 경고) → 일정 상세(참석 체크·멤버별 현황·셋리스트)**. 모두 실제 백엔드 API에 연결.

## 2. 이 단계의 범위

`client-DEVLOG.md` §5 "다음 할 일" 1~3번을 구현했다.

- 예약 캘린더 `/cal`
- 합주실 목록/등록 (일정 폼에서 필요)
- 일정 등록 폼 `/cal/new`
- 일정 상세 `/reservations/:id` (RSVP + 셋리스트)
- 홈의 "다음 단계에서 구현" 안내(스낵바)를 실제 화면 이동으로 교체

**이번 범위에서 뺀 것**

- **정기(반복) 일정 등록** — 목업엔 있으나 백엔드 `POST /reservations`에 반복 필드가
  없고 정기 규칙 생성 API가 없다(`recurringRuleId`는 응답에만 존재). 백엔드 작업이
  선행돼야 함 → 열린 결정에 기록.
- 지도, 정산, 게시판, 알림, 멤버 관리 화면 — 다음 단계.
- 일정 **수정**(PUT), 밴드장 **승인/거절** 버튼 — 상세 화면엔 취소만 넣음.

## 3. 무엇을 만들었나

경로는 모두 `client/lib/` 하위.

### 데이터 계층 (`features/reservation/data/`)

| 파일 | 내용 |
|---|---|
| `reservation_models.dart` (확장) | `OverlapWarning`, `ReservationWriteResult`(등록/수정 응답 = 일정 + 겹침 목록), `AttendanceStatus`/`AttendanceEntry`/`AttendanceBoard`, `SetlistItem`/`Setlist`, `ReservationDetail`(상세 = 일정 + 참석 + 셋리스트). 상태 라벨 헬퍼(`reservationStatusLabel`, `attendanceStatusLabel`, `attendanceStatusWire`) 추가 |
| `reservation_repository.dart` (확장) | `detail`(GET 상세), `create`(POST 등록), `cancel`(DELETE), `respondAttendance`(PUT 내 참석), `addSetlistItem`/`deleteSetlistItem` |
| `room_models.dart` (신규) | `Room` — id·이름·주소·연락처·메모·usageCount, 목록 부제(`subtitle`) 계산 |
| `room_repository.dart` (신규) | `list`(GET, usageCount 내림차순), `create`(POST) |

### 상태 (`features/reservation/application/calendar_providers.dart`)

- `calendarMonthProvider` (`Notifier<DateTime>`) — 캘린더가 보는 달. `prev()`/`next()`/`jumpTo()`.
- `monthReservationsProvider` (`FutureProvider.family`, key = `(bandId, month)`) —
  그 달 그리드(6주=42칸)에 걸치는 활성 일정. 내부적으로 `GET /reservations?from&to`.
- `roomsProvider` (`FutureProvider.family<List<Room>, int>`) — 밴드 합주실 목록.
- `reservationDetailProvider` (`FutureProvider.family`, key = `(bandId, reservationId)`).

### 화면 (`features/reservation/presentation/`)

| 화면 | 라우트 | 파일 | 백엔드 |
|---|---|---|---|
| 예약 캘린더 | `/cal` | `calendar_screen.dart` | `GET /bands/{id}/reservations?from&to` |
| 일정 등록 폼 | `/cal/new?date=YYYY-MM-DD` | `reservation_form_screen.dart` | `POST /bands/{id}/reservations` |
| 합주실 등록 폼 | `/cal/rooms/new` | `room_form_screen.dart` | `POST /bands/{id}/rooms`, `GET /bands/{id}/rooms/search?query=` |
| 합주실 선택 시트 | (모달) | `widgets/room_picker_sheet.dart` | `GET /bands/{id}/rooms` |
| 일정 상세 | `/reservations/:rid` | `reservation_detail_screen.dart` | `GET …/{rid}`, `PUT …/attendances/{uid}`, `POST/DELETE …/setlist`, `DELETE …/{rid}` |

- **캘린더**: 월 이동 화살표, 요일 헤더(일~토), 6주 그리드. 일정 있는 날에 점,
  오늘은 테두리, 선택일은 채움. 선택일 아래에 그 날 일정 리스트(탭 → 상세) +
  "＋ 이 날짜에 합주 등록"(선택일을 쿼리로 폼에 전달).
- **일정 등록 폼**: 합주실(시트로 선택, 없으면 시트에서 바로 등록 폼으로),
  날짜(`showDatePicker`)·시작 시간(`showTimePicker`)·이용 시간(± 0.5h 스테퍼),
  예약 메모, 비용(선택). 비용 입력 시 "1인당(멤버 균등)"을 `MyBand.memberCount`로 즉시 계산해 표시.
  등록 성공 후 응답 `overlaps`가 있으면 다이얼로그로 "겹치는 일정이 있어요(등록은 완료)"를
  안내하고, 확인하면 상세 화면으로 `pushReplacement`.
- **합주실 등록 폼**: 이름·주소·연락처·메모. 주소 칸은 **네이버 지역검색**과 연결 —
  두 글자 이상 입력하면 350ms 디바운스로 `GET .../rooms/search` 호출, 후보 목록을
  주소 칸 아래에 띄우고, 고르면 이름(비어 있으면)·주소·연락처가 자동 입력된다.
  서버에 검색 키(`NAVER_SEARCH_*`)가 없으면 후보가 안 뜰 뿐 직접 입력은 그대로 된다.
  `place_models.dart`(`PlaceSuggestion`), `room_repository.searchPlaces`.
- **일정 상세**: 상태 배너(대기/확정/취소/거절), 일시·시간·비용, 예약 메모,
  **내 참석 여부**(참석/불참/미정 3버튼 — 탭 시 `PUT …/attendances/{내 userId}`,
  응답으로 받은 현황으로 화면 갱신), **셋리스트**(곡 목록 + "＋ 곡 추가" 다이얼로그 +
  개별 삭제), **멤버별 참석 현황**(참석 N·불참 N·미정 N 집계 + 목록).
  등록자 본인 또는 밴드장이면 "이 합주 변경·취소" 버튼 → 확인 후 `DELETE`.
  취소·거절된 일정은 참석 체크·셋리스트 편집을 막는다(백엔드 409 방지).

### 라우팅·홈 연결

- `routing/app_router.dart` — 위 5개 라우트 추가. `Routes.reservation(id)`로 상세 경로 생성.
  `/cal/new`는 `?date=` 쿼리를 `DateTime`으로 파싱해 폼에 전달.
- `features/home/` — 홈의 `showSoon(…)` 안내를 실제 이동으로 교체:
  하단 탭 "캘린더" → `/cal`, "다가오는 일정"의 "캘린더" 링크와 각 타일 → 캘린더/상세,
  "다음 합주" 카드의 "일정 추가"와 "예정 합주" 요약 카드 → 폼/캘린더.
  (지도·정산·게시판 탭, "이번 달 정산" 카드는 화면이 없어 스낵바 유지.)
- `core/format/formatters.dart` — `monthTitleKo`("2026년 9월"), `dateKo`("9월 10일 (목)"),
  `dateKoUtc`, `ymd`("2026-09-10"), `hhmm` 추가.
- **한국어 로케일**: `app.dart`에 `flutter_localizations` + `locale: ko` 추가 —
  `showDatePicker`/`showTimePicker` 등 Material 위젯이 한국어로 표시된다("2026년 9월",
  "오후 7:00", "확인/취소"). `intl`도 `^0.20.2`로 올림(localizations 요구).

### 백엔드 (같은 PR)

- `GET /api/v1/bands/{bandId}/rooms/search?query=` 신규 — 네이버 지역검색 프록시.
  상세는 `docs/progress/phase-03-room.md` §8.2. `NAVER_SEARCH_CLIENT_ID/SECRET` 환경변수
  필요(네이버 개발자센터 앱 — NCP 지도 키와 별개). 비우면 200 + 빈 목록.

## 4. 어떻게 동작하나

```
홈 ─(하단 탭 "캘린더" / "예정 합주" 카드)─▶ 캘린더(/cal)
캘린더
  ├ 날짜 탭 ─▶ 그 날 일정 리스트 갱신
  ├ 일정 타일 탭 ─▶ 일정 상세(/reservations/:id)
  └ "＋ 이 날짜에 합주 등록" ─▶ 일정 등록 폼(/cal/new?date=…)

일정 등록 폼
  ├ "합주실 선택하기" ─▶ 합주실 선택 시트
  │     └ "＋ 새 합주실 등록" ─▶ 합주실 등록 폼(/cal/rooms/new) ─(생성)─▶ 시트가 그 방을 선택한 채 닫힘
  └ "합주 등록하기" ─ POST ─▶ (겹침 있으면 경고 다이얼로그) ─▶ 일정 상세로 교체 이동

일정 상세
  ├ 참석/불참/미정 ─ PUT attendances/{내 id} ─▶ 현황 갱신
  ├ "＋ 곡 추가" / 곡 삭제 ─ POST/DELETE setlist ─▶ 상세 새로고침
  └ "이 합주 변경·취소" ─ (확인) DELETE ─▶ 캘린더로 back
```

일정을 등록·취소하면 홈의 "다가오는 일정"과 캘린더 provider를 invalidate 해
다시 열 때 최신 상태가 보인다.

## 5. 직접 확인하는 법

`client-DEVLOG.md` §3·§4의 로컬 환경 함정(Flutter PATH, DB 포트 5432)을 먼저 본다.

```powershell
# 1) 백엔드
cd E:\project\band
docker compose up -d          # http://localhost:8080/actuator/health -> 200

# 2) 클라이언트
cd E:\project\band\client
& C:\flutter\bin\flutter.bat pub get
& C:\flutter\bin\flutter.bat run -d chrome
```

### 기대 시나리오

1. 로그인 → 밴드 홈. 하단 "캘린더" 탭 → 월간 캘린더.
2. 임의 날짜 선택 → "＋ 이 날짜에 합주 등록".
3. "합주실 선택하기" → (합주실이 없으면) "＋ 새 합주실 등록" → 이름만 넣고 저장 →
   시트가 그 합주실을 고른 채 닫힘.
4. 날짜·시작 시간·이용 시간 조정, 비용에 90000 입력 → "1인당" 자동 표시 →
   "합주 등록하기" → (겹치는 일정이 있으면 경고) → 일정 상세로 이동.
5. 상세에서 "참석" 탭 → 하단 "멤버별 참석 현황"의 내 상태가 "참석"으로,
   집계 "참석 1"로 바뀜.
6. "＋ 곡 추가" → 곡명 입력 → 목록에 추가. 곡 옆 ✕ 로 삭제.
7. 캘린더로 돌아가면 그 날짜에 점이 생기고 리스트에 일정이 보임.
   홈의 "다가오는 일정"에도 반영.

### 문제 해결

- **합주실 선택 시트가 비어 있음**: 그 밴드에 등록된 합주실이 없음 — 시트 하단
  "＋ 새 합주실 등록"으로 먼저 만든다.
- **"합주실을 먼저 선택해 주세요"에서 등록 버튼 비활성**: 정상 — 합주실 미선택 상태.
- **참석 버튼이 눌리지 않음**: 취소·거절된 일정이거나 `/users/me`가 실패해 내
  userId를 모르는 상태. 로그인 상태 확인.
- **날짜/시간 피커 스타일**: Flutter 기본 Material 피커. `flutter_localizations` 로
  한국어(“2026년 9월”, “오후 7:00”, “확인/취소”)는 적용됨. 다크 색상 커스터마이즈는 추후.

## 6. 검증 결과

- `flutter analyze` → **에러 0.** 경고 15개(전부 `unawaited_return_in_try_block` —
  기존 `auth_repository`/`band_repository`와 동일한 `return unwrap(...)` 패턴,
  기능 영향 없음), info 95개(`prefer_const`·`require_trailing_commas`·`withOpacity`
  deprecated — 기존 코드와 같은 스타일 부채). `dart format` 적용 완료.
- `flutter build web` → **성공**(`√ Built build\web`). `flutter_secure_storage_web`
  WASM 경고는 1단계와 동일한 기존 이슈(JS 빌드는 정상).
- 백엔드 붙여서 end-to-end 는 이 PC에서 미검증(1단계와 동일 — 다음 작업자/사용자 확인 필요).

## 7. 알려진 이슈 / 제약

| 항목 | 목업 | 실제 구현 |
|---|---|---|
| 반복(정기) 일정 | 폼에 반복 요일·횟수 설정 | 제외 — 백엔드에 정기 규칙 생성 API 없음 |
| 예약 방법(전화/카톡 등) 태그 | 칩 선택 | 자유 텍스트 "예약 메모" 하나로 (`note` 필드만 존재) |
| 합주실 주소 검색 | 검색 + 지도 마커 | **이름·주소 검색 됨**(네이버 지역검색, 백엔드 프록시 신설). 지도 마커는 지도 화면 단계에서 |
| 캘린더 "주" 뷰 | 월/주 토글 | 월간 뷰만 |
| 셋리스트 곡 재정렬·체크 | 드래그 정렬 + 완료 체크박스 | 추가·삭제만 (reorder API는 있으나 UI 미구현) |
| 일정 상세 "정산 확인" 버튼 | 있음 | 제외 — 정산 화면 단계에서 |
| 날짜/시간 피커 | 커스텀 다크 피커 | Flutter 기본 Material 피커(한국어는 적용, 다크 색상 커스터마이즈는 추후) |

## 8. 열린 결정 / 확인 필요

- **정기 일정**: 백엔드에 정기 규칙 생성 엔드포인트를 추가할지, 클라이언트에서
  N개 `POST`를 반복 호출해 흉내 낼지 결정 필요. (목업은 PRO 기능으로 표시.)
- **홈 "이번 달 정산" 카드**: 여전히 밴드 단위 합계 API 없음 → 값 `—` 유지
  (1단계 열린 결정과 동일).
- 일정 상세에 **수정(PUT)·밴드장 승인/거절** UI를 넣을지 — 지금은 취소만.
- ~~합주실 주소 검색~~ → 해결. `NAVER_SEARCH_CLIENT_ID/SECRET`를 `.env`에 넣어야
  실제 결과가 뜬다(현재 로컬엔 미설정 → 빈 목록).

### 로컬 환경 메모 (2026-09-03)

- `docker compose up -d --build`로 앱을 재빌드하면 **FCM 자격증명 마운트 문제로 기동 실패**한다:
  `.env`의 `FCM_CREDENTIALS_HOST_PATH`가 가리키는 JSON 파일이 저장소에 없어
  `/run/secrets/fcm-credentials.json`가 디렉터리로 마운트됨 → `FcmPushSender` 생성 실패.
  이번 작업과 무관한 기존 로컬 환경 이슈. 우회: `.env`에서 `FCM_CREDENTIALS_PATH=`(빈 값)로
  두면 앱이 FCM을 건너뛰고 정상 기동한다(스모크 테스트는 이 방식으로 진행함).

## 9. 커밋 · CI 링크

- PR: [#34](https://github.com/Yekapark/bandApp/pull/34) (`main` 대상). 커밋:
  `feat(room): 합주실 주소 검색 API (네이버 지역검색 프록시)`,
  `feat(client): 예약 캘린더·일정 등록/상세·합주실 등록(주소검색)·한국어 로케일`.
- CI: 백엔드 워크플로가 `RoomIntegrationTest` 신규분을 검증한다. 클라이언트용 워크플로는
  아직 없음(`flutter analyze` + `flutter test` 추가 검토 — DEVLOG §5).

## 10. 검증 결과 (요약)

- **클라이언트**: `flutter analyze` 에러 0(경고 16 = 기존 repository 패턴, info 95). `flutter build web` 성공.
- **백엔드**: `./gradlew compileJava compileTestJava` 통과. 순수 단위 테스트
  (`NaverLocalSearchParseTest`, `NaverGeocodingParseTest`) 통과.
- **런타임 스모크**(`docker compose`, FCM 우회): `rooms/search` 키 미설정 시 `200 {placeCount:0}`,
  공백 질의 `200` 빈 목록, 비멤버 `403`.
- **미검증**: `RoomIntegrationTest`(신규 3건 포함) — 이 PC의 Testcontainers/Docker 이슈로 실행 불가 → **CI 필수**.
  백엔드 붙인 캘린더·일정·RSVP end-to-end도 미검증(1단계와 동일).

## 11. 다음 단계 예고

- 하단 탭바를 `ShellRoute`로 바꿔 실제 탭 전환 UX.
- 합주실 지도 화면(`/map`) — 좌표 있는 합주실 마커 + 목록.
- 정산 화면(`/split`) — 일정별 1인당 금액·납부 체크리스트.
- 카카오 로그인 SDK 연동.
