package com.yeka.bandapp.plan.gateway;

import com.yeka.bandapp.plan.entity.Store;

import java.time.Instant;
import java.util.Optional;

/**
 * 스토어 인앱결제 구독의 <b>조회</b> 창구. 구매는 클라이언트에서 일어나고(Play Billing / StoreKit),
 * 서버는 클라이언트가 넘긴 구매 토큰으로 스토어에 "이 구독 지금 상태가 뭐냐"를 물어보고,
 * RTDN / App Store Server Notifications 웹훅이 올 때 다시 물어봐서 DB 를 맞춘다.
 *
 * <p>예전 {@code PaymentGateway}(서버가 subscribe/renew/cancel 을 능동 호출)와 달리 이 인터페이스는
 * 결제를 <b>시작하지 않는다</b> — 스토어 모델에서는 그럴 수 없다. 요금제 도메인 로직은 이 인터페이스에만
 * 의존하고, 실제 구현은 {@code GooglePlayBillingGateway}(Phase 12 슬라이스 2), 로컬·테스트는
 * {@link NoOpStoreBillingGateway}.
 */
public interface StoreBillingGateway {

    /**
     * 구매 토큰으로 스토어에 구독의 현재 상태를 물어본다. 토큰이 스토어에 없거나(위조·오타) 조회에
     * 실패하면 {@code empty} — 호출자는 이를 "검증 실패"로 처리한다.
     */
    Optional<StoreSubscription> fetch(Store store, String purchaseToken);

    /**
     * 구매를 확인 처리(acknowledge)한다. Google Play 는 3일 안에 acknowledge 하지 않으면 자동 환불한다.
     * 이미 확인된 구매에 다시 불러도 안전해야 한다(멱등). {@code productId} 는 Google v1 acknowledge
     * 엔드포인트가 요구하는 구독 상품 id — {@link StoreSubscription#productId()} 를 그대로 넘긴다.
     */
    void acknowledge(Store store, String productId, String purchaseToken);

    /**
     * 스토어가 말하는 구독의 현재 모습.
     *
     * @param productId     구독 상품 id(Play Console 의 상품 id, 예: {@code premium_yearly}). acknowledge 에 필요.
     * @param orderId       스토어 주문 식별자(Google 은 {@code GPA.xxxx-xxxx-xxxx-xxxxx}). 표시·추적용.
     * @param expiryTime    현재 결제 기간의 종료 시각. 이 값이 곧 {@code band_plans.expires_at}.
     * @param acknowledged  이미 확인 처리됐는지. false 면 호출자가 {@link #acknowledge} 를 불러야 한다.
     */
    record StoreSubscription(Store store, String purchaseToken, String productId, String orderId,
                             StoreSubscriptionState state, Instant expiryTime, boolean acknowledged) {
    }

    /**
     * 구독 상태. Google Play {@code SubscriptionPurchaseV2.subscriptionState} 를 이 앱이 쓰는 만큼으로 추린 것.
     *
     * <ul>
     *   <li>{@code ACTIVE} — 정상. 자동 갱신 켜짐.
     *   <li>{@code CANCELED} — 자동 갱신 껐지만 결제한 기간은 아직 남음(기간 끝까지 PREMIUM).
     *   <li>{@code IN_GRACE} — 결제 실패 후 유예. Google 은 이 동안 접근을 유지한다.
     *   <li>{@code ON_HOLD} / {@code PAUSED} — 접근 중단.
     *   <li>{@code EXPIRED} — 구독 종료.
     *   <li>{@code REVOKED} — 환불·강제 취소. 즉시 접근 박탈.
     * </ul>
     */
    enum StoreSubscriptionState {
        ACTIVE, CANCELED, IN_GRACE, ON_HOLD, PAUSED, EXPIRED, REVOKED;

        /** 이 상태면 PREMIUM 을 유지·부여한다. */
        public boolean grantsPremium() {
            return this == ACTIVE || this == CANCELED || this == IN_GRACE;
        }
    }
}
