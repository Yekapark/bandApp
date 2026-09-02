package com.yeka.bandapp.notification;

import com.yeka.bandapp.notification.entity.NotificationType;
import com.yeka.bandapp.notification.push.PushMessage;
import com.yeka.bandapp.notification.service.NotificationMessages;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 트리거별 푸시 문구·data 페이로드 조립(순수 함수). Docker 불필요.
 */
class NotificationMessagesTest {

    private static final Instant START = Instant.parse("2026-09-10T13:00:00Z");

    @Test
    void every_message_carries_type_band_and_reservation_in_data() {
        PushMessage created = NotificationMessages.reservationCreated(3, 12, START);
        assertThat(created.data())
                .containsEntry("type", NotificationType.RESERVATION_CREATED.name())
                .containsEntry("bandId", "3")
                .containsEntry("reservationId", "12");
        assertThat(created.title()).isNotBlank();
        assertThat(created.body()).isNotBlank();
    }

    @Test
    void decision_message_differs_by_outcome() {
        PushMessage approved = NotificationMessages.decision(1, 2, START, true);
        PushMessage rejected = NotificationMessages.decision(1, 2, START, false);
        assertThat(approved.data()).containsEntry("type", NotificationType.RESERVATION_APPROVED.name());
        assertThat(rejected.data()).containsEntry("type", NotificationType.RESERVATION_REJECTED.name());
        assertThat(approved.body()).isNotEqualTo(rejected.body());
    }

    @Test
    void reminder_message_mentions_the_offset() {
        PushMessage reminder = NotificationMessages.reminder(1, 2, START, 30);
        assertThat(reminder.data()).containsEntry("type", NotificationType.RESERVATION_REMINDER.name());
        assertThat(reminder.body()).contains("30");
    }

    @Test
    void settlement_message_mentions_the_amount() {
        PushMessage settlement = NotificationMessages.settlementRequested(1, 2, 30000);
        assertThat(settlement.data()).containsEntry("type", NotificationType.SETTLEMENT_REQUESTED.name());
        assertThat(settlement.body()).contains("30000");
    }

    @Test
    void data_map_is_immutable() {
        PushMessage message = NotificationMessages.attendanceNudge(1, 2, START);
        assertThat(message.data()).containsEntry("type", NotificationType.ATTENDANCE_NUDGE.name());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> message.data().put("x", "y"));
    }
}
