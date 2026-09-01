package com.yeka.bandapp.reservation.dto;

import java.util.List;

/** 캘린더용 기간 조회 결과. {@code startAt} 오름차순. */
public record ReservationListResponse(
        long bandId,
        int reservationCount,
        List<ReservationResponse> reservations
) {
}
