package com.yeka.bandapp.user.dto;

import com.yeka.bandapp.common.security.TokenPair;

/**
 * @param expiresIn access 토큰 만료까지 남은 초 (클라이언트가 갱신 시점 계산에 쓴다)
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static TokenResponse from(TokenPair pair) {
        return new TokenResponse(
                pair.accessToken(),
                pair.refreshToken(),
                "Bearer",
                pair.accessTokenTtl().toSeconds());
    }
}
