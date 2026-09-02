package com.yeka.bandapp.notification.event;

import java.time.Instant;
import java.util.List;

/**
 * 다른 도메인이 알림을 요청할 때 발행하는 이벤트들(DTO 성격). {@code ReservationService}·
 * {@code SettlementService}는 {@code ApplicationEventPublisher}로 이 레코드를 던지기만 하고,
 * {@link NotificationEventListener}가 <b>트랜잭션 커밋 후</b> 받아 발송한다 — 발송(FCM HTTP)이
 * 서비스의 트랜잭션·비관적 락 안에서 일어나지 않도록(CLAUDE.md 규칙).
 *
 * <p>수신자 목록은 이벤트를 발행하는 쪽이 이미 갖고 있으므로(멤버 조회 등) 이벤트에 실어 보낸다.
 */
public final class NotificationEvents {

    private NotificationEvents() {
    }

    /** 새 일정이 바로 확정됨(LEADER_ONLY / ANYONE). 등록자를 뺀 밴드 멤버 전원에게. */
    public record ReservationCreated(long bandId, long reservationId, Instant startAt,
                                     List<Long> recipientUserIds) {
    }

    /** 승인 대기 일정이 생김(APPROVAL_REQUIRED, 또는 확정 일정의 시간·장소 변경으로 재승인). 밴드장에게. */
    public record ReservationApprovalRequested(long bandId, long reservationId, Instant startAt,
                                               List<Long> recipientUserIds) {
    }

    /** 밴드장이 승인/거절함. 일정 등록자에게. */
    public record ReservationDecided(long bandId, long reservationId, Instant startAt,
                                     long requesterUserId, boolean approved) {
    }

    /** 일정이 취소됨. 취소자를 뺀 밴드 멤버 전원에게. */
    public record ReservationCancelled(long bandId, long reservationId, Instant startAt,
                                       List<Long> recipientUserIds) {
    }

    /** 정산이 생성/재계산됨. 분담 대상자에게. */
    public record SettlementRequested(long bandId, long reservationId, int totalAmount,
                                      List<Long> recipientUserIds) {
    }
}
