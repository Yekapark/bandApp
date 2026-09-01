package com.yeka.bandapp.reservation.dto;

import com.yeka.bandapp.reservation.entity.Reservation;
import com.yeka.bandapp.reservation.entity.ReservationStatus;

import java.time.Instant;

/**
 * 일정 응답. {@code roomName}은 합주실이 그 사이 삭제됐어도 채워진다(과거 일정이 이름을 계속 보여야 하므로).
 */
public record ReservationResponse(
        Long id,
        Long roomId,
        String roomName,
        Long requestedBy,
        ReservationStatus status,
        Instant startAt,
        Instant endAt,
        Integer cost,
        String note,
        /** 정기 규칙에서 만들어진 회차면 그 규칙 id, 단발 일정이면 {@code null}. */
        Long recurringRuleId,
        Instant createdAt
) {
    public static ReservationResponse from(Reservation r, String roomName) {
        return new ReservationResponse(
                r.getId(),
                r.getRoomId(),
                roomName,
                r.getRequestedBy(),
                r.getStatus(),
                r.getStartAt(),
                r.getEndAt(),
                r.getCost(),
                r.getNote(),
                r.getRecurringRuleId(),
                r.getCreatedAt());
    }
}
