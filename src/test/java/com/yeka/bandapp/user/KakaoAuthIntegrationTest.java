package com.yeka.bandapp.user;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.support.ApiIntegrationTest;
import com.yeka.bandapp.support.FakeKakaoClient;
import com.yeka.bandapp.support.KakaoTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@Import(KakaoTestConfig.class)
class KakaoAuthIntegrationTest extends ApiIntegrationTest {

    @Autowired
    FakeKakaoClient kakao;

    @BeforeEach
    void resetKakao() {
        kakao.reset();
    }

    private ResponseEntity<String> kakaoLogin() {
        return post("/api/v1/auth/kakao", "{\"accessToken\":\"any-token\"}");
    }

    @Test
    void first_login_creates_account_then_relogin_reuses_it() {
        kakao.willReturnUser("kakao-777", "k@band.app", "카카오철수");

        ResponseEntity<String> first = kakaoLogin();
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(body(first).at("/data/newUser").asBoolean()).isTrue();
        long firstId = body(first).at("/data/user/id").asLong();

        ResponseEntity<String> second = kakaoLogin();
        assertThat(body(second).at("/data/newUser").asBoolean()).isFalse();
        assertThat(body(second).at("/data/user/id").asLong()).isEqualTo(firstId);
    }

    @Test
    void account_without_email_or_nickname_still_signs_up() {
        kakao.willReturnUser("kakao-noinfo", null, null);

        ResponseEntity<String> res = kakaoLogin();
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(body(res).at("/data/user/email").isNull()).isTrue();
        assertThat(body(res).at("/data/user/name").asText()).isNotBlank();
    }

    @Test
    void token_from_another_app_is_rejected() {
        kakao.willReturnAppId(123456L); // app.kakao.app-id=999999 와 불일치

        ResponseEntity<String> res = kakaoLogin();
        assertThat(res.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(res)).isEqualTo("KAKAO_APP_MISMATCH");
    }

    @Test
    void kakao_api_failure_maps_to_502() {
        kakao.willFailFetchWith(new BusinessException(ErrorCode.KAKAO_API_ERROR));

        ResponseEntity<String> res = kakaoLogin();
        assertThat(res.getStatusCode().value()).isEqualTo(502);
        assertThat(errorCode(res)).isEqualTo("KAKAO_API_ERROR");
    }

    @Test
    void withdraw_calls_unlink() {
        kakao.willReturnUser("kakao-unlink-me", "u@band.app", "언링크");
        String access = body(kakaoLogin()).at("/data/tokens/accessToken").asText();

        ResponseEntity<String> res = post("/api/v1/users/me/withdraw", "{}", access);
        assertThat(res.getStatusCode().value()).isEqualTo(204);
        assertThat(kakao.unlinkedIds()).containsExactly("kakao-unlink-me");
    }

    @Test
    void withdraw_succeeds_even_when_unlink_fails() {
        kakao.willReturnUser("kakao-flaky", "f@band.app", "플레이키");
        String access = body(kakaoLogin()).at("/data/tokens/accessToken").asText();
        kakao.willFailUnlinkWith(new BusinessException(ErrorCode.KAKAO_API_ERROR));

        ResponseEntity<String> res = post("/api/v1/users/me/withdraw", "{}", access);
        assertThat(res.getStatusCode().value()).isEqualTo(204);

        // 재로그인 시 새 계정이 만들어진다(기존 계정은 탈퇴 처리됨).
        kakao.willFailUnlinkWith(null);
        assertThat(body(kakaoLogin()).at("/data/newUser").asBoolean()).isTrue();
    }
}
