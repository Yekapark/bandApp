package com.yeka.bandapp.user;

import com.yeka.bandapp.support.ApiIntegrationTest;
import com.yeka.bandapp.support.FakeKakaoClient;
import com.yeka.bandapp.support.KakaoTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이메일 인증. 미인증이어도 다른 기능은 막히지 않는다(느슨한 정책) — 여기서는 상태 전이와
 * 인증번호 검증만 확인한다.
 */
@Import(KakaoTestConfig.class)
class EmailVerificationIntegrationTest extends ApiIntegrationTest {

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    FakeKakaoClient kakao;

    @BeforeEach
    void resetKakao() {
        kakao.reset();
    }

    @Test
    void email_signup_starts_unverified_and_sends_no_code() {
        String access = body(post("/api/v1/auth/signup",
                "{\"email\":\"verify@band.app\",\"password\":\"pw12345678\",\"name\":\"인증\"}"))
                .at("/data/tokens/accessToken").asText();
        long userId = body(get("/api/v1/users/me", access)).at("/data/id").asLong();

        assertThat(body(get("/api/v1/users/me", access)).at("/data/emailVerified").asBoolean()).isFalse();
        // 가입만으로는 인증번호를 발송하지 않는다 — 입력할 화면이 없어 반송만 쌓였다.
        assertThat(redis.opsForValue().get("auth:emailverify:code:" + userId)).isNull();
    }

    @Test
    void kakao_login_starts_already_verified() {
        kakao.willReturnUser("kakao-verify-1", "k@band.app", "카카오인증");

        String access = body(post("/api/v1/auth/kakao", "{\"accessToken\":\"any-token\"}"))
                .at("/data/tokens/accessToken").asText();

        assertThat(body(get("/api/v1/users/me", access)).at("/data/emailVerified").asBoolean()).isTrue();
    }

    @Test
    void confirm_with_correct_code_marks_verified_and_code_is_single_use() {
        String access = body(post("/api/v1/auth/signup",
                "{\"email\":\"verify2@band.app\",\"password\":\"pw12345678\",\"name\":\"인증\"}"))
                .at("/data/tokens/accessToken").asText();
        long userId = body(get("/api/v1/users/me", access)).at("/data/id").asLong();

        // 가입은 코드를 발송하지 않으므로 사용자가 직접 재발송을 눌러 받는다.
        assertThat(post("/api/v1/users/me/email-verification/resend", "{}", access)
                .getStatusCode().value()).isEqualTo(204);
        String code = redis.opsForValue().get("auth:emailverify:code:" + userId);
        assertThat(code).isNotNull();

        ResponseEntity<String> confirm = post("/api/v1/users/me/email-verification/confirm",
                "{\"code\":\"" + code + "\"}", access);
        assertThat(confirm.getStatusCode().value()).isEqualTo(204);
        assertThat(body(get("/api/v1/users/me", access)).at("/data/emailVerified").asBoolean()).isTrue();

        // 같은 코드를 다시 보내면 이미 지워졌으므로 거부.
        ResponseEntity<String> replay = post("/api/v1/users/me/email-verification/confirm",
                "{\"code\":\"" + code + "\"}", access);
        assertThat(replay.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(replay)).isEqualTo("EMAIL_VERIFICATION_CODE_INVALID");
    }

    @Test
    void wrong_code_is_rejected() {
        String access = body(post("/api/v1/auth/signup",
                "{\"email\":\"verify3@band.app\",\"password\":\"pw12345678\",\"name\":\"인증\"}"))
                .at("/data/tokens/accessToken").asText();
        long userId = body(get("/api/v1/users/me", access)).at("/data/id").asLong();
        post("/api/v1/users/me/email-verification/resend", "{}", access);
        String code = redis.opsForValue().get("auth:emailverify:code:" + userId);
        String wrong = "000000".equals(code) ? "111111" : "000000";

        ResponseEntity<String> confirm = post("/api/v1/users/me/email-verification/confirm",
                "{\"code\":\"" + wrong + "\"}", access);
        assertThat(confirm.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(confirm)).isEqualTo("EMAIL_VERIFICATION_CODE_INVALID");
    }

    @Test
    void resend_issues_a_new_code() {
        String access = body(post("/api/v1/auth/signup",
                "{\"email\":\"verify4@band.app\",\"password\":\"pw12345678\",\"name\":\"인증\"}"))
                .at("/data/tokens/accessToken").asText();
        long userId = body(get("/api/v1/users/me", access)).at("/data/id").asLong();

        assertThat(post("/api/v1/users/me/email-verification/resend", "{}", access)
                .getStatusCode().value()).isEqualTo(204);
        assertThat(redis.opsForValue().get("auth:emailverify:code:" + userId)).isNotNull();
    }
}
