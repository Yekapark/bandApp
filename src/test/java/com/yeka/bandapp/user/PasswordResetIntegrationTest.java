package com.yeka.bandapp.user;

import com.yeka.bandapp.common.mail.EmailSender;
import com.yeka.bandapp.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 비밀번호 재설정. 테스트 환경엔 {@code app.mail.from}이 없어 실제 메일은 안 나가므로,
 * Redis에 저장된 인증번호를 직접 읽어 확인한다({@code EmailSender}가 조용히 건너뛴다).
 */
class PasswordResetIntegrationTest extends ApiIntegrationTest {

    @Autowired
    StringRedisTemplate redis;

    /** 실제 발송은 테스트에서 건너뛰지만(발신 계정 없음) 호출 자체는 센다. */
    @MockitoSpyBean
    EmailSender emailSender;

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

    /**
     * LAUNCH_REVIEW U24 — 재설정 전에 받은 access 토큰은 만료 전이어도 바로 401, 새로 로그인한 토큰은 통한다.
     * 본인에게 "비밀번호가 변경되었어요" 메일이 한 통 간다.
     */
    @Test
    void reset_revokes_old_access_tokens_keeps_new_login_and_sends_notice_mail() {
        String oldAccess = body(post("/api/v1/auth/signup", SIGNUP)).at("/data/tokens/accessToken").asText();
        assertThat(get("/api/v1/users/me", oldAccess).getStatusCode().value()).isEqualTo(200);
        post("/api/v1/auth/password-reset/request", "{\"email\":\"reset@band.app\"}");
        assertThat(post("/api/v1/auth/password-reset/confirm",
                "{\"email\":\"reset@band.app\",\"code\":\"" + storedCode() + "\",\"newPassword\":\"newpw12345\"}")
                .getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> stale = get("/api/v1/users/me", oldAccess);
        assertThat(stale.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(stale)).isEqualTo("INVALID_TOKEN");

        String newAccess = body(post("/api/v1/auth/login",
                "{\"email\":\"reset@band.app\",\"password\":\"newpw12345\"}"))
                .at("/data/tokens/accessToken").asText();
        assertThat(get("/api/v1/users/me", newAccess).getStatusCode().value()).isEqualTo(200);

        verify(emailSender, times(1)).send(eq("reset@band.app"), eq("[밴듈] 비밀번호가 변경되었어요"),
                contains("notice@bandule.com"));
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
    void oversized_password_keeps_code_and_old_password_then_72_byte_password_succeeds() {
        post("/api/v1/auth/signup", SIGNUP);
        post("/api/v1/auth/password-reset/request", "{\"email\":\"reset@band.app\"}");
        String code = storedCode();
        String request = "{\"email\":\"reset@band.app\",\"code\":\"" + code
                + "\",\"newPassword\":\"%s\"}";

        var rejected = post("/api/v1/auth/password-reset/confirm", request.formatted("가".repeat(25)));
        assertThat(rejected.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(rejected)).isEqualTo("INVALID_INPUT");
        assertThat(body(rejected).at("/error/fieldErrors/0/field").asText()).isEqualTo("newPassword");
        assertThat(storedCode()).isEqualTo(code);
        assertThat(post("/api/v1/auth/login",
                "{\"email\":\"reset@band.app\",\"password\":\"pw12345678\"}")
                .getStatusCode().value()).isEqualTo(200);

        String acceptedPassword = "가".repeat(24);
        assertThat(post("/api/v1/auth/password-reset/confirm", request.formatted(acceptedPassword))
                .getStatusCode().value()).isEqualTo(204);
        assertThat(post("/api/v1/auth/login",
                "{\"email\":\"reset@band.app\",\"password\":\"" + acceptedPassword + "\"}")
                .getStatusCode().value()).isEqualTo(200);
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

        // 시도 소진 후에는 원래 맞는 코드도 더 이상 통하지 않고, 잠시 후 다시 하라고 답한다.
        ResponseEntity<String> confirm = post("/api/v1/auth/password-reset/confirm",
                "{\"email\":\"reset@band.app\",\"code\":\"" + code + "\",\"newPassword\":\"newpw12345\"}");
        assertThat(confirm.getStatusCode().value()).isEqualTo(429);
        assertThat(errorCode(confirm)).isEqualTo("TOO_MANY_REQUESTS");
        // 잠긴 동안 다시 요청해도 새 번호가 생기지 않는다(요청마다 시도 횟수가 리셋되던 대입 우회 차단).
        post("/api/v1/auth/password-reset/request", "{\"email\":\"reset@band.app\"}");
        assertThat(storedCode()).isNull();
    }

    @Test
    void re_request_resends_same_code_and_keeps_attempt_count() {
        post("/api/v1/auth/signup", SIGNUP);
        post("/api/v1/auth/password-reset/request", "{\"email\":\"reset@band.app\"}");
        String code = storedCode();
        String wrong = "000000".equals(code) ? "111111" : "000000";
        String wrongBody = "{\"email\":\"reset@band.app\",\"code\":\"" + wrong + "\",\"newPassword\":\"newpw12345\"}";

        for (int i = 0; i < 4; i++) {
            post("/api/v1/auth/password-reset/confirm", wrongBody);
        }
        // 재요청: 먼저 받은 메일의 번호가 계속 유효하고, 오답 횟수는 이어진다.
        post("/api/v1/auth/password-reset/request", "{\"email\":\"reset@band.app\"}");
        assertThat(storedCode()).isEqualTo(code);
        assertThat(errorCode(post("/api/v1/auth/password-reset/confirm", wrongBody)))
                .isEqualTo("PASSWORD_RESET_CODE_INVALID");
        assertThat(post("/api/v1/auth/password-reset/confirm",
                "{\"email\":\"reset@band.app\",\"code\":\"" + code + "\",\"newPassword\":\"newpw12345\"}")
                .getStatusCode().value()).isEqualTo(429);
    }

    @Test
    void fifth_attempt_with_right_code_still_succeeds() {
        post("/api/v1/auth/signup", SIGNUP);
        post("/api/v1/auth/password-reset/request", "{\"email\":\"reset@band.app\"}");
        String code = storedCode();
        String wrong = "000000".equals(code) ? "111111" : "000000";
        for (int i = 0; i < 4; i++) {
            post("/api/v1/auth/password-reset/confirm",
                    "{\"email\":\"reset@band.app\",\"code\":\"" + wrong + "\",\"newPassword\":\"newpw12345\"}");
        }
        assertThat(post("/api/v1/auth/password-reset/confirm",
                "{\"email\":\"reset@band.app\",\"code\":\"" + code + "\",\"newPassword\":\"newpw12345\"}")
                .getStatusCode().value()).isEqualTo(204);
    }
}
