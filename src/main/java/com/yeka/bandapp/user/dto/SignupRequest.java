package com.yeka.bandapp.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다.") String password,
        @NotBlank @Size(min = 1, max = 30) String name
) {
}
