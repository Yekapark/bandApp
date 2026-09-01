package com.yeka.bandapp.band.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record DelegateLeadershipRequest(
        @Schema(description = "밴드장을 넘겨받을 멤버의 user id (그 밴드의 활성 멤버여야 함)", example = "42")
        @NotNull Long newLeaderUserId
) {
}
