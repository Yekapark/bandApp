package com.yeka.bandapp.plan.controller;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.plan.dto.PlanResponse;
import com.yeka.bandapp.plan.dto.RedeemCouponRequest;
import com.yeka.bandapp.plan.service.PlanCouponService;
import com.yeka.bandapp.plan.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 밴드 요금제(FREE/PREMIUM). 조회는 밴드 멤버, 전환(구독/해지/갱신)은 밴드장만 한다.
 *
 * <p>결제 자체는 앱 밖(앱스토어·구글플레이 결제 모듈)에서 이루어진다 — 여기서는 no-op 게이트웨이를
 * 거쳐 요금제 상태만 바꾼다. PREMIUM 전환 시 밴드의 기존 첨부 미디어 보관기한이 무제한으로,
 * 해지 시 30일 유예로 재계산된다.
 */
@Tag(name = "16. 요금제",
        description = "밴드 FREE/PREMIUM 요금제 조회·전환. 전환 시 첨부 미디어 보관기한 재계산 "
                + "(업그레이드=무제한, 다운그레이드=30일 유예). 실제 결제 연동은 포함하지 않는다.")
@RestController
@RequestMapping("/api/v1/bands/{bandId}/plan")
public class PlanController {

    private final PlanService planService;
    private final PlanCouponService planCouponService;

    public PlanController(PlanService planService, PlanCouponService planCouponService) {
        this.planService = planService;
        this.planCouponService = planCouponService;
    }

    @Operation(summary = "현재 요금제 조회",
            description = "밴드 멤버면 누구나. 밴드가 없거나 멤버가 아니면 403 NOT_BAND_MEMBER.")
    @GetMapping
    public ApiResponse<PlanResponse> view(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable long bandId) {
        return ApiResponse.ok(planService.view(bandId, principal.userId()));
    }

    @Operation(summary = "PREMIUM 구독 시작",
            description = "FREE → PREMIUM. 밴드장만(그 외 403 NOT_BAND_LEADER). 이미 PREMIUM 이면 "
                    + "409 PLAN_ALREADY_PREMIUM, 결제 실패면 402 PAYMENT_FAILED. 성공 시 밴드의 기존 "
                    + "READY 미디어 보관기한이 무제한으로 바뀐다(이미 만료·삭제된 미디어는 복구되지 않는다).")
    @PostMapping("/subscribe")
    public ApiResponse<PlanResponse> subscribe(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable long bandId) {
        return ApiResponse.ok(planService.subscribe(bandId, principal.userId()));
    }

    @Operation(summary = "PREMIUM 구독 해지",
            description = "PREMIUM → FREE. 밴드장만(그 외 403 NOT_BAND_LEADER). 이미 FREE 이면 "
                    + "409 PLAN_ALREADY_FREE. 밴드의 기존 READY 미디어는 해지 시점부터 30일간 유예된 뒤 만료된다.")
    @PostMapping("/cancel")
    public ApiResponse<PlanResponse> cancel(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable long bandId) {
        return ApiResponse.ok(planService.cancel(bandId, principal.userId()));
    }

    @Operation(summary = "PREMIUM 구독기간 연장",
            description = "PREMIUM 구독기간 종료일을 연장한다. 밴드장만(그 외 403 NOT_BAND_LEADER). "
                    + "FREE 이면 409 PLAN_ALREADY_FREE.")
    @PostMapping("/renew")
    public ApiResponse<PlanResponse> renew(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable long bandId) {
        return ApiResponse.ok(planService.renew(bandId, principal.userId()));
    }

    @Operation(summary = "맛보기 쿠폰 사용",
            description = "운영자가 발급한 쿠폰 코드로 PREMIUM 기간을 얻는다. 밴드장만"
                    + "(그 외 403 NOT_BAND_LEADER). 이미 PREMIUM 이면 남은 기간에 더한다. "
                    + "없는 코드 404 COUPON_NOT_FOUND, 기한 지남 410 COUPON_EXPIRED, "
                    + "모두 사용됨 409 COUPON_EXHAUSTED, 이 밴드가 이미 쓴 쿠폰 409 COUPON_ALREADY_USED.")
    @PostMapping("/coupons/redeem")
    public ApiResponse<PlanResponse> redeemCoupon(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable long bandId,
                                                  @Valid @RequestBody RedeemCouponRequest request) {
        return ApiResponse.ok(planCouponService.redeem(bandId, principal.userId(), request.code()));
    }
}
