package com.yeka.bandapp.reservation.dto;

import com.yeka.bandapp.reservation.entity.ReservationStatus;

import java.time.Instant;

/**
 * 일정 상세 응답. 일정 자체({@link ReservationResponse})의 필드를 그대로 펼쳐 담고(목록 응답과 필드
 * 이름·위치가 같다), 여기에 참석 현황과 셋리스트를 더한다
 * (BUILD_PLAN Phase 6: "일정 상세 조회 시 멤버별 참석 현황 및 집계 포함").
 *
 * <p>{@link #of}가 {@link ReservationResponse}의 접근자를 그대로 옮겨 담으므로 두 응답의 공통 필드는
 * 어긋나지 않는다.
 */
public record ReservationDetailResponse(
        Long id,
        Long roomId,
        String roomName,
        Long requestedBy,
        ReservationStatus status,
        Instant startAt,
        Instant endAt,
        Integer cost,
        String note,
        Long recurringRuleId,
        Instant createdAt,
        AttendanceBoardResponse attendance,
        SetlistResponse setlist
) {
    public static ReservationDetailResponse of(ReservationResponse r,
                                               AttendanceBoardResponse attendance,
                                               SetlistResponse setlist) {
        return new ReservationDetailResponse(
                r.id(), r.roomId(), r.roomName(), r.requestedBy(), r.status(),
                r.startAt(), r.endAt(), r.cost(), r.note(), r.recurringRuleId(), r.createdAt(),
                attendance, setlist);
    }
}
