package com.yeka.bandapp.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 완료 기준: 가입 → 로그인 → 내 정보 → 토큰 갱신 → (재사용 거부) → 탈퇴 → 로그인 차단.
 */
class AuthLifecycleIntegrationTest extends ApiIntegrationTest {

    @Test
    void full_lifecycle() {
        // 1. 가입 → 201 + 토큰
        ResponseEntity<String> signup = post("/api/v1/auth/signup",
                """
                {"email":"lifecycle@band.app","password":"pw12345678","name":"홍길동"}
                """);
        assertThat(signup.getStatusCode().value()).isEqualTo(201);
        JsonNode signupData = body(signup).get("data");
        String access = signupData.at("/tokens/accessToken").asText();
        assertThat(signupData.at("/user/email").asText()).isEqualTo("lifecycle@band.app");
        assertThat(access).isNotBlank();

        // 2. 내 정보 → 200
        ResponseEntity<String> me = get("/api/v1/users/me", access);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(body(me).at("/data/name").asText()).isEqualTo("홍길동");

        // 3. 로그인 → 200
        ResponseEntity<String> login = post("/api/v1/auth/login",
                """
                {"email":"lifecycle@band.app","password":"pw12345678"}
                """);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        String refresh = body(login).at("/data/tokens/refreshToken").asText();

        // 4. 토큰 갱신 → 200, 새 토큰은 이전과 다름
        ResponseEntity<String> refreshed = post("/api/v1/auth/refresh",
                "{\"refreshToken\":\"" + refresh + "\"}");
        assertThat(refreshed.getStatusCode().value()).isEqualTo(200);
        String newRefresh = body(refreshed).at("/data/refreshToken").asText();
        String newAccess = body(refreshed).at("/data/accessToken").asText();
        assertThat(newRefresh).isNotEqualTo(refresh);

        // 5. 이전 refresh 재사용 → 401
        ResponseEntity<String> reused = post("/api/v1/auth/refresh",
                "{\"refreshToken\":\"" + refresh + "\"}");
        assertThat(reused.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(reused)).isEqualTo("REFRESH_TOKEN_INVALID");

        // 6. 탈퇴 → 204
        ResponseEntity<String> withdraw = post("/api/v1/users/me/withdraw",
                "{\"password\":\"pw12345678\"}", newAccess);
        assertThat(withdraw.getStatusCode().value()).isEqualTo(204);

        // 7. 탈퇴 후 로그인 → 401
        ResponseEntity<String> loginAfter = post("/api/v1/auth/login",
                """
                {"email":"lifecycle@band.app","password":"pw12345678"}
                """);
        assertThat(loginAfter.getStatusCode().value()).isEqualTo(401);

        // 8. 탈퇴 전 발급된 access 토큰도 즉시 무효 (차단 목록)
        ResponseEntity<String> meAfter = get("/api/v1/users/me", newAccess);
        assertThat(meAfter.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(meAfter)).isEqualTo("ACCOUNT_WITHDRAWN");
    }
}
