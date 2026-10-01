package com.yeka.bandapp.common.ratelimit;

import com.yeka.bandapp.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인증 레이트리밋 버킷은 날 URI 가 아니라 매칭된 매핑으로 센다 —
 * 글자를 퍼센트 인코딩한 주소({@code /auth/%6Cogin})로 같은 엔드포인트를 다른 버킷에서 두드릴 수 없어야 한다.
 */
class AuthRateLimitIntegrationTest extends ApiIntegrationTest {

    private static final String LOGIN = "{\"email\":\"nobody@band.app\",\"password\":\"pw12345678\"}";

    @Test
    void percent_encoded_path_shares_the_login_bucket() throws InterruptedException {
        // 고정 1분 창 — 경계에 걸리면 카운트가 갈려 거짓 실패가 나니 다음 분으로 넘긴다.
        long second = Instant.now().getEpochSecond() % 60;
        if (second >= 50) {
            Thread.sleep((61 - second) * 1000);
        }
        for (int i = 0; i < 30; i++) { // 테스트 상한 auth-per-ip-per-min=30
            assertThat(post("/api/v1/auth/login", LOGIN).getStatusCode().value()).isEqualTo(401);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // URI 객체로 넘겨야 RestTemplate 이 % 를 %25 로 다시 인코딩하지 않는다.
        URI encoded = URI.create(rest.getRootUri() + "/api/v1/auth/%6Cogin");
        var res = rest.exchange(encoded, HttpMethod.POST, new HttpEntity<>(LOGIN, headers), String.class);

        assertThat(res.getStatusCode().value()).isEqualTo(429);
        assertThat(errorCode(res)).isEqualTo("TOO_MANY_REQUESTS");
    }
}
