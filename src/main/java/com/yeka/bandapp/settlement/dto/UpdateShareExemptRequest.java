package com.yeka.bandapp.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 나간 멤버의 미납 몫 면제 요청(밴드장만). {@code false}로 보내면 면제 취소. */
public record UpdateShareExemptRequest(
        @Schema(description = "면제 여부.", example = "true")
        @NotNull Boolean exempt
) {
}
