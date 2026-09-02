package com.yeka.bandapp.plan;

import com.yeka.bandapp.plan.gateway.PaymentGateway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * {@code @Import} 하면 결제가 항상 실패하는 게이트웨이가 {@code NoOpPaymentGateway} 를 대체한다({@code @Primary}).
 * 요금제 도메인 경로가 인터페이스에만 의존하고 실패 분기가 동작하는지 검증하는 데 쓴다.
 */
@TestConfiguration
public class FailingPaymentGatewayConfig {

    @Bean
    @Primary
    PaymentGateway failingPaymentGateway() {
        return new PaymentGateway() {
            @Override
            public SubscriptionResult subscribe(SubscribeCommand command) {
                return SubscriptionResult.failed("test: 결제 거절");
            }

            @Override
            public SubscriptionResult renew(RenewCommand command) {
                return SubscriptionResult.failed("test: 결제 거절");
            }

            @Override
            public CancellationResult cancel(CancelCommand command) {
                return CancellationResult.failed("test: 해지 실패");
            }
        };
    }
}
