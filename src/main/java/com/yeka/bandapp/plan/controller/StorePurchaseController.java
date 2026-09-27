package com.yeka.bandapp.plan.controller;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.plan.dto.RestoredPurchaseResponse;
import com.yeka.bandapp.plan.dto.VerifyGooglePurchaseRequest;
import com.yeka.bandapp.plan.service.StoreSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 밴드를 경로에 두지 않는 구매 엔드포인트. 밴드는 구매 자체에 적힌 값으로 정해진다
 * ({@code PurchaseBandTag}) — 앱이 "지금 선택된 밴드" 를 보내지 않게 하려는 것이다.
 * 밴드 소속 검증은 서비스가 그 밴드의 밴드장인지로 한다.
 */
@Tag(name = "16. 요금제")
@RestController
@RequestMapping("/api/v1/plan")
public class StorePurchaseController {

    private final StoreSubscriptionService storeSubscriptionService;

    public StorePurchaseController(StoreSubscriptionService storeSubscriptionService) {
        this.storeSubscriptionService = storeSubscriptionService;
    }

    @Operation(summary = "검증 못 끝낸 Google Play 구매 복구",
            description = "결제 직후 검증 전에 앱이 꺼졌거나 네트워크가 끊긴 구매를 앱이 다음 실행 때 보낸다. "
                    + "구매에 적힌 밴드(obfuscatedAccountId = band-{id})에 PREMIUM 을 반영하고 확인 처리한다. "
                    + "그 밴드의 밴드장만(그 외 403 NOT_BAND_LEADER). 토큰이 스토어에 없거나 구독이 유효하지 않으면 "
                    + "402 PURCHASE_NOT_VERIFIED, 적힌 밴드가 없으면 422 PURCHASE_BAND_UNKNOWN "
                    + "(요금제 화면의 밴드별 검증으로 처리). 이미 반영된 구매를 다시 보내도 안전하다.")
    @PostMapping("/google/restore")
    public ApiResponse<RestoredPurchaseResponse> restoreGoogle(@AuthenticationPrincipal AuthPrincipal principal,
                                                               @Valid @RequestBody VerifyGooglePurchaseRequest request) {
        return ApiResponse.ok(
                storeSubscriptionService.restoreGooglePurchase(principal.userId(), request.purchaseToken()));
    }
}
