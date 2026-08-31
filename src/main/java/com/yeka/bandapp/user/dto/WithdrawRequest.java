package com.yeka.bandapp.user.dto;

/**
 * 회원 탈퇴 요청. 이메일 계정은 {@code password} 재확인이 필요하고, 카카오 계정은 무시된다
 * (Bearer 토큰 보유만으로 진행).
 */
public record WithdrawRequest(
        String password
) {
}
