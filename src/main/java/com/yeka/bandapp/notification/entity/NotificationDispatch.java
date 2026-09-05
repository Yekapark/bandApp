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
 *
 * <p>{@code bandId}/{@code title}/{@code body}는 앱의 <b>알림 목록</b>을 위해 나중에 더한 것이라
 * {@code null}일 수 있다(V11 이전에 쌓인 행). 목록 조회는 문구가 있는 행만 대상으로 한다.
 * 읽음 여부는 여기 두지 않는다 — 클라이언트가 기기에 "마지막 확인 시각"을 저장한다.
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

    /** 알림이 속한 밴드. 목록을 밴드 단위로 거르는 데 쓴다. 옛 행은 {@code null}. */
    @Column(name = "band_id")
    private Long bandId;

    /** 보낸 그대로의 문구. 옛 행은 {@code null}. */
    @Column(length = 100)
    private String title;

    @Column(length = 500)
    private String body;

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
