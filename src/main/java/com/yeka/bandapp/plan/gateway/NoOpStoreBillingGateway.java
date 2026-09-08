package com.yeka.bandapp.plan.gateway;

import com.yeka.bandapp.plan.config.PlanProperties;
import com.yeka.bandapp.plan.config.StoreBillingProperties;
import com.yeka.bandapp.plan.entity.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 스토어 붙이기 전(그리고 로컬·CI)용 게이트웨이. 실제 Play 조회 없이 토큰만 보고 상태를 흉내낸다.
 * {@code app.plan.billing.gateway} 가 없거나 {@code noop} 일 때 뜬다 — 실제 어댑터({@code google})가
 * 뜨면 이 빈은 빠진다.
 *
 * <p>토큰 접두사로 상태를 고른다(테스트 편의): {@code revoked-…}→REVOKED, {@code expired-…}→EXPIRED,
 * {@code canceled-…}→CANCELED, {@code hold-…}→ON_HOLD, {@code invalid-…}→조회 실패(empty),
 * 그 밖에는 ACTIVE. 만료 시각은 {@code now + premiumPeriodDays}(1년).
 */
@Component
@ConditionalOnProperty(prefix = "app.plan.billing", name = "gateway", havingValue = "noop",
        matchIfMissing = true)
public class NoOpStoreBillingGateway implements StoreBillingGateway {

    private static final Logger log = LoggerFactory.getLogger(NoOpStoreBillingGateway.class);

    private final PlanProperties planProperties;

    public NoOpStoreBillingGateway(PlanProperties planProperties, StoreBillingProperties billingProperties) {
        this.planProperties = planProperties;
        log.info("[no-op billing] 스토어 검증 없이 동작한다 (app.plan.billing.gateway={})", billingProperties.gateway());
    }

    @Override
    public Optional<StoreSubscription> fetch(Store store, String purchaseToken) {
        if (purchaseToken == null || purchaseToken.isBlank()) {
            return Optional.empty();
        }
        String prefix = purchaseToken.contains("-") ? purchaseToken.substring(0, purchaseToken.indexOf('-')) : "";
        StoreSubscriptionState state = switch (prefix) {
            case "invalid" -> null;
            case "revoked" -> StoreSubscriptionState.REVOKED;
            case "expired" -> StoreSubscriptionState.EXPIRED;
            case "canceled" -> StoreSubscriptionState.CANCELED;
            case "hold" -> StoreSubscriptionState.ON_HOLD;
            default -> StoreSubscriptionState.ACTIVE;
        };
        if (state == null) {
            return Optional.empty();
        }
        Instant expiry = Instant.now().plus(planProperties.premiumPeriodDays(), ChronoUnit.DAYS);
        return Optional.of(new StoreSubscription(
                store, purchaseToken, "premium_yearly", "noop-order-" + purchaseToken, state, expiry, true));
    }

    @Override
    public void acknowledge(Store store, String productId, String purchaseToken) {
        log.info("[no-op billing] acknowledge store={} productId={} token={}", store, productId, purchaseToken);
    }
}
