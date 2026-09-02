package com.yeka.bandapp.plan;

import com.yeka.bandapp.plan.config.PlanProperties;
import com.yeka.bandapp.plan.gateway.NoOpPaymentGateway;
import com.yeka.bandapp.plan.gateway.PaymentGateway.CancelCommand;
import com.yeka.bandapp.plan.gateway.PaymentGateway.CancellationResult;
import com.yeka.bandapp.plan.gateway.PaymentGateway.SubscribeCommand;
import com.yeka.bandapp.plan.gateway.PaymentGateway.SubscriptionResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NoOpPaymentGateway} 단위 테스트 — Docker 불필요.
 * 항상 성공하고, 구독 식별자는 {@code noop-{bandId}}, 구독기간 종료는 요청 시각 + premiumPeriodDays.
 */
class NoOpPaymentGatewayTest {

    private final NoOpPaymentGateway gateway =
            new NoOpPaymentGateway(new PlanProperties(30, 30, "PREMIUM_MONTHLY"));

    private static final Instant NOW = Instant.parse("2026-03-01T00:00:00Z");

    @Test
    void subscribe_succeeds_with_ref_and_period_end() {
        SubscriptionResult result = gateway.subscribe(new SubscribeCommand(7L, 1L, "PREMIUM_MONTHLY", NOW));

        assertThat(result.success()).isTrue();
        assertThat(result.subscriptionRef()).isEqualTo("noop-7");
        assertThat(result.currentPeriodEnd()).isEqualTo(NOW.plus(30, ChronoUnit.DAYS));
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void cancel_succeeds_effective_immediately() {
        CancellationResult result = gateway.cancel(new CancelCommand(7L, "noop-7", NOW));

        assertThat(result.success()).isTrue();
        assertThat(result.effectiveAt()).isEqualTo(NOW);
    }
}
