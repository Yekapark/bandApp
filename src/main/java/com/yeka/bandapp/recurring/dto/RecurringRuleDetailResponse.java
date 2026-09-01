package com.yeka.bandapp.recurring.dto;

import com.yeka.bandapp.reservation.dto.ReservationResponse;

import java.util.List;

/** 정기 일정 규칙 상세. 규칙과 그로부터 만들어진 회차 전체(취소분 포함, start_at 오름차순). */
public record RecurringRuleDetailResponse(
        RecurringRuleResponse rule,
        int occurrenceCount,
        List<ReservationResponse> occurrences
) {
}
