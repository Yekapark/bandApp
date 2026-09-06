package com.yeka.bandapp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailVerificationConfirmRequest(
        @Schema(description = "메일로 받은 6자리 숫자 인증번호.", example = "482913")
        @NotBlank @Pattern(regexp = "\\d{6}", message = "인증번호는 숫자 6자리입니다.") String code
) {
}
