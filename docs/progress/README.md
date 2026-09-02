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

## 문서 목록

| Phase | 문서 | 상태 |
|---|---|---|
| 0 | [phase-00-scaffolding.md](phase-00-scaffolding.md) — 프로젝트 뼈대 | ✅ 완료 |
| 1 | [phase-01-auth.md](phase-01-auth.md) — 인증 (이메일 / 카카오 / JWT / 탈퇴) · [사람 잔여작업](phase-01-TODO.md) | ✅ 완료 — CI 통과, `main` 머지 (사람 잔여작업 별도) |
| 2 | [phase-02-band.md](phase-02-band.md) — 밴드 · 초대 · 멤버 · 딥링크 · 레이트리밋 | ✅ 완료 — CI 통과, `main` 머지 (PR #16) |
| 3 | [phase-03-room.md](phase-03-room.md) — 합주실 CRUD · 네이버 지오코딩 · 내 밴드 목록 | ✅ 완료 — CI 통과 (PR #18) |
| 4 | [phase-04-reservation.md](phase-04-reservation.md) — 일정 등록 · 권한 모드별 분기 · 승인/거절 · 겹침 경고 · 캘린더 조회 | ✅ 완료 — CI 통과 (PR #22) |
| 5 | [phase-05-recurring.md](phase-05-recurring.md) — 정기 일정 규칙 · 회차 자동 생성(주간/격주/월간) · 규칙 삭제 시 미래 회차만 취소 · 회차 연장 배치 | ✅ 완료 — CI 통과 (PR #24) |
| 6 | [phase-06-attendance-setlist.md](phase-06-attendance-setlist.md) — 참석 체크(RSVP) · 일정 상세의 참석 현황·집계 · 셋리스트 CRUD·재정렬 | ✅ 완료 — CI 통과 (PR #25) |
| 7 | [phase-07-settlement.md](phase-07-settlement.md) — 정산(N빵) · EQUAL/ATTENDEES_ONLY 분배 · 나머지 밴드장 부담 · 참석자 변경 시 재계산(자동 없음) · 본인 납부 셀프 체크 | ✅ 완료 — CI 통과, `main` 머지 (PR #27) |
| 8 | [phase-08-board-media-report.md](phase-08-board-media-report.md) — 게시판 CRUD(커서 페이징) · R2 presigned 업로드(백엔드 미경유) · 완료 콜백 HEAD 크기·형식 검증 · 신고 접수 · 사용자 차단(양방향) | ✅ 완료 — CI 통과 (PR #29) |
