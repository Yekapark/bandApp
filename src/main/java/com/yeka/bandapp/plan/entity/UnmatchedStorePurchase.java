package com.yeka.bandapp.plan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 돈은 들어왔는데 PREMIUM 을 줄 밴드를 못 찾은 스토어 구독(QA BILL-31, V24). 해결({@link #resolve})될 때까지 매일 점검이
 * 실패한다. 토큰 하나에 한 행 — 같은 토큰의 알림이 또 와도 새 행을 만들지 않는다(유니크 위반을 삼킨다).
 */
@Entity
@Table(name = "unmatched_store_purchases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnmatchedStorePurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Store store;

    @Column(name = "purchase_token", nullable = false)
    private String purchaseToken;

    @Column(name = "notification_type")
    private Integer notificationType;

    @Column(name = "product_id", length = 100)
    private String productId;

    @Column(name = "order_id", length = 100)
    private String orderId;

    @Column(name = "linked_purchase_token")
    private String linkedPurchaseToken;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "target_band_id")
    private Long targetBandId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(length = 200)
    private String resolution;

    private UnmatchedStorePurchase(Store store, String purchaseToken, Integer notificationType, String productId,
                                   String orderId, String linkedPurchaseToken, Instant receivedAt) {
        this.store = store;
        this.purchaseToken = purchaseToken;
        this.notificationType = notificationType;
        this.productId = productId;
        this.orderId = orderId;
        this.linkedPurchaseToken = linkedPurchaseToken;
        this.receivedAt = receivedAt;
    }

    public static UnmatchedStorePurchase of(Store store, String purchaseToken, Integer notificationType, String productId,
                                            String orderId, String linkedPurchaseToken, Instant receivedAt) {
        return new UnmatchedStorePurchase(store, purchaseToken, notificationType, productId, orderId,
                linkedPurchaseToken, receivedAt);
    }

    public boolean isOpen() {
        return resolvedAt == null;
    }

    /** 해결 표시. 이미 해결됐으면 처음 기록을 지킨다. */
    public void resolve(Instant at, String how) {
        if (resolvedAt != null) {
            return;
        }
        this.resolvedAt = at;
        this.resolution = how.length() > 200 ? how.substring(0, 200) : how;
    }
}
