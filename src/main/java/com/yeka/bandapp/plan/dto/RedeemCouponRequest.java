package com.yeka.bandapp.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RedeemCouponRequest(
        @Schema(description = "운영자가 발급한 8자 쿠폰 코드(대소문자 무관)", example = "BANDULE7")
        @NotBlank String code
) {
}
