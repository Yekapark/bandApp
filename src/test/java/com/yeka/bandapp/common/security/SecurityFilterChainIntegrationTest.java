package com.yeka.bandapp.common.security;

import com.yeka.bandapp.support.ApiIntegrationTest;
import com.yeka.bandapp.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security 도입 후에도 공개 엔드포인트는 열려 있고(Phase 0 회귀 방지), 보호 엔드포인트의 401이
 * 공통 {@code ApiResponse} 포맷으로 나가는지 확인한다.
 */
class SecurityFilterChainIntegrationTest extends ApiIntegrationTest {

    @Autowired
    JwtProperties jwtProperties;

    private JwtTokenProvider providerWith(Duration accessTtl) {
        return new JwtTokenProvider(new JwtProperties(
                IntegrationTestSupport.TEST_JWT_SECRET, jwtProperties.issuer(), accessTtl, Duration.ofDays(1)));
    }

    @Test
    void actuator_health_stays_public() {
        assertThat(get("/actuator/health").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void openapi_docs_stay_public() {
        assertThat(get("/v3/api-docs").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void protected_endpoint_without_token_is_401_in_common_format() {
        ResponseEntity<String> res = get("/api/v1/users/me");

        assertThat(res.getStatusCode().value()).isEqualTo(401);
        assertThat(body(res).at("/success").asBoolean()).isFalse();
        assertThat(errorCode(res)).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void expired_access_token_is_401_expired() {
        String token = providerWith(Duration.ofSeconds(-5)).issue(1L).accessToken();

        ResponseEntity<String> res = get("/api/v1/users/me", token);

        assertThat(res.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(res)).isEqualTo("ACCESS_TOKEN_EXPIRED");
    }

    @Test
    void token_signed_with_wrong_key_is_401_invalid() {
        JwtTokenProvider foreign = new JwtTokenProvider(new JwtProperties(
                "a-completely-different-secret-key-abcdefghijklmnop", jwtProperties.issuer(),
                Duration.ofMinutes(30), Duration.ofDays(1)));
        String token = foreign.issue(1L).accessToken();

        ResponseEntity<String> res = get("/api/v1/users/me", token);

        assertThat(res.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(res)).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void refresh_token_used_as_bearer_is_401_invalid() {
        String refresh = providerWith(Duration.ofMinutes(30)).issue(1L).refreshToken();

        ResponseEntity<String> res = get("/api/v1/users/me", refresh);

        assertThat(res.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(res)).isEqualTo("INVALID_TOKEN");
    }
}
