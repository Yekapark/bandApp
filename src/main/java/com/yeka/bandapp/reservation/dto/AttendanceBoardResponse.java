package com.yeka.bandapp.reservation.dto;

import java.util.List;

/**
 * 일정 하나의 참석 현황 전체. {@code members}는 <b>현재</b> 활성 밴드 멤버 전원이며(일정 생성 이후
 * 합류한 멤버도 포함, 그 사이 탈퇴한 멤버는 제외), 아직 응답하지 않은 멤버는 {@code PENDING}으로 나온다.
 *
 * <p>{@code attendingCount}는 그중 {@code ATTENDING}인 수, {@code memberCount}는 현재 활성 멤버 수다
 * ("참석 N / 전체 M").
 */
public record AttendanceBoardResponse(
        Long reservationId,
        int attendingCount,
        int memberCount,
        List<AttendanceEntryResponse> members
) {
}
