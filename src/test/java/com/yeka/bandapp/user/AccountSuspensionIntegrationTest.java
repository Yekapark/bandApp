package com.yeka.bandapp.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계정 이용 정지(LAUNCH_REVIEW P7, 약관 제14조). 정지는 운영 스크립트({@code tools/moderate.py})가 DB·Redis 에
 * 직접 적으므로, 여기서도 스크립트와 같은 SQL·Redis 명령으로 정지·해제하고 앱의 반응을 본다 — 스크립트와 서버가
 * 약속한 형식(칼럼 이름, {@code auth:blocked:{id}} = {@code "S"})이 깨지면 이 테스트가 깨진다.
 */
class AccountSuspensionIntegrationTest extends ApiIntegrationTest {

    private static final String PASSWORD = "pw12345678";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void 정지되면_쓰던_토큰도_갱신도_로그인도_막히고_기간이_지나면_다시_로그인된다() {
        String email = "suspend-period@band.app";
        JsonNode signup = signup(email);
        long userId = signup.at("/user/id").asLong();
        String access = signup.at("/tokens/accessToken").asText();
        String refresh = signup.at("/tokens/refreshToken").asText();

        // 운영 스크립트: 7일 정지 + 즉시 차단
        jdbc.update("UPDATE users SET suspended_until = now() + interval '7 days', suspension_reason = ? WHERE id = ?",
                "테스트 — 반복 신고", userId);
        redis.opsForValue().set("auth:blocked:" + userId, "S", Duration.ofMinutes(30));

        // 1) 쓰던 access 토큰 → 정지(탈퇴라고 답하지 않는다)
        ResponseEntity<String> me = get("/api/v1/users/me", access);
        assertThat(me.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(me)).isEqualTo("ACCOUNT_SUSPENDED");

        // 2) 앱은 401 이면 갱신을 시도한다 → 갱신도 정지로 거절, 기간과 문의처를 알린다. 사유는 싣지 않는다.
        ResponseEntity<String> refreshed = post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}");
        assertThat(refreshed.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(refreshed)).isEqualTo("ACCOUNT_SUSPENDED");
        String message = body(refreshed).at("/error/message").asText();
        assertThat(message).contains("까지").contains("notice@bandule.com").doesNotContain("반복 신고");

        // 3) 다시 로그인 → 정지. 비밀번호가 틀리면 정지 여부를 알리지 않는다.
        assertThat(errorCode(login(email, PASSWORD))).isEqualTo("ACCOUNT_SUSPENDED");
        assertThat(errorCode(login(email, "wrong-password"))).isEqualTo("INVALID_CREDENTIALS");

        // 4) 기간이 지나면(해제) 다시 로그인된다. 정지 때 지운 옛 세션은 되살아나지 않는다.
        jdbc.update("UPDATE users SET suspended_until = now() - interval '1 second' WHERE id = ?", userId);
        redis.delete("auth:blocked:" + userId);
        ResponseEntity<String> again = login(email, PASSWORD);
        assertThat(again.getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> oldRefresh = post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}");
        assertThat(errorCode(oldRefresh)).isEqualTo("REFRESH_TOKEN_INVALID");
        String newAccess = body(again).at("/data/tokens/accessToken").asText();
        assertThat(get("/api/v1/users/me", newAccess).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void 영구_정지는_기간_없이_안내한다() {
        String email = "suspend-forever@band.app";
        long userId = signup(email).at("/user/id").asLong();
        jdbc.update("UPDATE users SET suspended_until = '9999-12-31T00:00:00Z' WHERE id = ?", userId);

        ResponseEntity<String> res = login(email, PASSWORD);
        assertThat(errorCode(res)).isEqualTo("ACCOUNT_SUSPENDED");
        assertThat(body(res).at("/error/message").asText())
                .doesNotContain("까지").contains("notice@bandule.com");
    }

    private JsonNode signup(String email) {
        ResponseEntity<String> res = post("/api/v1/auth/signup",
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"name\":\"정지테스트\"}");
        assertThat(res.getStatusCode().value()).isEqualTo(201);
        return body(res).get("data");
    }

    private ResponseEntity<String> login(String email, String password) {
        return post("/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }
}
