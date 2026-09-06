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
    ATTENDANCE_NUDGE,

    /** PREMIUM 만료 예고. {@code variant} 에 남은 일수(30·7·1)를 넣어 시점마다 한 번씩만 보낸다. */
    PLAN_EXPIRING_SOON,

    /** PREMIUM 이 끝나 FREE 로 내려간 직후. 유예 기간이 지나면 첨부가 사라진다는 안내다. */
    PLAN_EXPIRED,

    /**
     * 신고가 접수됐다. <b>운영자에게만</b> 간다({@code app.report.notify-user-ids}).
     *
     * <p>이게 없으면 신고는 표에 한 줄 쌓일 뿐 아무도 모른다. 약관에 "신고를 검토한다" 고
     * 쓰려면 최소한 접수 사실이 사람에게 닿아야 한다.
     */
    REPORT_RECEIVED
}
