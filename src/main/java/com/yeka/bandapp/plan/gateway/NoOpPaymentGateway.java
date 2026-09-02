package com.yeka.bandapp.plan.gateway;

import com.yeka.bandapp.plan.config.PlanProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;

/**
 * 결제 없이 항상 성공하는 게이트웨이. 요금 정책과 PG 선택이 확정되기 전까지 요금제 도메인 로직을
 * 그대로 돌리기 위한 구현체다(BUILD_PLAN Phase 10 — "요금제 구조와 인터페이스, no-op 구현체까지만").
 *
 * <p>구독 식별자는 {@code "noop-{bandId}"}, 구독기간 종료는 {@code requestedAt + premiumPeriodDays}.
 */
@Component
public class NoOpPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(NoOpPaymentGateway.class);

    private final PlanProperties planProperties;

    public NoOpPaymentGateway(PlanProperties planProperties) {
        this.planProperties = planProperties;
    }

    @Override
    public SubscriptionResult subscribe(SubscribeCommand command) {
        log.info("[no-op payment] subscribe bandId={} planCode={}", command.bandId(), command.planCode());
        return SubscriptionResult.ok(refFor(command.bandId()),
                command.requestedAt().plus(planProperties.premiumPeriodDays(), ChronoUnit.DAYS));
    }

    @Override
    public SubscriptionResult renew(RenewCommand command) {
        log.info("[no-op payment] renew bandId={} subscriptionRef={}", command.bandId(), command.subscriptionRef());
        return SubscriptionResult.ok(refFor(command.bandId()),
                command.requestedAt().plus(planProperties.premiumPeriodDays(), ChronoUnit.DAYS));
    }

    @Override
    public CancellationResult cancel(CancelCommand command) {
        log.info("[no-op payment] cancel bandId={} subscriptionRef={}", command.bandId(), command.subscriptionRef());
        return CancellationResult.ok(command.requestedAt());
    }

    private static String refFor(long bandId) {
        return "noop-" + bandId;
    }
}
