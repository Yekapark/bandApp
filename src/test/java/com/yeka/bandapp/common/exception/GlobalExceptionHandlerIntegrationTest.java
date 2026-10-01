package com.yeka.bandapp.common.exception;

import com.yeka.bandapp.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스프링이 컨트롤러 전에 거절하는 요청(없는 주소·안 받는 메서드·Content-Type)이 500 "서버 오류" 가 아니라
 * 제 상태코드와 공통 응답 포맷으로 나가는지.
 */
class GlobalExceptionHandlerIntegrationTest extends ApiIntegrationTest {

    @Test
    void unknown_path_is_404_not_500() {
        ResponseEntity<String> res = get("/.well-known/does-not-exist");

        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(res)).isEqualTo("NOT_FOUND");
    }

    @Test
    void wrong_method_is_405_not_500() {
        ResponseEntity<String> res = delete("/api/v1/auth/login", null);

        assertThat(res.getStatusCode().value()).isEqualTo(405);
        assertThat(errorCode(res)).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void unsupported_content_type_is_415_not_500() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        ResponseEntity<String> res = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>("hello", headers), String.class);

        assertThat(res.getStatusCode().value()).isEqualTo(415);
        assertThat(errorCode(res)).isEqualTo("INVALID_INPUT");
    }
}
