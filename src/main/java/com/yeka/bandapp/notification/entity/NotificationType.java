package com.yeka.bandapp.notification.entity;

/**
 * 알림 트리거 종류. {@code notification_dispatches.type}에 문자열로 저장되며 멱등 키의 일부다
 * (같은 {@code (user, type, target, variant)} 조합은 한 번만 발송된다).
 */
public enum NotificationType {
    RESERVATION_CREATED,
    RESERVATION_APPROVAL_REQUESTED,
    RESERVATION_APPROVED,
    RESERVATION_REJECTED,
    RESERVATION_CANCELLED,
    SETTLEMENT_REQUESTED,
    RESERVATION_REMINDER,
    ATTENDANCE_NUDGE
}
