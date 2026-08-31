package com.yeka.bandapp.common.security;

import java.time.Duration;

/**
 * 새로 발급된 access/refresh 토큰 한 쌍.
 *
 * @param refreshJti refresh 토큰의 {@code jti} 클레임. Redis에 세션 식별자로 저장된다.
 * @param accessTokenTtl access 토큰 만료까지 남은 시간 (응답의 {@code expiresIn} 계산용)
 */
public record TokenPair(
        String accessToken,
        String refreshToken,
        String refreshJti,
        Duration accessTokenTtl
) {
}
