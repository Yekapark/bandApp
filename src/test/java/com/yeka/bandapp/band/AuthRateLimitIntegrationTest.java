package com.yeka.bandapp.band;

import com.yeka.bandapp.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import static com.yeka.bandapp.support.RateLimitAssertions.assertRateLimited;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BACKLOG 1.8: 초대코드 레이트리밋 인프라를 {@code /api/v1/auth/**} 에도 적용한다.
 * (무차별 대입 / 이메일 열거 / 카카오 API 남용 완화)
 */
class AuthRateLimitIntegrationTest extends ApiIntegrationTest {

    /** 테스트 설정상 {@code auth-per-ip-per-min} = 30. */
    private static final int AUTH_LIMIT_PER_MIN = 30;

    @Test
    void login_endpoint_is_rate_limited_by_ip() {
        assertRateLimited(AUTH_LIMIT_PER_MIN, () -> post("/api/v1/auth/login",
                "{\"email\":\"nobody@band.app\",\"password\":\"pw12345678\"}")
                .getStatusCode().value());
    }

    @Test
    void a_different_auth_endpoint_keeps_its_own_budget() {
        // 예산이 실제로 소진됐음을 확인하고 넘어간다 — 소진 안 된 채로 signup 이 통과하면
        // "버킷이 분리돼 있다" 를 증명하지 못한다.
        assertRateLimited(AUTH_LIMIT_PER_MIN, () -> post("/api/v1/auth/login",
                "{\"email\":\"x@band.app\",\"password\":\"pw12345678\"}")
                .getStatusCode().value());

        // /login 예산을 소진해도 /signup 은 별도 버킷이라 정상 처리된다.
        ResponseEntity<String> signup = post("/api/v1/auth/signup",
                "{\"email\":\"budget@band.app\",\"password\":\"pw12345678\",\"name\":\"예산\"}");
        assertThat(signup.getStatusCode().value()).isEqualTo(201);
    }
}
