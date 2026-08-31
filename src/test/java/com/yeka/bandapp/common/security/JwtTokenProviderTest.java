package com.yeka.bandapp.common.security;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 스프링 없이 도는 순수 단위 테스트. */
class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-0123456789-abcdefghijklmnopqrstuvwxyz";

    private final JwtTokenProvider provider = new JwtTokenProvider(
            new JwtProperties(SECRET, "bandapp", Duration.ofMinutes(30), Duration.ofDays(14)));

    @Test
    void issue_then_parse_roundtrip() {
        TokenPair pair = provider.issue(42L);

        assertThat(provider.parseAccess(pair.accessToken()).userId()).isEqualTo(42L);
        assertThat(provider.parseRefresh(pair.refreshToken()).jti()).isEqualTo(pair.refreshJti());
    }

    @Test
    void parseAccess_rejects_a_refresh_token() {
        TokenPair pair = provider.issue(1L);

        assertThatThrownBy(() -> provider.parseAccess(pair.refreshToken()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void parseAccess_expired_token_reports_expired() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(
                new JwtProperties(SECRET, "bandapp", Duration.ofSeconds(-5), Duration.ofDays(1)));
        String token = expiredProvider.issue(1L).accessToken();

        assertThatThrownBy(() -> provider.parseAccess(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.ACCESS_TOKEN_EXPIRED);
    }

    @Test
    void token_signed_with_another_key_is_invalid() {
        JwtTokenProvider foreign = new JwtTokenProvider(
                new JwtProperties("a-completely-different-secret-key-abcdefghijklmnop", "bandapp",
                        Duration.ofMinutes(30), Duration.ofDays(1)));
        String token = foreign.issue(1L).accessToken();

        assertThatThrownBy(() -> provider.parseAccess(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }
}
