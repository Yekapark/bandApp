package com.yeka.bandapp.plan.gateway.google;

import com.google.api.services.androidpublisher.model.SubscriptionPurchaseLineItem;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;
import com.yeka.bandapp.plan.entity.Store;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway.StoreSubscription;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway.StoreSubscriptionState;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@link SubscriptionPurchaseV2}(Play Developer API 응답) → {@link StoreSubscription} 변환.
 * HTTP 없이 순수 매핑이라 따로 떼어 단위 테스트한다.
 */
public final class GooglePlaySubscriptionMapper {

    private GooglePlaySubscriptionMapper() {
    }

    public static StoreSubscription toStoreSubscription(String purchaseToken, SubscriptionPurchaseV2 purchase) {
        List<SubscriptionPurchaseLineItem> lineItems = purchase.getLineItems() == null
                ? List.of() : purchase.getLineItems();

        String productId = lineItems.stream()
                .map(SubscriptionPurchaseLineItem::getProductId)
                .filter(p -> p != null && !p.isBlank())
                .findFirst()
                .orElse(null);

        // 여러 라인 아이템이면 가장 늦은 만료 시각을 취한다(하나뿐인 게 보통).
        Instant expiry = lineItems.stream()
                .map(SubscriptionPurchaseLineItem::getExpiryTime)
                .filter(t -> t != null && !t.isBlank())
                .map(GooglePlaySubscriptionMapper::parseRfc3339)
                .max(Instant::compareTo)
                .orElse(null);

        boolean acknowledged = "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
                .equals(purchase.getAcknowledgementState());

        return new StoreSubscription(
                Store.GOOGLE_PLAY,
                purchaseToken,
                productId,
                purchase.getLatestOrderId(),
                mapState(purchase.getSubscriptionState()),
                expiry,
                acknowledged);
    }

    /** Play 문자열 상태 → 우리 enum. 모르는 값은 EXPIRED 로 취급한다(PREMIUM 을 주지 않는 쪽이 안전). */
    public static StoreSubscriptionState mapState(String subscriptionState) {
        if (subscriptionState == null) {
            return StoreSubscriptionState.EXPIRED;
        }
        return switch (subscriptionState) {
            case "SUBSCRIPTION_STATE_ACTIVE" -> StoreSubscriptionState.ACTIVE;
            case "SUBSCRIPTION_STATE_CANCELED" -> StoreSubscriptionState.CANCELED;
            case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" -> StoreSubscriptionState.IN_GRACE;
            case "SUBSCRIPTION_STATE_ON_HOLD" -> StoreSubscriptionState.ON_HOLD;
            case "SUBSCRIPTION_STATE_PAUSED" -> StoreSubscriptionState.PAUSED;
            case "SUBSCRIPTION_STATE_EXPIRED" -> StoreSubscriptionState.EXPIRED;
            // PENDING(첫 결제 미완료)·그 밖의 값은 PREMIUM 대상이 아니다.
            default -> StoreSubscriptionState.ON_HOLD;
        };
    }

    private static Instant parseRfc3339(String value) {
        // Play 는 보통 Zulu("…Z")로 주지만 오프셋이 붙어 와도 견디게 OffsetDateTime 으로 받는다.
        return OffsetDateTime.parse(value).toInstant();
    }
}
