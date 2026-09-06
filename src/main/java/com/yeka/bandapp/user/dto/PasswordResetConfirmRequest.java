package com.yeka.bandapp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @Schema(description = "재설정을 요청한 이메일.", example = "leader@test.app")
        @NotBlank @Email @Size(max = 255) String email,

        @Schema(description = "메일로 받은 6자리 숫자 인증번호.", example = "482913")
        @NotBlank @Pattern(regexp = "\\d{6}", message = "인증번호는 숫자 6자리입니다.") String code,

        @Schema(description = "새 비밀번호. 8~64자.", example = "newpw12345")
        @NotBlank @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다.") String newPassword
) {
}
