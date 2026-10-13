package com.yeka.bandapp.user.dto;

import com.yeka.bandapp.user.validation.PasswordByteLength;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Schema(description = "이메일. 대소문자·공백은 정규화되어 저장된다.", example = "leader@test.app")
        @NotBlank(message = "이메일을 입력해 주세요.") @Email(message = "이메일 형식이 아니에요.")
        @Size(max = 255, message = "이메일이 너무 길어요.") String email,

        @Schema(description = "비밀번호. 8~64자, UTF-8 72바이트 이내.", example = "pw12345678")
        @NotBlank @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해 주세요.")
        @PasswordByteLength String password,

        @Schema(description = "표시 이름. 1~30자.", example = "Leader")
        @NotBlank(message = "이름을 입력해 주세요.") @Size(min = 1, max = 30, message = "이름은 30자까지 쓸 수 있어요.") String name
) {
}
