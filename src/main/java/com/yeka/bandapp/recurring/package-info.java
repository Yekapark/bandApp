/**
 * 정기 일정 도메인 — "매주 토요일 15:00 합주" 같은 반복 규칙을 등록하면 앞으로의 회차가
 * {@link com.yeka.bandapp.reservation.entity.Reservation} 행으로 미리 만들어진다.
 *
 * <p>규칙({@code RecurringRule})은 합주실의 실제 예약이 아니라 일정 생성 틀이다. 만들어진 회차는
 * Phase 4 의 일정과 똑같이 다뤄진다 — 캘린더 조회·개별 수정/취소는 기존 일정 API 를 그대로 쓴다.
 * 이 패키지는 규칙의 등록·조회·삭제와 회차 자동 생성(등록 시 + 배치 연장)만 담당한다.
 *
 * <p>회차를 실제로 저장하고 합주실 {@code usageCount} 를 증감하는 일은 일정 도메인의 몫이라,
 * {@link com.yeka.bandapp.reservation.service.ReservationDirectoryService} 창구를 통해서만 한다
 * ({@code ReservationRepository} 를 직접 만지지 않는다 — 코딩 컨벤션).
 *
 * <p>시간대 겹침은 여기서도 막지 않는다 — 규칙이 만든 회차가 기존 일정과 겹쳐도 저장되고,
 * 겹침은 등록 응답의 {@code overlaps} 에 경고로만 실린다(BUILD_PLAN 2장 2번).
 */
package com.yeka.bandapp.recurring;
