package com.yeka.bandapp.plan.controller;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.plan.dto.PlanResponse;
import com.yeka.bandapp.plan.dto.RedeemCouponRequest;
import com.yeka.bandapp.plan.dto.VerifyGooglePurchaseRequest;
import com.yeka.bandapp.plan.service.PlanCouponService;
import com.yeka.bandapp.plan.service.PlanService;
import com.yeka.bandapp.plan.service.StoreSubscriptionService;
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
 * 밴드 요금제(FREE/PREMIUM). 조회는 밴드 멤버, 전환은 밴드장만.
 *
 * <p><b>구독은 스토어에서 산다.</b> 클라이언트가 Play Billing 으로 결제하고 받은 구매 토큰을
 * {@code POST /google/verify} 로 보내면 서버가 스토어에 확인하고 PREMIUM 으로 올린다. 갱신·해지·환불은
 * 사용자가 Play 스토어에서 하고, 서버는 RTDN 웹훅({@code /api/v1/webhooks/google-play})으로 받는다 —
 * 이 컨트롤러에 해지·갱신 엔드포인트는 없다.
 *
 * <p>맛보기 쿠폰({@code /coupons/redeem})은 결제와 무관하게 PREMIUM 기간을 준다.
 */
@Tag(name = "16. 요금제",
        description = "밴드 FREE/PREMIUM 요금제 조회, Play 결제 검증, 맛보기 쿠폰. 전환 시 첨부 미디어 "
                + "보관기한 재계산(업그레이드=무제한, 다운그레이드=30일 유예).")
@RestController
@RequestMapping("/api/v1/bands/{bandId}/plan")
public class PlanController {

    private final PlanService planService;
    private final StoreSubscriptionService storeSubscriptionService;
    private final PlanCouponService planCouponService;

    public PlanController(PlanService planService, StoreSubscriptionService storeSubscriptionService,
                         PlanCouponService planCouponService) {
        this.planService = planService;
        this.storeSubscriptionService = storeSubscriptionService;
        this.planCouponService = planCouponService;
    }

    @Operation(summary = "현재 요금제 조회",
            description = "밴드 멤버면 누구나. 밴드가 없거나 멤버가 아니면 403 NOT_BAND_MEMBER.")
    @GetMapping
    public ApiResponse<PlanResponse> view(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable long bandId) {
        return ApiResponse.ok(planService.view(bandId, principal.userId()));
    }

    @Operation(summary = "Google Play 결제 검증",
            description = "클라이언트가 Play Billing 으로 결제하고 받은 구매 토큰을 검증해 PREMIUM 으로 올린다. "
                    + "밴드장만(그 외 403 NOT_BAND_LEADER). 토큰이 스토어에 없거나 구독이 유효 상태가 아니면 "
                    + "402 PURCHASE_NOT_VERIFIED. 이미 PREMIUM 이면 조회된 만료일로 연장한다(재전송에 안전). "
                    + "성공 시 밴드의 기존 READY 미디어 보관기한이 무제한으로 바뀐다.")
    @PostMapping("/google/verify")
    public ApiResponse<PlanResponse> verifyGoogle(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable long bandId,
                                                  @Valid @RequestBody VerifyGooglePurchaseRequest request) {
        return ApiResponse.ok(
                storeSubscriptionService.verifyGooglePurchase(bandId, principal.userId(), request.purchaseToken()));
    }

    @Operation(summary = "맛보기 쿠폰 사용",
            description = "운영자가 발급한 쿠폰 코드로 PREMIUM 기간을 얻는다. 밴드장만"
                    + "(그 외 403 NOT_BAND_LEADER). 이미 PREMIUM 이면 남은 기간에 더한다. "
                    + "없는 코드 404 COUPON_NOT_FOUND, 기한 지남 410 COUPON_EXPIRED, "
                    + "모두 사용됨 409 COUPON_EXHAUSTED, 이 밴드 또는 이 계정이 이미 쓴 쿠폰 409 "
                    + "COUPON_ALREADY_USED(한 계정은 한 쿠폰을 한 번만 — 밴드를 여러 개 만들어도 같다).")
    @PostMapping("/coupons/redeem")
    public ApiResponse<PlanResponse> redeemCoupon(@AuthenticationPrincipal AuthPrincipal principal,
                                                  @PathVariable long bandId,
                                                  @Valid @RequestBody RedeemCouponRequest request) {
        return ApiResponse.ok(planCouponService.redeem(bandId, principal.userId(), request.code()));
    }
}
