package com.yeka.bandapp.reservation.dto;

import com.yeka.bandapp.reservation.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

/** 본인 참석 상태 변경 요청. {@code PENDING}으로 되돌리는 것도 허용한다(응답 취소). */
public record UpdateAttendanceRequest(
        @NotNull AttendanceStatus status
) {
}
