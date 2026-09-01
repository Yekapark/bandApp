package com.yeka.bandapp.user;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.support.ApiIntegrationTest;
import com.yeka.bandapp.support.FakeKakaoClient;
import com.yeka.bandapp.support.KakaoTestConfig;
import com.yeka.bandapp.user.entity.SocialProvider;
import com.yeka.bandapp.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@Import(KakaoTestConfig.class)
class KakaoAuthIntegrationTest extends ApiIntegrationTest {

    @Autowired
    FakeKakaoClient kakao;

    @Autowired
    UserRepository userRepository;

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
    void concurrent_first_login_of_one_account_never_500() throws Exception {
        kakao.willReturnUser("kakao-race-1", "race@band.app", "레이스");

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Integer>> calls = Collections.nCopies(threads,
                    () -> kakaoLogin().getStatusCode().value());
            List<Integer> codes = pool.invokeAll(calls).stream()
                    .map(f -> {
                        try {
                            return f.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }).toList();

            // ux_users_social_active 경합에서도 500 이 새지 않는다 — 한 요청이 만들고 나머지는 그 계정으로 이어간다.
            assertThat(codes).allSatisfy(c -> assertThat(c).isEqualTo(200));
            assertThat(userRepository.findBySocialProviderAndSocialIdAndDeletedAtIsNull(
                    SocialProvider.KAKAO, "kakao-race-1")).isPresent();
        } finally {
            pool.shutdownNow();
        }
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

    /** 카카오 밴드장이 탈퇴하면 밴드 정리(자동 위임)와 카카오 unlink 가 한 트랜잭션에서 함께 일어난다. */
    @Test
    void kakao_leader_withdrawal_delegates_leadership_and_still_unlinks() {
        kakao.willReturnUser("kakao-band-leader", "kl@band.app", "카카오리더");
        String leader = body(kakaoLogin()).at("/data/tokens/accessToken").asText();
        String member = body(post("/api/v1/auth/signup",
                "{\"email\":\"kwd-member@band.app\",\"password\":\"pw12345678\",\"name\":\"멤버\"}"))
                .at("/data/tokens/accessToken").asText();

        long bandId = body(post("/api/v1/bands", "{\"name\":\"카카오밴드\"}", leader)).at("/data/id").asLong();
        String code = body(post("/api/v1/bands/" + bandId + "/invites", null, leader)).at("/data/code").asText();
        assertThat(post("/api/v1/bands/join", "{\"code\":\"" + code + "\"}", member).getStatusCode().value())
                .isEqualTo(200);

        assertThat(post("/api/v1/users/me/withdraw", "{}", leader).getStatusCode().value()).isEqualTo(204);

        assertThat(kakao.unlinkedIds()).containsExactly("kakao-band-leader");
        // 멤버가 밴드장으로 승격돼 초대코드를 발급할 수 있다.
        assertThat(post("/api/v1/bands/" + bandId + "/invites", null, member).getStatusCode().value())
                .isEqualTo(201);
    }
}
