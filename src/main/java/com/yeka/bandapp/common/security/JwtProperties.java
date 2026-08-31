package com.yeka.bandapp.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT 서명·만료 설정. {@code app.jwt.*}.
 *
 * <p>{@code secret}에 기본값을 두지 않는다. 미설정 시 애플리케이션이 기동에 실패하는 편이,
 * 운영 서버가 알려진 키로 토큰을 서명하는 것보다 안전하다.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "JWT secret은 32바이트 이상이어야 합니다.") String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl
) {
}
