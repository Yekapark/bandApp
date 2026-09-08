package com.yeka.bandapp.plan;

import com.google.api.services.androidpublisher.model.SubscriptionPurchaseLineItem;
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway.StoreSubscription;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway.StoreSubscriptionState;
import com.yeka.bandapp.plan.gateway.google.GooglePlaySubscriptionMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Play 응답 → {@link StoreSubscription} 매핑 단위 테스트 — HTTP·Docker 불필요.
 */
class GooglePlaySubscriptionMapperTest {

    @Test
    void active_purchase_maps_all_fields() {
        SubscriptionPurchaseV2 p = new SubscriptionPurchaseV2()
                .setSubscriptionState("SUBSCRIPTION_STATE_ACTIVE")
                .setLatestOrderId("GPA.1234-5678-9012-34567")
                .setAcknowledgementState("ACKNOWLEDGEMENT_STATE_PENDING")
                .setLineItems(List.of(new SubscriptionPurchaseLineItem()
                        .setProductId("premium_yearly")
                        .setExpiryTime("2027-01-15T09:30:00Z")));

        StoreSubscription s = GooglePlaySubscriptionMapper.toStoreSubscription("tok-abc", p);

        assertThat(s.purchaseToken()).isEqualTo("tok-abc");
        assertThat(s.productId()).isEqualTo("premium_yearly");
        assertThat(s.orderId()).isEqualTo("GPA.1234-5678-9012-34567");
        assertThat(s.state()).isEqualTo(StoreSubscriptionState.ACTIVE);
        assertThat(s.state().grantsPremium()).isTrue();
        assertThat(s.acknowledged()).isFalse();
        assertThat(s.expiryTime()).isEqualTo(Instant.parse("2027-01-15T09:30:00Z"));
    }

    @Test
    void acknowledged_state_and_offset_expiry_takes_the_latest_line_item() {
        SubscriptionPurchaseV2 p = new SubscriptionPurchaseV2()
                .setSubscriptionState("SUBSCRIPTION_STATE_CANCELED")
                .setAcknowledgementState("ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED")
                .setLineItems(List.of(
                        new SubscriptionPurchaseLineItem().setProductId("premium_yearly")
                                .setExpiryTime("2026-06-01T00:00:00+09:00"),
                        new SubscriptionPurchaseLineItem().setProductId("premium_yearly")
                                .setExpiryTime("2027-06-01T00:00:00+09:00")));

        StoreSubscription s = GooglePlaySubscriptionMapper.toStoreSubscription("t", p);

        assertThat(s.acknowledged()).isTrue();
        assertThat(s.state()).isEqualTo(StoreSubscriptionState.CANCELED);
        assertThat(s.state().grantsPremium()).isTrue(); // 해지 예약이어도 기간은 남아 있음
        assertThat(s.expiryTime()).isEqualTo(Instant.parse("2027-05-31T15:00:00Z"));
    }

    @Test
    void state_string_mapping() {
        assertThat(GooglePlaySubscriptionMapper.mapState("SUBSCRIPTION_STATE_ACTIVE"))
                .isEqualTo(StoreSubscriptionState.ACTIVE);
        assertThat(GooglePlaySubscriptionMapper.mapState("SUBSCRIPTION_STATE_IN_GRACE_PERIOD"))
                .isEqualTo(StoreSubscriptionState.IN_GRACE);
        assertThat(GooglePlaySubscriptionMapper.mapState("SUBSCRIPTION_STATE_ON_HOLD"))
                .isEqualTo(StoreSubscriptionState.ON_HOLD);
        assertThat(GooglePlaySubscriptionMapper.mapState("SUBSCRIPTION_STATE_PAUSED"))
                .isEqualTo(StoreSubscriptionState.PAUSED);
        assertThat(GooglePlaySubscriptionMapper.mapState("SUBSCRIPTION_STATE_EXPIRED"))
                .isEqualTo(StoreSubscriptionState.EXPIRED);
        // 모르는/PENDING 상태는 PREMIUM 대상 아님
        assertThat(GooglePlaySubscriptionMapper.mapState("SUBSCRIPTION_STATE_PENDING").grantsPremium()).isFalse();
        assertThat(GooglePlaySubscriptionMapper.mapState(null)).isEqualTo(StoreSubscriptionState.EXPIRED);
        assertThat(GooglePlaySubscriptionMapper.mapState("SOMETHING_NEW").grantsPremium()).isFalse();
    }
}
