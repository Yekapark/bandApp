# 진행 기록 (Progress Log)

각 구현 Phase가 끝날 때마다 여기에 상세 기록을 남긴다.
**목표 독자: 코드를 직접 쓰지 않고 작업을 지시하는 사람.** 개발 배경지식 없이도
"무엇을 / 왜 / 어떻게 확인하는지"를 이해할 수 있게 작성한다.

각 문서 공통 목차:

1. 한 줄 요약
2. 이 Phase의 목표 (BUILD_PLAN 기준)
3. 무엇을 만들었나 — 파일/구성요소별 설명과 존재 이유
4. 어떻게 동작하나 — 흐름 설명
5. **직접 확인하는 법** — 사전 준비, 명령어, 기대 결과, 문제 해결
6. 실제 검증 기록 — 무엇을 돌렸고 결과가 무엇이었는지
7. 알려진 이슈 / 제약
8. 커밋 · CI 링크
9. 다음 Phase 예고

> **이어서 할 일: [NEXT.md](NEXT.md)** — 못 끝낸 것과 다음 우선순위를 모아 둔 살아있는 문서.

## 문서 목록

| Phase | 문서 | 상태 |
|---|---|---|
| 0 | [phase-00-scaffolding.md](phase-00-scaffolding.md) — 프로젝트 뼈대 | ✅ 완료 |
| 1 | [phase-01-auth.md](phase-01-auth.md) — 인증 (이메일 / 카카오 / JWT / 탈퇴) · [사람 잔여작업](phase-01-TODO.md) | ✅ 완료 — CI 통과, `main` 머지 (사람 잔여작업 별도) |
| 2 | [phase-02-band.md](phase-02-band.md) — 밴드 · 초대 · 멤버 · 딥링크 · 레이트리밋 | ✅ 완료 — CI 통과, `main` 머지 (PR #16) |
| 3 | [phase-03-room.md](phase-03-room.md) — 합주실 CRUD · 네이버 지오코딩 · 내 밴드 목록 · (후속) 네이버 지역검색 프록시 `rooms/search` | ✅ 완료 — CI 통과 (PR #18) · 후속은 §8.2, 클라 2단계 PR에 포함 |
| 4 | [phase-04-reservation.md](phase-04-reservation.md) — 일정 등록 · 권한 모드별 분기 · 승인/거절 · 겹침 경고 · 캘린더 조회 | ✅ 완료 — CI 통과 (PR #22) |
| 5 | [phase-05-recurring.md](phase-05-recurring.md) — 정기 일정 규칙 · 회차 자동 생성(주간/격주/월간) · 규칙 삭제 시 미래 회차만 취소 · 회차 연장 배치 | ✅ 완료 — CI 통과 (PR #24) |
| 6 | [phase-06-attendance-setlist.md](phase-06-attendance-setlist.md) — 참석 체크(RSVP) · 일정 상세의 참석 현황·집계 · 셋리스트 CRUD·재정렬 | ✅ 완료 — CI 통과 (PR #25) |
| 7 | [phase-07-settlement.md](phase-07-settlement.md) — 정산(N빵) · EQUAL/ATTENDEES_ONLY 분배 · 나머지 밴드장 부담 · 참석자 변경 시 재계산(자동 없음) · 본인 납부 셀프 체크 | ✅ 완료 — CI 통과, `main` 머지 (PR #27) |
| 8 | [phase-08-board-media-report.md](phase-08-board-media-report.md) — 게시판 CRUD(커서 페이징) · R2 presigned 업로드(백엔드 미경유) · 완료 콜백 HEAD 크기·형식 검증 · 신고 접수 · 사용자 차단(양방향) | ✅ 완료 — CI 통과 (PR #29) |
| 9 | [phase-09-notification-batch.md](phase-09-notification-batch.md) — FCM 푸시(디바이스 토큰·알림 on/off·리마인더 시점) · 트리거(새 일정·승인·정산·취소, AFTER_COMMIT 이벤트) · 리마인더·참석 독촉 배치(멱등) · 미디어 만료·고아 PENDING 정리 배치(R2 삭제 실패 재시도) | ✅ 완료 — CI 통과 (PR #30) |
| 10 | [phase-10-plan.md](phase-10-plan.md) — 밴드 FREE/PREMIUM 요금제 · 미디어 보관기한을 현재 플랜에 연결(FREE 30일 / PREMIUM 무제한) · 티어 변경 시 기존 미디어 만료일 재계산(업그레이드=NULL, 다운그레이드=30일 유예) · `PaymentGateway` 인터페이스 + no-op 구현체 · 동시 전환 `SELECT … FOR UPDATE` 직렬화 | ✅ 완료 — CI 통과 (PR #33) |

### 클라이언트 (Flutter, `client/`)

> **이어받기: [client-DEVLOG.md](client-DEVLOG.md) 를 먼저 읽는다.** 현재 상태·다음 할 일·로컬 환경 함정(Flutter PATH, DB 포트 5432 충돌 등).

| 단계 | 문서 | 상태 |
|---|---|---|
| — | [client-DEVLOG.md](client-DEVLOG.md) — 이어받기 가이드 (살아있는 문서) | 🔄 상시 갱신 |
| — | [client-SCREENS.md](client-SCREENS.md) — 화면 구현 현황 표 (요구 13개 대비, 라우트 표, 남은 작업) | 🔄 상시 갱신 |
| — | [client-GAP-BACKLOG.md](client-GAP-BACKLOG.md) — 백엔드엔 있는데 클라에 없던 기능 체크리스트 (C10~C15) | ✅ 전부 완료 (2026-09-04) |
| C1 | [client-01-onboarding-home.md](client-01-onboarding-home.md) — 프로젝트 스캐폴딩 · 스플래시/로그인/약관/회원가입 · 밴드 생성·초대코드 가입 · 밴드 홈 (실제 API 연동) | 🔨 구현 완료, `analyze` 통과·웹 빌드 OK, end-to-end 검증 대기 |
| C2 | [client-02-calendar-reservation.md](client-02-calendar-reservation.md) — 예약 캘린더(월간 뷰) · 합주실 목록/등록(네이버 주소검색) · 일정 등록 폼(겹침 경고) · 일정 상세(참석 RSVP·멤버별 현황·셋리스트) · 한국어 로케일 · (백엔드) `rooms/search` | 🔨 구현 완료 (PR #34), `analyze` 에러 0·웹 빌드 OK·백엔드 단위테스트 통과, 통합테스트 CI 대기·end-to-end 검증 대기 |
| C3 | [client-03-settlement.md](client-03-settlement.md) — 일정 정산(N빵) 화면: 총액 분배(전원/참석자) · 납부 셀프 체크 · 재계산. Phase 7 API 사용, 백엔드 변경 없음 | 🔨 구현 완료, `analyze` 에러 0·웹 빌드 OK·백엔드 스모크(생성/납부/재계산) 통과, UI end-to-end 대기 |
| C4 | [client-04-room-map.md](client-04-room-map.md) — 하단 탭바 `StatefulShellRoute` 전환(홈·캘린더·지도) · 합주실 지도 `/map`(네이버 지도, Android/iOS 전용, 웹은 목록만). 백엔드 변경 없음 | 🔨 구현 완료, `analyze` 에러 0·웹 빌드 OK, 네이티브 지도 end-to-end 미검증(키·기기 없음) |
| C5 | [client-05-notification-settings.md](client-05-notification-settings.md) — 알림 설정 화면 `/settings/notifications`: 푸시 on/off · "N분 전" 리마인더 시점. `notifications/settings` API 사용, 백엔드 변경 없음 | 🔨 구현 완료, `analyze` 에러 0·`flutter test` 통과·웹 빌드 OK, end-to-end 대기. 알림 수신부(FCM)·미납 독촉은 미구현 → [client-PROBLEMS-2026-09-03.md](client-PROBLEMS-2026-09-03.md) |
| C6 | [client-06-board.md](client-06-board.md) — 게시판(#11·#12): 피드(커서 무한스크롤)·글 상세·작성/수정·첨부 업로드(R2 presigned 직접 PUT)·신고·작성자 차단. 하단 탭 "게시판" 승격. Phase 8 API 사용, 백엔드 변경 없음. 신규 의존성 `image_picker` | 🔨 구현 완료, `analyze` 에러 0·`flutter test`(신규 4개 포함) 통과·웹 빌드 OK, end-to-end 대기(로컬 R2 미설정). 영상 인앱 재생·차단 해제 화면은 미구현 |
| C7 | [client-07-reservation-recurring.md](client-07-reservation-recurring.md) — 일정 수정(PUT)·밴드장 승인/거절 + 정기 일정 규칙 등록/목록/삭제(`/cal/recurring`). Phase 4·5 API 사용, 백엔드 변경 없음 | 🔨 구현 완료, `analyze` 에러 0·`flutter test`(신규 5개 포함, 총 14) 통과·웹 빌드 OK, end-to-end 대기. 정기 규칙 상세/수정 UI는 미구현 |
| C8 | [client-08-settings.md](client-08-settings.md) — 설정 허브 `/settings` + 밴드 설정(일정 권한 모드·밴드장 위임·멤버 추방·밴드 나가기) + 계정(회원 탈퇴) + 차단한 사용자(해제). Phase 1~3·8 API 사용, 백엔드 변경 없음 | 🔨 구현 완료, `analyze` 에러 0·`flutter test`(총 16) 통과·웹 빌드 OK, end-to-end 대기. 밴드 이름 변경·프로필 편집은 백엔드 미제공 |
| C9 | [client-09-fcm-ci.md](client-09-fcm-ci.md) — FCM 디바이스 토큰 등록·포그라운드 수신 배선(`push_service.dart`, 설정 파일 없으면 no-op) + 클라이언트 CI(`.github/workflows/client-ci.yml`). Phase 9 API 사용, 백엔드 변경 없음. 신규 의존성 `firebase_core`·`firebase_messaging` | 🔨 구현 완료, `analyze` 에러 0·`flutter test`(총 16) 통과·웹 빌드 OK. 실제 푸시는 Firebase 설정 파일 필요. **요구 화면 13개 모두 도달** |
