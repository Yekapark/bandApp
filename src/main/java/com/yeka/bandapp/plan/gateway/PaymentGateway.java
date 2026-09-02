package com.yeka.bandapp.plan.gateway;

import java.time.Instant;

/**
 * 구독 결제 게이트웨이. 구독 시작/갱신/해지를 추상화한다 — 요금제 도메인 로직은 이 인터페이스에만
 * 의존하고, 실제 PG(토스·포트원 등) 연동은 어댑터 구현체로 나중에 추가한다.
 *
 * <p>이번 릴리스의 유일한 구현체는 {@link NoOpPaymentGateway} — 항상 즉시 성공한다. 실제 어댑터가
 * 생기면 {@code @ConditionalOnProperty}/{@code @Primary} 로 선택하고 no-op 은 폴백으로 남긴다.
 *
 * <p>커맨드에는 카드·토큰 같은 결제수단 정보가 없다 — 그건 어댑터가 자체 설정이나 별도 파라미터로 받는다.
 */
public interface PaymentGateway {

    SubscriptionResult subscribe(SubscribeCommand command);

    SubscriptionResult renew(RenewCommand command);

    CancellationResult cancel(CancelCommand command);

    record SubscribeCommand(long bandId, long requestedByUserId, String planCode, Instant requestedAt) {
    }

    record RenewCommand(long bandId, String subscriptionRef, Instant requestedAt) {
    }

    record CancelCommand(long bandId, String subscriptionRef, Instant requestedAt) {
    }

    /**
     * 구독 시작/갱신 결과.
     *
     * @param subscriptionRef 게이트웨이의 불투명 구독 식별자(성공 시 non-null)
     * @param currentPeriodEnd 현재 구독기간 종료 시각(성공 시 non-null)
     */
    record SubscriptionResult(boolean success, String subscriptionRef, Instant currentPeriodEnd,
                              String failureReason) {

        public static SubscriptionResult ok(String subscriptionRef, Instant currentPeriodEnd) {
            return new SubscriptionResult(true, subscriptionRef, currentPeriodEnd, null);
        }

        public static SubscriptionResult failed(String failureReason) {
            return new SubscriptionResult(false, null, null, failureReason);
        }
    }

    /** 구독 해지 결과. */
    record CancellationResult(boolean success, Instant effectiveAt, String failureReason) {

        public static CancellationResult ok(Instant effectiveAt) {
            return new CancellationResult(true, effectiveAt, null);
        }

        public static CancellationResult failed(String failureReason) {
            return new CancellationResult(false, null, failureReason);
        }
    }
}
