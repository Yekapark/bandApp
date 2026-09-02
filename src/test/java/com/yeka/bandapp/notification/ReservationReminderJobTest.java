package com.yeka.bandapp.notification;

import com.yeka.bandapp.notification.service.ReminderService;
import com.yeka.bandapp.support.FakePushSender;
import com.yeka.bandapp.support.PushTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 일정 리마인더 배치. 스케줄러를 기다리지 않고 {@link ReminderService#runOnce}를 직접 호출한다
 * ({@code RecurringExtensionJobTest} 방식). 멱등성(2회 실행해도 1회만 발송)을 반드시 단언한다.
 */
@Import(PushTestConfig.class)
class ReservationReminderJobTest extends NotificationApiSupport {

    @Autowired
    private ReminderService reminderService;

    @Autowired
    private FakePushSender push;

    @BeforeEach
    void resetPush() {
        push.reset();
    }

    @Test
    void sends_once_when_a_default_offset_is_due_and_is_idempotent() {
        String leader = signup("rmd-def-l@band.app", "리더");
        long bandId = createBand(leader, "혁오");
        registerToken(leader, "leader-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        // 기본 리마인더 시점은 60분 전. 45분 뒤 시작이면 이미 도래한 상태.
        createReservation(leader, bandId, roomId,
                isoFromNow(Duration.ofMinutes(45)), isoFromNow(Duration.ofMinutes(105)));

        int sent = reminderService.runOnce(Instant.now());
        assertThat(sent).isEqualTo(1);
        assertThat(push.allTokens()).containsExactly("leader-dev");

        // 다시 실행해도 이력(dispatch)이 있어 재발송되지 않는다.
        assertThat(reminderService.runOnce(Instant.now())).isZero();
        assertThat(push.sentCount()).isEqualTo(1);
    }

    @Test
    void each_configured_offset_fires_once() {
        String leader = signup("rmd-multi-l@band.app", "리더");
        long bandId = createBand(leader, "잔나비");
        registerToken(leader, "leader-dev", "ANDROID");
        putSettings(leader, true, 10, 30);
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        // 5분 뒤 시작 → 10분 전·30분 전 시점이 모두 도래.
        createReservation(leader, bandId, roomId,
                isoFromNow(Duration.ofMinutes(5)), isoFromNow(Duration.ofMinutes(65)));

        int sent = reminderService.runOnce(Instant.now());
        assertThat(sent).isEqualTo(2);
        assertThat(push.sentCount()).isEqualTo(2);
        assertThat(reminderService.runOnce(Instant.now())).isZero();
    }

    @Test
    void offset_not_yet_due_is_not_sent() {
        String leader = signup("rmd-early-l@band.app", "리더");
        long bandId = createBand(leader, "국카스텐");
        registerToken(leader, "leader-dev", "ANDROID");
        putSettings(leader, true, 10);
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        // 3시간 뒤 시작, 시점은 10분 전 → 아직 도래하지 않음.
        createReservation(leader, bandId, roomId,
                isoFromNow(Duration.ofHours(3)), isoFromNow(Duration.ofHours(4)));

        assertThat(reminderService.runOnce(Instant.now())).isZero();
        assertThat(push.sentCount()).isZero();
    }

    /**
     * 회귀 방지 — 한 offset 이 이미 발송된 상태에서 다른 offset 이 뒤늦게 도래해도 그 건은 정상 발송된다.
     * (이력 기록을 saveAndFlush+catch 로 하면 "이미 발송" 이 REQUIRES_NEW 를 rollback-only 로 만들어
     * 같은 실행의 나머지 offset 이 통째로 누락됐다.)
     */
    @Test
    void a_newly_due_offset_still_fires_after_another_offset_was_already_sent() {
        String leader = signup("rmd-partial-l@band.app", "리더");
        long bandId = createBand(leader, "실리카겔");
        registerToken(leader, "leader-dev", "ANDROID");
        putSettings(leader, true, 10, 30);
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        Instant start = Instant.now().plus(Duration.ofMinutes(25));
        createReservation(leader, bandId, roomId,
                start.toString(), start.plus(Duration.ofHours(1)).toString());
        push.reset();

        // T-A: 30분 전 시점만 도래(10분 전은 아직).
        assertThat(reminderService.runOnce(start.minus(Duration.ofMinutes(20)))).isEqualTo(1);
        // T-B: 10분 전도 도래 → 30분 전은 이미 보냈고, 10분 전만 새로 나가야 한다.
        assertThat(reminderService.runOnce(start.minus(Duration.ofMinutes(5)))).isEqualTo(1);

        assertThat(push.sentCount()).isEqualTo(2);
        assertThat(push.allTokens()).containsExactly("leader-dev", "leader-dev");
    }

    @Test
    void cancelled_reservation_is_not_reminded() {
        String leader = signup("rmd-cx-l@band.app", "리더");
        long bandId = createBand(leader, "새소년");
        registerToken(leader, "leader-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId,
                isoFromNow(Duration.ofMinutes(45)), isoFromNow(Duration.ofMinutes(105)));
        delete("/api/v1/bands/" + bandId + "/reservations/" + reservationId, leader);

        assertThat(reminderService.runOnce(Instant.now())).isZero();
        assertThat(push.sentCount()).isZero();
    }
}
