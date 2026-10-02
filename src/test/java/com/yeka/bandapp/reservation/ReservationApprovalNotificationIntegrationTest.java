package com.yeka.bandapp.reservation;

import com.yeka.bandapp.notification.NotificationApiSupport;
import com.yeka.bandapp.support.FakePushSender;
import com.yeka.bandapp.support.PushTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 승인제(APPROVAL_REQUIRED) 밴드의 일정 알림 수신자.
 * 승인 대기 일정은 등록 때 밴드장에게만 알려지므로, 확정되면 나머지 멤버도 알아야 하고
 * 대기 중에 취소되면 몰랐던 멤버에게 "취소" 알림이 가면 안 된다.
 */
@Import(PushTestConfig.class)
class ReservationApprovalNotificationIntegrationTest extends NotificationApiSupport {

    @Autowired
    private FakePushSender push;

    @BeforeEach
    void resetPush() {
        push.reset();
    }

    @Test
    void approving_tells_the_other_members_about_the_new_schedule() {
        String leader = signup("apn-a-l@band.app", "리더");
        String member = signup("apn-a-m@band.app", "멤버");
        String other = signup("apn-a-o@band.app", "다른멤버");
        long bandId = createBand(leader, "승인알림");
        join(member, issueInvite(leader, bandId, null));
        join(other, issueInvite(leader, bandId, null));
        setPermission(leader, bandId, "APPROVAL_REQUIRED");
        registerToken(leader, "leader-dev", "ANDROID");
        registerToken(member, "member-dev", "ANDROID");
        registerToken(other, "other-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createPending(member, bandId, roomId);
        assertThat(tokensFor("RESERVATION_CREATED")).isEmpty();
        push.reset();

        post("/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/approve", "{}", leader);

        assertThat(tokensFor("RESERVATION_APPROVED")).containsExactly("member-dev");
        assertThat(tokensFor("RESERVATION_CREATED")).containsExactly("other-dev");
    }

    @Test
    void cancelling_a_pending_schedule_does_not_notify_members_who_never_saw_it() {
        String leader = signup("apn-c-l@band.app", "리더");
        String member = signup("apn-c-m@band.app", "멤버");
        String other = signup("apn-c-o@band.app", "다른멤버");
        long bandId = createBand(leader, "대기취소");
        join(member, issueInvite(leader, bandId, null));
        join(other, issueInvite(leader, bandId, null));
        setPermission(leader, bandId, "APPROVAL_REQUIRED");
        registerToken(leader, "leader-dev", "ANDROID");
        registerToken(member, "member-dev", "ANDROID");
        registerToken(other, "other-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createPending(member, bandId, roomId);
        push.reset();

        assertThat(delete("/api/v1/bands/" + bandId + "/reservations/" + reservationId, member)
                .getStatusCode().value()).isEqualTo(204);

        assertThat(tokensFor("RESERVATION_CANCELLED")).containsExactly("leader-dev");
    }

    @Test
    void leader_cancelling_a_pending_request_tells_the_requester_only() {
        String leader = signup("apn-r-l@band.app", "리더");
        String member = signup("apn-r-m@band.app", "멤버");
        String other = signup("apn-r-o@band.app", "다른멤버");
        long bandId = createBand(leader, "대기취소둘");
        join(member, issueInvite(leader, bandId, null));
        join(other, issueInvite(leader, bandId, null));
        setPermission(leader, bandId, "APPROVAL_REQUIRED");
        registerToken(member, "member-dev", "ANDROID");
        registerToken(other, "other-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createPending(member, bandId, roomId);
        push.reset();

        assertThat(delete("/api/v1/bands/" + bandId + "/reservations/" + reservationId, leader)
                .getStatusCode().value()).isEqualTo(204);

        assertThat(tokensFor("RESERVATION_CANCELLED")).containsExactly("member-dev");
    }

    private long createPending(String token, long bandId, long roomId) {
        var res = post("/api/v1/bands/" + bandId + "/reservations", reservationBody(roomId, T10, T13), token);
        assertThat(res.getStatusCode().value()).isEqualTo(201);
        assertThat(reservationOf(res).get("status").asText()).isEqualTo("PENDING");
        return reservationOf(res).get("id").asLong();
    }

    private List<String> tokensFor(String type) {
        return push.sent().stream()
                .filter(s -> type.equals(s.message().data().get("type")))
                .flatMap(s -> s.tokens().stream())
                .toList();
    }
}
