package com.yeka.bandapp.recurring.dto;

import com.yeka.bandapp.recurring.entity.RecurringFrequency;
import com.yeka.bandapp.recurring.entity.RecurringRule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 정기 일정 규칙 응답. {@code roomName}은 합주실이 그 사이 삭제됐어도 채워진다.
 */
public record RecurringRuleResponse(
        Long id,
        Long roomId,
        String roomName,
        RecurringFrequency frequency,
        DayOfWeek dayOfWeek,
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
