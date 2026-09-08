package com.yeka.bandapp.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Google Play 결제 검증 요청. 클라이언트가 Play Billing 으로 받은 구매 토큰을 그대로 넘긴다. */
public record VerifyGooglePurchaseRequest(
        @Schema(description = "Play Billing 이 준 구독 구매 토큰(Purchase.purchaseToken).")
        @NotBlank String purchaseToken
) {
}
