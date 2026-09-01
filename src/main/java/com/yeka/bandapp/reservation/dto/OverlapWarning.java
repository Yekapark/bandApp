package com.yeka.bandapp.reservation.dto;

import com.yeka.bandapp.reservation.entity.Reservation;
import com.yeka.bandapp.reservation.entity.ReservationStatus;

import java.time.Instant;

/**
 * 등록/수정하려는 일정과 시간대가 겹치는 <b>기존</b> 일정 한 건. 이 값이 응답에 담긴다고 해서 요청이
 * 실패한 것은 아니다 — 이 앱은 겹침을 막지 않고 알리기만 한다(BUILD_PLAN 2장 2번).
 */
public record OverlapWarning(
        Long id,
        Long roomId,
        String roomName,
        ReservationStatus status,
        Instant startAt,
        Instant endAt
) {
    public static OverlapWarning from(Reservation r, String roomName) {
        return new OverlapWarning(
                r.getId(),
                r.getRoomId(),
                roomName,
                r.getStatus(),
                r.getStartAt(),
                r.getEndAt());
    }
}
