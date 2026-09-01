package com.yeka.bandapp.recurring.dto;

import com.yeka.bandapp.reservation.dto.OverlapWarning;
import com.yeka.bandapp.reservation.dto.ReservationResponse;

import java.util.List;

/**
 * 정기 일정 규칙 등록 응답. 규칙과 함께 최근 구간(오늘 ± horizonWeeks)의 회차 목록, 그리고 그
 * 회차들과 시간대가 겹치는 기존 일정({@code overlaps})을 담는다. 회차는 이 구간에서만 생성되므로
 * {@code startDate}를 과거로 멀리 잡아도 대량 백필되지 않는다. {@code overlaps}가 비어 있지 않아도
 * 요청은 <b>성공</b>이다 — 겹침은 경고이지 거부 사유가 아니다(BUILD_PLAN 2장 2번).
 */
public record RecurringRuleWriteResponse(
        RecurringRuleResponse rule,
        int occurrenceCount,
        List<ReservationResponse> occurrences,
        List<OverlapWarning> overlaps
) {
}
