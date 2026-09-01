package com.yeka.bandapp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원 탈퇴 요청. 이메일 계정은 {@code password} 재확인이 필요하고, 카카오 계정은 무시된다
 * (Bearer 토큰 보유만으로 진행).
 */
public record WithdrawRequest(
        @Schema(description = "이메일 계정만 필요(현재 비밀번호 재확인). 소셜 계정은 빈 본문 {} 로 호출.",
                example = "pw12345678")
        String password
) {
}
