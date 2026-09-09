package com.yeka.bandapp.plan;

import com.yeka.bandapp.plan.config.StoreBillingProperties;
import com.yeka.bandapp.plan.controller.WebhookAuthenticator;
import com.yeka.bandapp.plan.gateway.google.PubSubOidcVerifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RTDN 웹훅 인증 판정 — Docker 불필요. OIDC 검증기는 가짜로 주입한다.
 */
class WebhookAuthenticatorTest {

    private static StoreBillingProperties props(String secret, String audience, String serviceAccount) {
        return new StoreBillingProperties("noop", secret, null, null, null, null, audience, serviceAccount);
    }

    private static PubSubOidcVerifier verifierReturning(String email) {
        return token -> Optional.ofNullable(email);
    }

    @Test
    void shared_secret_path_when_no_oidc_configured() {
        WebhookAuthenticator auth = new WebhookAuthenticator(
                props("s3cr3t", null, null), verifierReturning(null));

        assertThat(auth.isAuthorized(null, "s3cr3t")).isTrue();
        assertThat(auth.isAuthorized(null, "wrong")).isFalse();
        assertThat(auth.isAuthorized(null, null)).isFalse();
    }

    @Test
    void nothing_configured_denies_everything() {
        WebhookAuthenticator auth = new WebhookAuthenticator(
                props(null, null, null), verifierReturning("x@y.iam.gserviceaccount.com"));

        assertThat(auth.isAuthorized("Bearer whatever", "whatever")).isFalse();
    }

    @Test
    void oidc_path_accepts_a_valid_token() {
        WebhookAuthenticator auth = new WebhookAuthenticator(
                props(null, "bandule-rtdn", "rtdn@proj.iam.gserviceaccount.com"),
                verifierReturning("rtdn@proj.iam.gserviceaccount.com"));

        assertThat(auth.isAuthorized("Bearer good.jwt.token", null)).isTrue();
    }

    /**
     * audience 만 설정된 반쪽 상태는 OIDC 경로를 열지 않는다. audience 는 우리 웹훅 URL 이고,
     * 그 값을 audience 로 하는 진짜 구글 OIDC 토큰은 아무 GCP 계정이나 발급할 수 있다 —
     * "누가 보냈나"를 안 보면 아무나 환불·해지 이벤트를 밀어 넣을 수 있다.
     */
    @Test
    void oidc_path_is_closed_when_the_service_account_is_not_configured() {
        WebhookAuthenticator auth = new WebhookAuthenticator(
                props(null, "bandule-rtdn", null),
                verifierReturning("anyone@some-other-project.iam.gserviceaccount.com"));

        assertThat(auth.isAuthorized("Bearer valid.google.token", null)).isFalse();
    }

    @Test
    void oidc_path_rejects_an_invalid_token() {
        WebhookAuthenticator auth = new WebhookAuthenticator(
                props(null, "bandule-rtdn", "rtdn@proj.iam.gserviceaccount.com"),
                verifierReturning(null));

        assertThat(auth.isAuthorized("Bearer bad", null)).isFalse();
    }

    @Test
    void oidc_path_enforces_the_expected_service_account() {
        WebhookAuthenticator auth = new WebhookAuthenticator(
                props(null, "bandule-rtdn", "rtdn@proj.iam.gserviceaccount.com"),
                verifierReturning("someone-else@proj.iam.gserviceaccount.com"));

        assertThat(auth.isAuthorized("Bearer good.but.wrong.sa", null)).isFalse();
    }

    @Test
    void either_mechanism_passing_is_enough() {
        // OIDC 설정돼 있지만 토큰이 없을 때, 시크릿도 같이 설정돼 있으면 시크릿으로 통과.
        WebhookAuthenticator auth = new WebhookAuthenticator(
                props("s3cr3t", "bandule-rtdn", "rtdn@proj.iam.gserviceaccount.com"),
                verifierReturning(null));

        assertThat(auth.isAuthorized(null, "s3cr3t")).isTrue();
        assertThat(auth.isAuthorized("Bearer bad", "s3cr3t")).isTrue();
        assertThat(auth.isAuthorized("Bearer bad", "nope")).isFalse();
    }
}
