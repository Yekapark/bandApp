package com.yeka.bandapp.plan.gateway;

import com.yeka.bandapp.plan.config.PlanProperties;
import com.yeka.bandapp.plan.config.StoreBillingProperties;
import com.yeka.bandapp.plan.entity.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
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
 *
 * <p><b>운영({@code prod} 프로파일)에서는 아무것도 통과시키지 않는다.</b> 이 빈이 운영에 떴다는 건
 * {@code PLAN_BILLING_GATEWAY=google} 이 컨테이너에 안 실렸다는 뜻이고, 그대로 두면 밴드장이
 * 아무 문자열이나 구매 토큰으로 보내 PREMIUM 1년을 공짜로 가져간다. 그래서 {@link #fetch} 가
 * 빈 값을 돌려주고({@code PURCHASE_NOT_VERIFIED}), 기동 로그에 에러를 남긴다. 빈을 아예
 * 없애지 않는 이유는 앱이 기동 자체를 못 하면 결제와 무관한 기능까지 멈추기 때문이다 —
 * 쿠폰으로 주는 PREMIUM 은 이 게이트웨이를 타지 않으므로 그대로 동작한다.
 */
@Component
@ConditionalOnProperty(prefix = "app.plan.billing", name = "gateway", havingValue = "noop",
        matchIfMissing = true)
public class NoOpStoreBillingGateway implements StoreBillingGateway {

    private static final Logger log = LoggerFactory.getLogger(NoOpStoreBillingGateway.class);

    private final PlanProperties planProperties;
    /** 운영에 이 빈이 떴다 = 설정 사고. 검증을 흉내내지 않고 전부 거부한다. */
    private final boolean refuseEverything;

    public NoOpStoreBillingGateway(PlanProperties planProperties, StoreBillingProperties billingProperties,
                                   Environment environment) {
        this.planProperties = planProperties;
        this.refuseEverything = environment.matchesProfiles("prod");
        if (refuseEverything) {
            log.error("운영인데 결제 게이트웨이가 noop 이다 — 구매 검증을 전부 거부한다. "
                    + "PLAN_BILLING_GATEWAY=google 과 서비스 계정 키를 넣고 재기동할 것 "
                    + "(docker-compose.prod.yml 에 PLAN_BILLING_* 가 실려 있는지도 확인)");
        } else {
            log.info("[no-op billing] 스토어 검증 없이 동작한다 (app.plan.billing.gateway={})",
                    billingProperties.gateway());
        }
    }

    @Override
    public Optional<StoreSubscription> fetch(Store store, String purchaseToken) {
        if (refuseEverything || purchaseToken == null || purchaseToken.isBlank()) {
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
