package com.yeka.bandapp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @Schema(description = "정리할 세션의 refreshToken")
        @NotBlank String refreshToken,

        @Schema(description = "이 기기의 푸시(FCM) 토큰. 주면 이 기기로 가던 푸시를 끊는다 — 로그아웃한 폰에 이전 계정의 "
                + "일정·정산 알림이 계속 가지 않게. refresh 토큰이 이미 만료됐어도 지운다(선택).")
        String deviceToken
) {
}
