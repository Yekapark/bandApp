package com.yeka.bandapp.reservation.dto;

import com.yeka.bandapp.reservation.entity.AttendanceStatus;

import java.time.Instant;

/**
 * 일정 참석 현황의 한 줄 — 밴드 멤버 한 명의 응답. {@code respondedAt}은 아직 응답하지 않았으면 {@code null}.
 */
public record AttendanceEntryResponse(
        Long userId,
        String name,
        String role,
        AttendanceStatus status,
        Instant respondedAt
) {
}
