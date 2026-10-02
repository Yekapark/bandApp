package com.yeka.bandapp.recurring.dto;

import com.yeka.bandapp.recurring.entity.RecurringFrequency;
import com.yeka.bandapp.recurring.entity.RecurringRule;
import com.yeka.bandapp.recurring.service.OccurrenceGenerator;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 정기 일정 규칙 응답. {@code roomName}은 합주실이 그 사이 삭제됐어도 채워진다.
 * {@code monthlyWeek}는 매월 규칙이 반복하는 "몇째 주" — 1~4, {@code -1}이면 "마지막 주",
 * 매월 규칙이 아니면 {@code null}.
 */
public record RecurringRuleResponse(
        Long id,
        Long roomId,
        String roomName,
        RecurringFrequency frequency,
        DayOfWeek dayOfWeek,
        Integer monthlyWeek,
        LocalTime startTime,
        LocalTime endTime,
        LocalDate startDate,
        LocalDate endDate,
        Integer cost,
        String note,
        Long createdBy,
        Instant createdAt
) {
    public static RecurringRuleResponse from(RecurringRule r, String roomName) {
        return new RecurringRuleResponse(
                r.getId(),
                r.getRoomId(),
                roomName,
                r.getFrequency(),
                r.getDayOfWeek(),
                OccurrenceGenerator.monthlyWeek(r),
                r.getStartTime(),
                r.getEndTime(),
                r.getStartDate(),
                r.getEndDate(),
                r.getCost(),
                r.getNote(),
                r.getCreatedBy(),
                r.getCreatedAt());
    }
}
