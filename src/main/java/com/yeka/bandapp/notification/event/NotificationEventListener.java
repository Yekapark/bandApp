package com.yeka.bandapp.notification.event;

import com.yeka.bandapp.band.service.BandDirectoryService;
import com.yeka.bandapp.notification.entity.NotificationType;
import com.yeka.bandapp.notification.service.NotificationMessages;
import com.yeka.bandapp.notification.service.NotificationSender;
import com.yeka.bandapp.notification.service.PlanExpiryReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import com.yeka.bandapp.plan.service.WithdrawnPurchaserSubscriptions.PurchaserSubscriptionCanceled;
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
    private final PlanExpiryReminderService planExpiryReminderService;
    private final BandDirectoryService bandDirectory;

    public NotificationEventListener(NotificationSender sender,
                                     PlanExpiryReminderService planExpiryReminderService,
                                     BandDirectoryService bandDirectory) {
        this.sender = sender;
        this.planExpiryReminderService = planExpiryReminderService;
        this.bandDirectory = bandDirectory;
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
        // variant = 총액 — 같은 금액의 요청은 한 번만, 재계산으로 총액이 바뀌면 다시 알린다. 예전에는 0 으로 고정이라
        // 재계산 알림이 "이미 보냄" 으로 전부 걸러져 바뀐 금액을 아무도 몰랐다.
        safely(() -> sender.notify(NotificationType.SETTLEMENT_REQUESTED, e.reservationId(), e.totalAmount(),
                e.recipientUserIds(),
                NotificationMessages.settlementRequested(e.bandId(), e.reservationId(), e.totalAmount())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSettlementShareChanged(NotificationEvents.SettlementShareChanged e) {
        for (NotificationEvents.ShareChange c : e.changes()) {
            // variant = -1 - 새 몫 — 음수라 정산 요청(variant = 총액, 양수)·옛 기록(0)과 겹치지 않고, 몫이 또 바뀌면 다시 간다.
            // ponytail: 같은 금액으로 되돌아왔다 다시 바뀌는 경우(10,000→15,000→10,000→15,000)의 두 번째 15,000 은 걸러진다.
            safely(() -> sender.notify(NotificationType.SETTLEMENT_REQUESTED, e.reservationId(), -1 - c.after(),
                    List.of(c.userId()),
                    NotificationMessages.settlementShareChanged(e.bandId(), e.reservationId(), c.before(), c.after())));
        }
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("알림 발송 실패", e);
        }
    }

    /**
     * 결제자가 나가 자동 결제를 해지함 — 밴드장에게. 해지 확인 뒤(이미 커밋된 뒤 또는 재시도 배치)에 발행되므로 바로 받는다
     * ({@code AFTER_COMMIT} 이 아니다 — 끝난 트랜잭션의 afterCommit 안에서 발행되면 다시 등록한 동기화가 불리지 않는다).
     */
    @EventListener
    public void onPurchaserSubscriptionCanceled(PurchaserSubscriptionCanceled e) {
        safely(() -> sender.notify(NotificationType.PLAN_PURCHASER_LEFT, e.bandId(), (int) e.purchaserUserId(),
                bandDirectory.leaderUserIds(e.bandId()),
                NotificationMessages.planPurchaserLeft(e.bandId(), e.premiumUntil())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPlanExpired(NotificationEvents.PlanExpired e) {
        planExpiryReminderService.notifyExpired(e.bandId(), e.graceDays());
    }
}
