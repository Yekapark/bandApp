package com.yeka.bandapp.plan;

import com.yeka.bandapp.plan.config.PlanProperties;
import com.yeka.bandapp.plan.config.StoreBillingProperties;
import com.yeka.bandapp.plan.entity.Store;
import com.yeka.bandapp.plan.gateway.NoOpStoreBillingGateway;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway.StoreSubscription;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway.StoreSubscriptionState;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NoOpStoreBillingGateway} 단위 테스트 — Docker 불필요.
 * 토큰 접두사로 상태를 흉내내고, 만료는 {@code now + premiumPeriodDays}(1년).
 * 단, {@code prod} 프로파일에서는 아무것도 통과시키지 않는다.
 */
class NoOpStoreBillingGatewayTest {

    private final NoOpStoreBillingGateway gateway = gatewayFor(new MockEnvironment());

    private static NoOpStoreBillingGateway gatewayFor(MockEnvironment environment) {
        return new NoOpStoreBillingGateway(
                new PlanProperties(365, 30, "-", "Asia/Seoul"),
                new StoreBillingProperties("noop", null, null, null, null, null, null, null),
                environment);
    }

    @Test
    void plain_token_is_active_with_a_one_year_expiry() {
        StoreSubscription sub = gateway.fetch(Store.GOOGLE_PLAY, "tok42").orElseThrow();

        assertThat(sub.state()).isEqualTo(StoreSubscriptionState.ACTIVE);
        assertThat(sub.state().grantsPremium()).isTrue();
        assertThat(sub.purchaseToken()).isEqualTo("tok42");
        assertThat(sub.acknowledged()).isTrue();
        assertThat(Duration.between(Instant.now(), sub.expiryTime()).toDays()).isBetween(364L, 366L);
    }

    @Test
    void blank_or_invalid_token_returns_empty() {
        assertThat(gateway.fetch(Store.GOOGLE_PLAY, "")).isEmpty();
        assertThat(gateway.fetch(Store.GOOGLE_PLAY, "  ")).isEmpty();
        assertThat(gateway.fetch(Store.GOOGLE_PLAY, "invalid-abc")).isEmpty();
    }

    @Test
    void token_prefix_picks_the_state() {
        assertThat(gateway.fetch(Store.GOOGLE_PLAY, "revoked-x").orElseThrow().state())
                .isEqualTo(StoreSubscriptionState.REVOKED);
        assertThat(gateway.fetch(Store.GOOGLE_PLAY, "expired-x").orElseThrow().state())
                .isEqualTo(StoreSubscriptionState.EXPIRED);
        assertThat(gateway.fetch(Store.GOOGLE_PLAY, "canceled-x").orElseThrow().state())
                .isEqualTo(StoreSubscriptionState.CANCELED);
        assertThat(gateway.fetch(Store.GOOGLE_PLAY, "hold-x").orElseThrow().state())
                .isEqualTo(StoreSubscriptionState.ON_HOLD);
    }

    /**
     * 운영에 이 게이트웨이가 뜨는 건 설정 사고다(PLAN_BILLING_GATEWAY 가 컨테이너에 안 실린 경우).
     * 그때 아무 토큰이나 ACTIVE 로 통과시키면 밴드장이 결제 없이 PREMIUM 1년을 가져간다.
     */
    @Test
    void prod_profile_refuses_every_token() {
        NoOpStoreBillingGateway prod = gatewayFor(new MockEnvironment().withProperty(
                "spring.profiles.active", "prod"));

        assertThat(prod.fetch(Store.GOOGLE_PLAY, "tok42")).isEmpty();
        assertThat(prod.fetch(Store.GOOGLE_PLAY, "canceled-x")).isEmpty();
    }

    @Test
    void acknowledge_does_not_throw() {
        gateway.acknowledge(Store.GOOGLE_PLAY, "premium_yearly", "tok42");
    }
}
