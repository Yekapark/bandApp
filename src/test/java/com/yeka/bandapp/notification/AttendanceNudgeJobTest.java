package com.yeka.bandapp.notification;

import com.yeka.bandapp.notification.service.AttendanceNudgeService;
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
 * 참석 미응답 독촉 배치. 응답한 멤버는 빼고, 일정 생성 이후 합류해 참석 행이 없는 멤버도 대상에 넣는다.
 * 스케줄러를 기다리지 않고 {@link AttendanceNudgeService#runOnce}를 직접 호출한다. 멱등성을 단언한다.
 */
@Import(PushTestConfig.class)
class AttendanceNudgeJobTest extends NotificationApiSupport {

    @Autowired
    private AttendanceNudgeService attendanceNudgeService;

    @Autowired
    private FakePushSender push;

    @BeforeEach
    void resetPush() {
        push.reset();
    }

    @Test
    void nudges_non_responders_including_a_member_who_joined_after_creation() {
        String leader = signup("ndg-l@band.app", "리더");
        String responder = signup("ndg-r@band.app", "응답자");
        String latecomer = signup("ndg-late@band.app", "지각자");
        long bandId = createBand(leader, "혁오");
        join(responder, issueInvite(leader, bandId, null));
        long responderId = myUserId(responder);
        long latecomerId = myUserId(latecomer);
        registerToken(leader, "leader-dev", "ANDROID");
        registerToken(responder, "responder-dev", "ANDROID");
        registerToken(latecomer, "latecomer-dev", "ANDROID");

        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId,
                isoFromNow(Duration.ofHours(3)), isoFromNow(Duration.ofHours(4)));

        // responder 만 응답. latecomer 는 일정 생성 뒤 합류 → 참석 행이 없다.
        put("/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/attendances/" + responderId,
                "{\"status\":\"ATTENDING\"}", responder);
        join(latecomer, issueInvite(leader, bandId, null));
        push.reset();   // 일정 등록 시 발생한 "새 일정" 알림은 이 테스트 관심 밖

        int sent = attendanceNudgeService.runOnce(Instant.now());
        assertThat(sent).isEqualTo(2);
        assertThat(push.allTokens()).containsExactlyInAnyOrder("leader-dev", "latecomer-dev");

        // 다시 실행해도 재발송되지 않는다.
        assertThat(attendanceNudgeService.runOnce(Instant.now())).isZero();
        assertThat(push.sentCount()).isEqualTo(1);
    }

    /**
     * 회귀 방지 — 1차 독촉 뒤 합류한 멤버는 2차 실행에서 독촉되고, 이미 독촉한 멤버가 그 처리를 막지 않는다.
     */
    @Test
    void a_member_joining_between_runs_is_nudged_without_blocking_on_the_rest() {
        String leader = signup("ndg-cross-l@band.app", "리더");
        String early = signup("ndg-cross-e@band.app", "선참");
        long bandId = createBand(leader, "국카스텐");
        join(early, issueInvite(leader, bandId, null));
        registerToken(leader, "leader-dev", "ANDROID");
        registerToken(early, "early-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        createReservation(leader, bandId, roomId,
                isoFromNow(Duration.ofHours(3)), isoFromNow(Duration.ofHours(4)));
        push.reset();

        assertThat(attendanceNudgeService.runOnce(Instant.now())).isEqualTo(2);
        assertThat(push.allTokens()).containsExactlyInAnyOrder("leader-dev", "early-dev");

        String late = signup("ndg-cross-late@band.app", "지각");
        join(late, issueInvite(leader, bandId, null));
        registerToken(late, "late-dev", "ANDROID");
        push.reset();

        assertThat(attendanceNudgeService.runOnce(Instant.now())).isEqualTo(1);
        assertThat(push.allTokens()).containsExactly("late-dev");
    }

    @Test
    void does_nothing_when_reservation_is_beyond_the_lead_window() {
        String leader = signup("ndg-far-l@band.app", "리더");
        long bandId = createBand(leader, "잔나비");
        registerToken(leader, "leader-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        // 3일 뒤 시작 → 기본 리드타임(24시간) 밖.
        createReservation(leader, bandId, roomId,
                isoFromNow(Duration.ofDays(3)), isoFromNow(Duration.ofDays(3).plusHours(1)));

        assertThat(attendanceNudgeService.runOnce(Instant.now())).isZero();
        assertThat(push.sentCount()).isZero();
    }
}
