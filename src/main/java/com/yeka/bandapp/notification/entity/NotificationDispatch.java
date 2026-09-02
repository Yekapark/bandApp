package com.yeka.bandapp.notification.entity;

import com.yeka.bandapp.common.entity.BaseTimeEntity;
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

/**
 * 한 건의 발송 이력이자 <b>멱등 키</b>. {@code (userId, type, targetId, variant)} 유니크 제약이,
 * 배치 재실행·서버 재시작에도 같은 알림을 두 번 보내지 않도록 막는다.
 *
 * <p>{@code variant}는 같은 {@code (user, type, target)} 안에서 발송을 구분한다 — 리마인더는 offset(분),
 * 그 외 트리거는 0. 불변 기록이라 상태 전이 메서드가 없다.
 */
@Entity
@Table(name = "notification_dispatches")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDispatch extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false)
    private int variant;

    private NotificationDispatch(long userId, NotificationType type, long targetId, int variant) {
        this.userId = userId;
        this.type = type;
        this.targetId = targetId;
        this.variant = variant;
    }

    public static NotificationDispatch of(long userId, NotificationType type, long targetId, int variant) {
        return new NotificationDispatch(userId, type, targetId, variant);
    }
}
