package com.yeka.bandapp.notification.event;

import com.yeka.bandapp.notification.entity.NotificationType;
import com.yeka.bandapp.notification.repository.NotificationDispatchRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 일정 시작 시각이 바뀌면 그 일정의 리마인더 발송 기록을 지운다 — 바뀐 시각 기준으로 리마인더가 다시 나가게.
 *
 * <p>발송 기록(notification_dispatches)은 (사람, 종류, 일정, 시점) 으로 "이미 보냄" 을 판단한다. 시각이 바뀌어도 기록이 남아
 * 있으면 "6시간 전 알림은 이 일정에 이미 보냈다" 로 보고 새 시각의 알림을 건너뛰었다(테스터 의견 2026-10-01).
 * 옛 시각을 가리키는 리마인더는 알림 목록에서도 사라진다 — 틀린 시각이 남아 있는 것보다 낫다.
 *
 * <p>{@code @EventListener}(동기) — 일정 수정 트랜잭션 안에서 같이 지운다. 외부 호출이 없어 커밋 뒤로 미룰 이유가 없고,
 * 수정이 롤백되면 기록 삭제도 함께 롤백된다.
 */
@Component
public class ReminderResetListener {

    private final NotificationDispatchRepository dispatches;

    public ReminderResetListener(NotificationDispatchRepository dispatches) {
        this.dispatches = dispatches;
    }

    @EventListener
    public void onRescheduled(NotificationEvents.ReservationRescheduled e) {
        dispatches.deleteByTypeAndTargetId(NotificationType.RESERVATION_REMINDER, e.reservationId());
    }
}
