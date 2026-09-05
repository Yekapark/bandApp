package com.yeka.bandapp.band.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record DeleteBandRequest(
        @Schema(description = "삭제를 확인하기 위해 다시 입력하는 밴드 이름. 실제 이름과 정확히 같아야 한다.",
                example = "혁오")
        @NotBlank String confirmName
) {
}
