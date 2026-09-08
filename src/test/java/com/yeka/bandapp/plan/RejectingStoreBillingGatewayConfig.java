package com.yeka.bandapp.plan;

import com.yeka.bandapp.plan.entity.Store;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Optional;

/**
 * {@code @Import} 하면 스토어 조회가 항상 실패(구매 없음)를 돌려주는 게이트웨이가
 * {@code NoOpStoreBillingGateway} 를 대체한다({@code @Primary}). 요금제 도메인이 인터페이스에만
 * 의존하고, 검증 실패 시 402 로 끝나며 상태가 그대로인지 확인하는 데 쓴다.
 */
@TestConfiguration
public class RejectingStoreBillingGatewayConfig {

    @Bean
    @Primary
    StoreBillingGateway rejectingStoreBillingGateway() {
        return new StoreBillingGateway() {
            @Override
            public Optional<StoreSubscription> fetch(Store store, String purchaseToken) {
                return Optional.empty();
            }

            @Override
            public void acknowledge(Store store, String productId, String purchaseToken) {
                // no-op
            }
        };
    }
}
