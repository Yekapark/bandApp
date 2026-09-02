package com.yeka.bandapp.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 사용자 차단 요청. 밴드와 무관한 전역 차단이다. */
public record CreateBlockRequest(
        @Schema(description = "차단할 사용자 id.", example = "7")
        @NotNull @Positive Long blockedUserId
) {
}
