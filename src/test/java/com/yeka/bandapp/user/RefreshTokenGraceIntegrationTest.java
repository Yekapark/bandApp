package com.yeka.bandapp.user;

import com.yeka.bandapp.common.security.JwtProperties;
import com.yeka.bandapp.common.security.JwtTokenProvider;
import com.yeka.bandapp.support.ApiIntegrationTest;
import com.yeka.bandapp.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * refresh 회전의 두 상반된 요구를 함께 검증한다.
 *
 * <ul>
 *   <li>정상 클라이언트의 재시도·더블탭(같은 토큰 재전송)은 전 세션 로그아웃을 유발하지 않는다.</li>
 *   <li>등록된 적 없는(위조·탈취) refresh 토큰은 여전히 전 세션을 끊는다.</li>
 * </ul>
 */
class RefreshTokenGraceIntegrationTest extends ApiIntegrationTest {

    @Autowired
    JwtProperties jwtProperties;

    private static final String SIGNUP = """
            {"email":"grace@band.app","password":"pw12345678","name":"유예"}
            """;

    @Test
    void repeated_refresh_with_the_same_token_is_idempotent_and_keeps_sessions() {
        String access = body(post("/api/v1/auth/signup", SIGNUP)).at("/data/tokens/accessToken").asText();
        String refresh = body(post("/api/v1/auth/login",
                "{\"email\":\"grace@band.app\",\"password\":\"pw12345678\"}"))
                .at("/data/tokens/refreshToken").asText();

        ResponseEntity<String> first = post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}");
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        String rotated = body(first).at("/data/refreshToken").asText();

        // 같은(옛) 토큰으로 두 번 더 — 매번 첫 번째와 동일한 응답.
        for (int i = 0; i < 2; i++) {
            ResponseEntity<String> again = post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}");
            assertThat(again.getStatusCode().value()).isEqualTo(200);
            assertThat(body(again).at("/data/refreshToken").asText()).isEqualTo(rotated);
        }

        // 회전으로 받은 새 토큰은 여전히 살아 있다 = 세션이 안 끊겼다.
        assertThat(post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + rotated + "\"}")
                .getStatusCode().value()).isEqualTo(200);
        // 로그인 때 받은 access 도 계속 유효.
        assertThat(get("/api/v1/users/me", access).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void never_issued_refresh_token_still_drops_all_sessions() {
        String access = body(post("/api/v1/auth/signup", SIGNUP)).at("/data/tokens/accessToken").asText();
        long userId = body(get("/api/v1/users/me", access)).at("/data/id").asLong();
        String realRefresh = body(post("/api/v1/auth/login",
                "{\"email\":\"grace@band.app\",\"password\":\"pw12345678\"}"))
                .at("/data/tokens/refreshToken").asText();

        // 서명은 유효하지만 Redis 에 등록된 적 없는 refresh 토큰 (탈취·위조 시나리오).
        JwtTokenProvider foreign = new JwtTokenProvider(new JwtProperties(
                IntegrationTestSupport.TEST_JWT_SECRET, jwtProperties.issuer(),
                Duration.ofMinutes(30), Duration.ofDays(14)));
        String forged = foreign.issue(userId).refreshToken();

        ResponseEntity<String> attack = post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + forged + "\"}");
        assertThat(attack.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(attack)).isEqualTo("REFRESH_TOKEN_INVALID");

        // 방어 발동: 진짜 refresh 토큰도 이제 무효.
        assertThat(post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + realRefresh + "\"}")
                .getStatusCode().value()).isEqualTo(401);
    }
}
