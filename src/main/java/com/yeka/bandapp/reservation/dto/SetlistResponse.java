package com.yeka.bandapp.reservation.dto;

import java.util.List;

/** 한 일정의 셋리스트 전체. {@code items}는 {@code orderNo} 오름차순이다. */
public record SetlistResponse(
        Long reservationId,
        int itemCount,
        List<SetlistItemResponse> items
) {
}
