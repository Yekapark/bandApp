package com.yeka.bandapp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "가입한 이메일(대소문자 무관).", example = "leader@test.app")
        @NotBlank String email,

        @Schema(example = "pw12345678")
        @NotBlank String password
) {
}
