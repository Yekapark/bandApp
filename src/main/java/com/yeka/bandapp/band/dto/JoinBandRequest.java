package com.yeka.bandapp.band.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record JoinBandRequest(
        @Schema(description = "밴드장이 발급한 8자 초대코드(대소문자 무관)", example = "ABCD2345")
        @NotBlank String code
) {
}
