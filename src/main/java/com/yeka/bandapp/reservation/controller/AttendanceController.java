package com.yeka.bandapp.reservation.controller;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.reservation.dto.AttendanceBoardResponse;
import com.yeka.bandapp.reservation.dto.UpdateAttendanceRequest;
import com.yeka.bandapp.reservation.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 일정 참석 체크(RSVP). Bearer 인증 필요, 모든 엔드포인트가 밴드 멤버십을 검증한다.
 *
 * <p>일정이 만들어질 때 그 시점의 활성 멤버 전원이 {@code PENDING}으로 생성되고, 일정 생성 이후 합류한
 * 멤버는 첫 응답 시 자동으로 추가된다. 참석 현황은 저장된 행이 아니라 <b>현재</b> 활성 멤버 기준이다.
 */
@Tag(name = "9. 참석 체크(RSVP)",
        description = "일정별 멤버 참석 현황 조회와 본인 참석 상태 변경. 본인 것만 바꿀 수 있다(타인 변경은 403). "
                + "일정 상세 조회(GET /reservations/{id})에도 같은 참석 현황이 포함된다.")
@RestController
@RequestMapping("/api/v1/bands/{bandId}/reservations/{reservationId}/attendances")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @Operation(summary = "일정 참석 현황",
            description = "현재 활성 밴드 멤버 전원의 참석 상태(members)와 집계(attendingCount/memberCount)를 반환한다. "
                    + "아직 응답하지 않은 멤버는 PENDING. 그 밴드 멤버만(비멤버 403 NOT_BAND_MEMBER).")
    @GetMapping
    public ApiResponse<AttendanceBoardResponse> board(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @PathVariable long bandId,
                                                      @PathVariable long reservationId) {
        return ApiResponse.ok(attendanceService.getBoard(bandId, reservationId, principal.userId()));
    }

    @Operation(summary = "내 참석 상태 변경",
            description = "path의 userId가 요청자 본인이 아니면 403 NOT_ATTENDANCE_OWNER. status는 "
                    + "ATTENDING/ABSENT/PENDING(PENDING은 응답 취소). 일정 생성 이후 합류한 멤버도 호출 가능하며 "
                    + "이때 참석 행이 새로 만들어진다. 취소·거절된 일정이면 409 RESERVATION_NOT_EDITABLE. "
                    + "변경 후 전체 참석 현황을 반환한다.")
    @PutMapping("/{userId}")
    public ApiResponse<AttendanceBoardResponse> respond(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @PathVariable long bandId,
                                                        @PathVariable long reservationId,
                                                        @PathVariable long userId,
                                                        @Valid @RequestBody UpdateAttendanceRequest request) {
        return ApiResponse.ok(attendanceService.respond(
                bandId, reservationId, userId, principal.userId(), request.status()));
    }
}
