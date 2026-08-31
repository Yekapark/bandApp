package com.yeka.bandapp.band;

import com.yeka.bandapp.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BACKLOG 1.8: 초대코드 레이트리밋 인프라를 {@code /api/v1/auth/**} 에도 적용한다.
 * (무차별 대입 / 이메일 열거 / 카카오 API 남용 완화)
 */
class AuthRateLimitIntegrationTest extends ApiIntegrationTest {

    @Test
    void login_endpoint_is_rate_limited_by_ip() {
        int lastStatus = 0;
        for (int i = 0; i < 31; i++) {
            lastStatus = post("/api/v1/auth/login",
                    "{\"email\":\"nobody@band.app\",\"password\":\"pw12345678\"}")
                    .getStatusCode().value();
        }
        // 테스트 설정상 엔드포인트별 분당 30회 → 31번째는 429.
        assertThat(lastStatus).isEqualTo(429);
    }

    @Test
    void a_different_auth_endpoint_keeps_its_own_budget() {
        for (int i = 0; i < 31; i++) {
            post("/api/v1/auth/login", "{\"email\":\"x@band.app\",\"password\":\"pw12345678\"}");
        }
        // /login 예산을 소진해도 /signup 은 별도 버킷이라 정상 처리된다.
        ResponseEntity<String> signup = post("/api/v1/auth/signup",
                "{\"email\":\"budget@band.app\",\"password\":\"pw12345678\",\"name\":\"예산\"}");
        assertThat(signup.getStatusCode().value()).isEqualTo(201);
    }
}
