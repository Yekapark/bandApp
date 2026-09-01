package com.yeka.bandapp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @Schema(description = "정리할 세션의 refreshToken")
        @NotBlank String refreshToken
) {
}
