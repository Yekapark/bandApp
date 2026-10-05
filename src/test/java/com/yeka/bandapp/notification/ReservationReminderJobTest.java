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

    @Autowired
    private com.yeka.bandapp.notification.service.NotificationSender notificationSender;

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

    /**
     * 여러 시점이 한꺼번에 도래하면 시작에 가장 가까운 것 하나만 보낸다(테스터 의견 2026-10-01 — 10분 전에 만든 일정에
     * 6시간·3시간·1시간·30분·10분 전 알림이 한꺼번에 왔다).
     */
    @Test
    void only_the_nearest_due_offset_fires_when_several_are_due_at_once() {
        String leader = signup("rmd-multi-l@band.app", "리더");
        long bandId = createBand(leader, "잔나비");
        registerToken(leader, "leader-dev", "ANDROID");
        putSettings(leader, true, 10, 30, 60, 180, 360);
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        // 5분 뒤 시작 → 다섯 시점이 모두 도래.
        createReservation(leader, bandId, roomId,
                isoFromNow(Duration.ofMinutes(5)), isoFromNow(Duration.ofMinutes(65)));
        push.reset();

        assertThat(reminderService.runOnce(Instant.now())).isEqualTo(1);
        assertThat(push.sentCount()).isEqualTo(1);
        assertThat(reminderService.runOnce(Instant.now())).isZero();   // 지나간 먼 시점이 나중에 나가지도 않는다
        assertThat(push.sentCount()).isEqualTo(1);
    }

    /** 일정 시각을 바꾸면 리마인더가 바뀐 시각 기준으로 다시 나간다(테스터 의견 2026-10-01 — 수정하면 알림이 안 왔다). */
    @Test
    void rescheduling_sends_the_reminder_again_for_the_new_time() {
        String leader = signup("rmd-move-l@band.app", "리더");
        long bandId = createBand(leader, "검정치마");
        registerToken(leader, "leader-dev", "ANDROID");
        putSettings(leader, true, 60);
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId,
                isoFromNow(Duration.ofMinutes(30)), isoFromNow(Duration.ofMinutes(90)));
        push.reset();
        assertThat(reminderService.runOnce(Instant.now())).isEqualTo(1);   // 옛 시각의 60분 전 알림

        // 40분 뒤로 옮김 → 60분 전 시점이 다시 도래 상태. 예전에는 "이미 보냄" 으로 건너뛰었다.
        assertThat(put("/api/v1/bands/" + bandId + "/reservations/" + reservationId,
                "{\"roomId\":" + roomId + ",\"startAt\":\"" + isoFromNow(Duration.ofMinutes(40))
                        + "\",\"endAt\":\"" + isoFromNow(Duration.ofMinutes(100)) + "\"}", leader)
                .getStatusCode().value()).isEqualTo(200);

        assertThat(reminderService.runOnce(Instant.now())).isEqualTo(1);
        assertThat(push.sentCount()).isEqualTo(2);
    }

    /** QA PUSH-10 — 시작 시각은 그대로 두고 장소·메모만 바꾸면 이미 보낸 리마인더를 다시 보내지 않는다. */
    @Test
    void changing_only_the_room_or_note_does_not_resend_the_reminder() {
        String leader = signup("rmd-place-l@band.app", "리더");
        long bandId = createBand(leader, "장소만");
        registerToken(leader, "place-dev", "ANDROID");
        putSettings(leader, true, 60);
        long roomA = createRoom(leader, bandId, "{\"name\":\"A방\"}");
        long roomB = createRoom(leader, bandId, "{\"name\":\"B방\"}");
        // 앱처럼 분 단위 시각을 쓴다 — Instant.now() 의 나노초는 DB(마이크로초)에 잘려 "시각이 바뀜" 으로 보인다.
        Instant base = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        String start = base.plus(Duration.ofMinutes(30)).toString();
        String end = base.plus(Duration.ofMinutes(90)).toString();
        long reservationId = createReservation(leader, bandId, roomA, start, end);
        push.reset();
        assertThat(reminderService.runOnce(Instant.now())).isEqualTo(1);

        assertThat(put("/api/v1/bands/" + bandId + "/reservations/" + reservationId,
                "{\"roomId\":" + roomB + ",\"startAt\":\"" + start + "\",\"endAt\":\"" + end
                        + "\",\"note\":\"장소 바뀜\"}", leader).getStatusCode().value()).isEqualTo(200);

        assertThat(reminderService.runOnce(Instant.now())).isZero();
        assertThat(push.sentCount()).isEqualTo(1);
    }

    /** 시점보다 늦게 도래하면 문구는 실제 남은 시간 — 5분 뒤 시작인데 "1시간 뒤" 라고 하면 안 된다. */
    @Test
    void late_reminder_says_the_real_time_left() {
        String leader = signup("rmd-late-l@band.app", "리더");
        long bandId = createBand(leader, "혁오넷");
        registerToken(leader, "leader-dev", "ANDROID");
        putSettings(leader, true, 60);
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        Instant start = Instant.now().plus(Duration.ofMinutes(30));
        createReservation(leader, bandId, roomId, start.toString(), start.plus(Duration.ofHours(1)).toString());
        push.reset();

        assertThat(reminderService.runOnce(start.minus(Duration.ofMinutes(5)))).isEqualTo(1);
        assertThat(push.sent().get(0).message().body()).contains("5분 뒤").doesNotContain("1시간");
    }

    /** 보관기한 지난 이력 정리는 배치가 트랜잭션 없이 부른다 — 예전엔 @Transactional 이 빠져 매번 실패했다. */
    @Test
    void purging_old_dispatches_works_outside_a_transaction() {
        String leader = signup("rmd-purge-l@band.app", "리더");
        long bandId = createBand(leader, "정리");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        createReservation(leader, bandId, roomId,
                isoFromNow(Duration.ofMinutes(45)), isoFromNow(Duration.ofMinutes(105)));
        assertThat(reminderService.runOnce(Instant.now())).isEqualTo(1);

        assertThat(notificationSender.purgeDispatchesBefore(Instant.now().plus(Duration.ofDays(1))))
                .isGreaterThanOrEqualTo(1);
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
