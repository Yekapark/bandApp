package com.yeka.bandapp.reservation.controller;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.reservation.dto.CreateReservationRequest;
import com.yeka.bandapp.reservation.dto.ReservationDetailResponse;
import com.yeka.bandapp.reservation.dto.ReservationListResponse;
import com.yeka.bandapp.reservation.dto.ReservationResponse;
import com.yeka.bandapp.reservation.dto.ReservationWriteResponse;
import com.yeka.bandapp.reservation.dto.UpdateReservationRequest;
import com.yeka.bandapp.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 밴드 일정(이미 외부에서 잡은 예약의 기록). Bearer 인증 필요, 모든 엔드포인트가 밴드 멤버십을 검증한다.
 *
 * <p><b>시간대 겹침은 등록/수정을 막지 않는다.</b> 겹치는 일정이 있으면 등록/수정 응답의 {@code overlaps}에
 * 목록으로 담길 뿐 요청은 201/200으로 성공한다. {@code PATCH} 미지원 클라이언트를 위해 수정은 {@code PUT}이다.
 */
@Tag(name = "7. 일정",
        description = "밴드 합주 일정 등록·조회·수정·취소와 밴드장 승인/거절. 일정은 '이미 외부에서 완료된 예약의 기록'이며 "
                + "시간대가 겹쳐도 등록을 막지 않고 응답에 경고(overlaps)로만 알린다. 등록 직후 상태는 밴드의 "
                + "reservationPermission(LEADER_ONLY/ANYONE=즉시 CONFIRMED, APPROVAL_REQUIRED=PENDING)에 따른다.")
@RestController
@RequestMapping("/api/v1/bands/{bandId}/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Operation(summary = "일정 등록",
            description = "roomId·startAt·endAt 필수. endAt은 startAt보다 뒤여야 한다(400 INVALID_RESERVATION_PERIOD). "
                    + "LEADER_ONLY 밴드에서 일반 멤버가 등록하면 403 NOT_BAND_LEADER. 다른 밴드의 roomId나 삭제된 "
                    + "합주실이면 404 ROOM_NOT_FOUND. 겹치는 일정이 있어도 201이며 overlaps에 담긴다. "
                    + "해당 합주실의 usageCount가 1 증가한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReservationWriteResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @PathVariable long bandId,
                                                        @Valid @RequestBody CreateReservationRequest request) {
        return ApiResponse.ok(reservationService.create(bandId, principal.userId(), request));
    }

    @Operation(summary = "일정 목록(캘린더)",
            description = "from~to 구간과 조금이라도 겹치는 일정을 startAt 오름차순으로 반환한다. from·to 필수, "
                    + "to는 from보다 뒤여야 한다. 기본은 취소·거절 건을 제외하며 includeInactive=true면 전부 포함한다. "
                    + "그 밴드 멤버만(비멤버 403 NOT_BAND_MEMBER).")
    @GetMapping
    public ApiResponse<ReservationListResponse> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable long bandId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ApiResponse.ok(reservationService.list(bandId, principal.userId(), from, to, includeInactive));
    }

    @Operation(summary = "일정 상세",
            description = "일정 정보에 더해 참석 현황(members: 현재 활성 멤버 전원, 미응답은 PENDING)과 집계"
                    + "(attendingCount/memberCount), 셋리스트(setlist)를 함께 반환한다. 다른 밴드의 "
                    + "reservationId를 넣으면 존재 여부와 무관하게 404 RESERVATION_NOT_FOUND.")
    @GetMapping("/{reservationId}")
    public ApiResponse<ReservationDetailResponse> get(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @PathVariable long bandId,
                                                      @PathVariable long reservationId) {
        return ApiResponse.ok(reservationService.get(bandId, reservationId, principal.userId()));
    }

    @Operation(summary = "일정 수정",
            description = "PUT 전체 교체 — 보내지 않은 선택 필드는 비워진다. 등록자 본인 또는 밴드장만(그 외 403 "
                    + "NOT_RESERVATION_OWNER). 취소·거절된 일정은 409 RESERVATION_NOT_EDITABLE. APPROVAL_REQUIRED "
                    + "밴드에서 확정된 일정의 시간·합주실이 바뀌면 다시 PENDING이 된다. 합주실을 바꾸면 이전 방 "
                    + "usageCount -1 / 새 방 +1. 겹치는 일정은 overlaps에 담기고 요청은 200으로 성공한다.")
    @PutMapping("/{reservationId}")
    public ApiResponse<ReservationWriteResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @PathVariable long bandId,
                                                        @PathVariable long reservationId,
                                                        @Valid @RequestBody UpdateReservationRequest request) {
        return ApiResponse.ok(reservationService.update(bandId, reservationId, principal.userId(), request));
    }

    @Operation(summary = "일정 승인 (APPROVAL_REQUIRED)",
            description = "PENDING 일정을 CONFIRMED로 바꾼다. 밴드장만(그 외 403). 대기 상태가 아니면 409 "
                    + "RESERVATION_NOT_PENDING.")
    @PostMapping("/{reservationId}/approve")
    public ApiResponse<ReservationResponse> approve(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @PathVariable long bandId,
                                                    @PathVariable long reservationId) {
        return ApiResponse.ok(reservationService.approve(bandId, reservationId, principal.userId()));
    }

    @Operation(summary = "일정 거절 (APPROVAL_REQUIRED)",
            description = "PENDING 일정을 REJECTED로 바꾸고 등록 시 올렸던 합주실 usageCount를 되돌린다. 밴드장만. "
                    + "대기 상태가 아니면 409 RESERVATION_NOT_PENDING.")
    @PostMapping("/{reservationId}/reject")
    public ApiResponse<ReservationResponse> reject(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable long bandId,
                                                   @PathVariable long reservationId) {
        return ApiResponse.ok(reservationService.reject(bandId, reservationId, principal.userId()));
    }

    @Operation(summary = "일정 취소",
            description = "status를 CANCELLED로 바꾼다(행은 남아 과거 기록·정산이 참조 가능). 등록자 본인 또는 "
                    + "밴드장만(그 외 403 NOT_RESERVATION_OWNER). 합주실 usageCount -1. 이미 취소된 일정에 다시 "
                    + "호출해도 204(멱등). 거절된 일정은 409 RESERVATION_NOT_EDITABLE.")
    @DeleteMapping("/{reservationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable long bandId,
                       @PathVariable long reservationId) {
        reservationService.cancel(bandId, reservationId, principal.userId());
    }
}
