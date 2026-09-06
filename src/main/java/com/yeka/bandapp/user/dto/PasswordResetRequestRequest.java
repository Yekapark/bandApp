package com.yeka.bandapp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequestRequest(
        @Schema(description = "비밀번호를 재설정할 이메일 계정.", example = "leader@test.app")
        @NotBlank @Email @Size(max = 255) String email
) {
}
