package com.yeka.bandapp.plan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 처리 완료한 스토어 웹훅 메시지. Pub/Sub 는 at-least-once + 순서 무보장이라 같은 {@code messageId} 가
 * 여러 번 올 수 있다 — {@code messageId} 를 PK 로 두고, insert 가 충돌하면 이미 반영한 것으로 보고 무시한다
 * (쿠폰 중복 사용 방어와 같은 "insert 후 유니크 위반을 잡는" 패턴).
 */
@Entity
@Table(name = "processed_store_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedStoreEvent {

    @Id
    @Column(name = "message_id", length = 255)
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Store store;

    @Column(name = "notification_type")
    private Integer notificationType;

    @Column(name = "purchase_token")
    private String purchaseToken;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    private ProcessedStoreEvent(String messageId, Store store, Integer notificationType,
                               String purchaseToken, Instant receivedAt) {
        this.messageId = messageId;
        this.store = store;
        this.notificationType = notificationType;
        this.purchaseToken = purchaseToken;
        this.receivedAt = receivedAt;
    }

    public static ProcessedStoreEvent of(String messageId, Store store, Integer notificationType,
                                         String purchaseToken, Instant receivedAt) {
        return new ProcessedStoreEvent(messageId, store, notificationType, purchaseToken, receivedAt);
    }
}
