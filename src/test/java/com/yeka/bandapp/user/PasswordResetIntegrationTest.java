package com.yeka.bandapp.user;

import com.yeka.bandapp.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비밀번호 재설정. 테스트 환경엔 {@code app.mail.from}이 없어 실제 메일은 안 나가므로,
 * Redis에 저장된 인증번호를 직접 읽어 확인한다({@code EmailSender}가 조용히 건너뛴다).
 */
class PasswordResetIntegrationTest extends ApiIntegrationTest {

    @Autowired
    StringRedisTemplate redis;

    private static final String SIGNUP = """
            {"email":"reset@band.app","password":"pw12345678","name":"재설정"}
            """;

    private String storedCode() {
        return redis.opsForValue().get("auth:pwreset:code:reset@band.app");
    }

    @Test
    void request_then_confirm_changes_password_and_logs_out_all_sessions() {
        post("/api/v1/auth/signup", SIGNUP);
        String oldRefresh = body(post("/api/v1/auth/login",
                "{\"email\":\"reset@band.app\",\"password\":\"pw12345678\"}"))
                .at("/data/tokens/refreshToken").asText();

        assertThat(post("/api/v1/auth/password-reset/request", "{\"email\":\"reset@band.app\"}")
                .getStatusCode().value()).isEqualTo(204);

        String code = storedCode();
        assertThat(code).isNotNull();

        ResponseEntity<String> confirm = post("/api/v1/auth/password-reset/confirm",
                "{\"email\":\"reset@band.app\",\"code\":\"" + code + "\",\"newPassword\":\"newpw12345\"}");
        assertThat(confirm.getStatusCode().value()).isEqualTo(204);

        assertThat(post("/api/v1/auth/login", "{\"email\":\"reset@band.app\",\"password\":\"pw12345678\"}")
                .getStatusCode().value()).isEqualTo(401);
        assertThat(post("/api/v1/auth/login", "{\"email\":\"reset@band.app\",\"password\":\"newpw12345\"}")
                .getStatusCode().value()).isEqualTo(200);

        // 재설정 전 발급된 세션은 끊긴다.
        assertThat(post("/api/v1/auth/refresh", "{\"refreshToken\":\"" + oldRefresh + "\"}")
                .getStatusCode().value()).isEqualTo(401);

        // 인증번호는 1회용 — 같은 코드로 다시 확인하면 거부.
        ResponseEntity<String> replay = post("/api/v1/auth/password-reset/confirm",
                "{\"email\":\"reset@band.app\",\"code\":\"" + code + "\",\"newPassword\":\"anotherpw123\"}");
        assertThat(replay.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(replay)).isEqualTo("PASSWORD_RESET_CODE_INVALID");
    }

    @Test
    void wrong_code_is_rejected_and_unknown_email_is_silently_204() {
        post("/api/v1/auth/signup", SIGNUP);
        post("/api/v1/auth/password-reset/request", "{\"email\":\"reset@band.app\"}");

        String code = storedCode();
        String wrong = "000000".equals(code) ? "111111" : "000000";

        ResponseEntity<String> wrongCode = post("/api/v1/auth/password-reset/confirm",
                "{\"email\":\"reset@band.app\",\"code\":\"" + wrong + "\",\"newPassword\":\"newpw12345\"}");
        assertThat(wrongCode.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(wrongCode)).isEqualTo("PASSWORD_RESET_CODE_INVALID");

        // 존재하지 않는 이메일이어도 열거 방지를 위해 항상 204.
        assertThat(post("/api/v1/auth/password-reset/request", "{\"email\":\"nobody@band.app\"}")
                .getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void five_wrong_attempts_invalidate_the_code() {
        post("/api/v1/auth/signup", SIGNUP);
        post("/api/v1/auth/password-reset/request", "{\"email\":\"reset@band.app\"}");
        String code = storedCode();
        String wrong = "000000".equals(code) ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            post("/api/v1/auth/password-reset/confirm",
                    "{\"email\":\"reset@band.app\",\"code\":\"" + wrong + "\",\"newPassword\":\"newpw12345\"}");
        }

        // 시도 소진 후에는 원래 맞는 코드도 더 이상 통하지 않는다.
        ResponseEntity<String> confirm = post("/api/v1/auth/password-reset/confirm",
                "{\"email\":\"reset@band.app\",\"code\":\"" + code + "\",\"newPassword\":\"newpw12345\"}");
        assertThat(confirm.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(confirm)).isEqualTo("PASSWORD_RESET_CODE_INVALID");
    }
}
