package com.yeka.bandapp.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 본인 몫의 납부 상태 변경 요청. {@code false}로 보내면 체크 취소. */
public record UpdateSharePaidRequest(
        @Schema(description = "납부 완료 여부.", example = "true")
        @NotNull Boolean paid
) {
}
