package com.yeka.bandapp.settlement.controller;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.settlement.dto.CreateSettlementRequest;
import com.yeka.bandapp.settlement.dto.RecalculateSettlementRequest;
import com.yeka.bandapp.settlement.dto.SettlementResponse;
import com.yeka.bandapp.settlement.dto.UpdateSharePaidRequest;
import com.yeka.bandapp.settlement.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 일정 정산(N빵). Bearer 인증 필요, 모든 엔드포인트가 밴드 멤버십을 검증한다.
 *
 * <p>일정당 정산은 하나다. 만든 뒤 총액·참석자 변화는 재계산 API 로만 반영하며, 서버가 자동으로
 * 다시 나누지는 않는다. 생성·재계산은 일정 등록자 본인 또는 밴드장만, 납부 체크는 본인 몫만 가능하다.
 */
@Tag(name = "11. 정산(N빵)",
        description = "일정 총비용을 분배 방식(EQUAL=멤버 전원 / ATTENDEES_ONLY=참석자만)에 따라 멤버별 몫으로 나눈다. "
                + "나머지는 밴드장 먼저(없으면 최고참) 부담해 몫 합계가 총액과 정확히 일치한다. 참석자가 바뀌면 "
                + "재계산 API 로 반영(자동 재계산 없음). 납부 여부는 본인이 셀프 체크한다.")
@RestController
@RequestMapping("/api/v1/bands/{bandId}/reservations/{reservationId}/settlement")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Operation(summary = "정산 생성",
            description = "totalAmount(0 초과)·splitType 필수. 일정 등록자 본인 또는 밴드장만(그 외 403 NOT_SETTLEMENT_MANAGER). "
                    + "이미 정산이 있으면 409 SETTLEMENT_ALREADY_EXISTS. splitType=ATTENDEES_ONLY 인데 참석(ATTENDING) "
                    + "멤버가 0명이면 409 SETTLEMENT_NO_ATTENDEES. 다른 밴드의 일정이면 404 RESERVATION_NOT_FOUND.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SettlementResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable long bandId,
                                                  @PathVariable long reservationId,
                                                  @Valid @RequestBody CreateSettlementRequest request) {
        return ApiResponse.ok(settlementService.create(bandId, reservationId, principal.userId(), request));
    }

    @Operation(summary = "정산 현황 조회",
            description = "총액·분배 방식·멤버별 몫과 납부 집계(paidCount/paidAmount/outstandingAmount)를 반환한다. "
                    + "정산이 없으면 404 SETTLEMENT_NOT_FOUND. 그 밴드 멤버만(비멤버 403 NOT_BAND_MEMBER).")
    @GetMapping
    public ApiResponse<SettlementResponse> get(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable long bandId,
                                               @PathVariable long reservationId) {
        return ApiResponse.ok(settlementService.get(bandId, reservationId, principal.userId()));
    }

    @Operation(summary = "정산 재계산",
            description = "현재 밴드 멤버·참석자 기준으로 몫을 다시 만든다. totalAmount·splitType 은 선택이며 넘기면 "
                    + "그 값으로 갱신, 생략하면 유지. 계속 대상인 멤버의 납부 여부(paid)는 보존되고, 빠진 멤버의 몫은 "
                    + "삭제, 새 멤버의 몫은 미납으로 추가된다. 권한·에러는 생성과 동일(ATTENDEES_ONLY 참석자 0명이면 "
                    + "409 SETTLEMENT_NO_ATTENDEES, 정산이 없으면 404 SETTLEMENT_NOT_FOUND).")
    @PostMapping("/recalculate")
    public ApiResponse<SettlementResponse> recalculate(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @PathVariable long bandId,
                                                       @PathVariable long reservationId,
                                                       @Valid @RequestBody RecalculateSettlementRequest request) {
        return ApiResponse.ok(settlementService.recalculate(bandId, reservationId, principal.userId(), request));
    }

    @Operation(summary = "내 납부 상태 변경",
            description = "path 의 userId 가 요청자 본인이 아니면 403 NOT_SETTLEMENT_SHARE_OWNER. 요청자가 분담 대상이 "
                    + "아니면 404 SETTLEMENT_SHARE_NOT_FOUND. paid=false 로 보내면 체크 취소. 변경 후 전체 정산 현황을 반환한다.")
    @PutMapping("/shares/{userId}")
    public ApiResponse<SettlementResponse> markPaid(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @PathVariable long bandId,
                                                    @PathVariable long reservationId,
                                                    @PathVariable long userId,
                                                    @Valid @RequestBody UpdateSharePaidRequest request) {
        return ApiResponse.ok(settlementService.markPaid(
                bandId, reservationId, userId, principal.userId(), request.paid()));
    }
}
