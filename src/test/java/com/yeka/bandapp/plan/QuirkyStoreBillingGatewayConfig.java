package com.yeka.bandapp.plan;

import com.yeka.bandapp.plan.entity.Store;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 스토어가 <b>정상 상태(ACTIVE)로 응답하지만 내용이 이상한</b> 구매를 돌려주는 게이트웨이.
 * {@code RejectingStoreBillingGatewayConfig}(조회 자체가 실패)와 달리, 상태만 보고 통과시키면
 * 그대로 PREMIUM 이 붙는 경우들을 만든다.
 *
 * <p>구매 토큰 접두사로 고른다:
 * <ul>
 *   <li>{@code wrongproduct-…} — 우리가 파는 상품이 아닌 다른 구독 상품
 *   <li>{@code noexpiry-…} — 만료일이 없는 구독
 *   <li>그 밖 — 정상(대조군)
 * </ul>
 */
@TestConfiguration
public class QuirkyStoreBillingGatewayConfig {

    static final String OUR_PRODUCT = "premium_yearly";

    @Bean
    @Primary
    StoreBillingGateway quirkyStoreBillingGateway() {
        return new StoreBillingGateway() {
            @Override
            public Optional<StoreSubscription> fetch(Store store, String purchaseToken) {
                String productId = purchaseToken.startsWith("wrongproduct-")
                        ? "premium_monthly_cheap"
                        : OUR_PRODUCT;
                Instant expiry = purchaseToken.startsWith("noexpiry-")
                        ? null
                        : Instant.now().plus(365, ChronoUnit.DAYS);
                return Optional.of(new StoreSubscription(
                        Store.GOOGLE_PLAY, purchaseToken, productId, "GPA.test-order",
                        StoreSubscriptionState.ACTIVE, expiry, true));
            }

            @Override
            public void acknowledge(Store store, String productId, String purchaseToken) {
                // no-op
            }
        };
    }
}
