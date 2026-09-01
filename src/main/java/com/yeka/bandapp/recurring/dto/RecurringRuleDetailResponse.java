package com.yeka.bandapp.recurring.dto;

import com.yeka.bandapp.reservation.dto.ReservationResponse;

import java.util.List;

/**
 * 정기 일정 규칙 상세. {@code occurrences}는 <b>최근 구간</b>(오늘 − horizonWeeks 이후)의 회차만
 * 담는다(취소분 포함, start_at 오름차순) — 응답이 무한정 커지지 않도록. 그 이전 이력은
 * {@code GET /api/v1/bands/{bandId}/reservations?from=&to=} 캘린더 API 로 조회한다.
 * {@code occurrenceCount}도 이 구간 기준이다.
 */
public record RecurringRuleDetailResponse(
        RecurringRuleResponse rule,
        int occurrenceCount,
        List<ReservationResponse> occurrences
) {
}
