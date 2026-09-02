package com.yeka.bandapp.notification.event;

import com.yeka.bandapp.notification.entity.NotificationType;
import com.yeka.bandapp.notification.service.NotificationMessages;
import com.yeka.bandapp.notification.service.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 도메인 이벤트를 받아 실제 푸시를 보내는 지점. 이 코드베이스에서 Spring 도메인 이벤트를 쓰는 첫 사례다.
 *
 * <ul>
 *   <li><b>{@code AFTER_COMMIT}</b> — 발송을 트랜잭션 커밋 뒤로 미뤄, 서비스가 잡은 DB 커넥션·비관적 락이
 *       FCM 왕복 동안 붙잡히지 않게 한다({@code UserAccountService.unlinkKakaoAfterCommit}와 같은 계약).</li>
 *   <li><b>{@code fallbackExecution = true}</b> — 트랜잭션 없이 발행돼도(테스트 등) 즉시 실행한다.</li>
 *   <li>모든 핸들러를 {@link #safely}로 감싼다 — 발송 실패가 이미 커밋된 본 작업(일정 등록·정산)을
 *       되돌리지 않는다.</li>
 * </ul>
 *
 * <p>동기 실행이라 FCM 왕복만큼 응답이 늦어질 수 있다. FCM 타임아웃(5초)으로 상한을 두고, 부하가
 * 문제되면 {@code @Async} 도입을 후속 과제로 남긴다(지금 도입하면 트랜잭션·예외 경로가 한 번에 늘어난다).
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationSender sender;

    public NotificationEventListener(NotificationSender sender) {
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReservationCreated(NotificationEvents.ReservationCreated e) {
        safely(() -> sender.notify(NotificationType.RESERVATION_CREATED, e.reservationId(), 0,
                e.recipientUserIds(),
                NotificationMessages.reservationCreated(e.bandId(), e.reservationId(), e.startAt())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onApprovalRequested(NotificationEvents.ReservationApprovalRequested e) {
        safely(() -> sender.notify(NotificationType.RESERVATION_APPROVAL_REQUESTED, e.reservationId(), 0,
                e.recipientUserIds(),
                NotificationMessages.approvalRequested(e.bandId(), e.reservationId(), e.startAt())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReservationDecided(NotificationEvents.ReservationDecided e) {
        NotificationType type = e.approved()
                ? NotificationType.RESERVATION_APPROVED
                : NotificationType.RESERVATION_REJECTED;
        safely(() -> sender.notify(type, e.reservationId(), 0, List.of(e.requesterUserId()),
                NotificationMessages.decision(e.bandId(), e.reservationId(), e.startAt(), e.approved())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReservationCancelled(NotificationEvents.ReservationCancelled e) {
        safely(() -> sender.notify(NotificationType.RESERVATION_CANCELLED, e.reservationId(), 0,
                e.recipientUserIds(),
                NotificationMessages.cancelled(e.bandId(), e.reservationId(), e.startAt())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSettlementRequested(NotificationEvents.SettlementRequested e) {
        safely(() -> sender.notify(NotificationType.SETTLEMENT_REQUESTED, e.reservationId(), 0,
                e.recipientUserIds(),
                NotificationMessages.settlementRequested(e.bandId(), e.reservationId(), e.totalAmount())));
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("알림 발송 실패", e);
        }
    }
}
