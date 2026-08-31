package com.yeka.bandapp.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Flutter 앱이 카카오 SDK 로그인으로 받은 access token. 서버가 이 토큰으로 카카오에 사용자 정보를 조회한다.
 */
public record KakaoLoginRequest(
        @NotBlank String accessToken
) {
}
