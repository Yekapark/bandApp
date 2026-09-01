package com.yeka.bandapp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Schema(description = "이메일. 대소문자·공백은 정규화되어 저장된다.", example = "leader@test.app")
        @NotBlank @Email @Size(max = 255) String email,

        @Schema(description = "비밀번호. 8~64자.", example = "pw12345678")
        @NotBlank @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다.") String password,

        @Schema(description = "표시 이름. 1~30자.", example = "Leader")
        @NotBlank @Size(min = 1, max = 30) String name
) {
}
