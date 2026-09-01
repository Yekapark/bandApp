package com.yeka.bandapp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(
        @Schema(description = "로그인/가입 응답의 tokens.refreshToken")
        @NotBlank String refreshToken
) {
}
