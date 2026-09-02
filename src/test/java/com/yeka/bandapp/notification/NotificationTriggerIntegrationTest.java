package com.yeka.bandapp.notification;

import com.yeka.bandapp.support.FakePushSender;
import com.yeka.bandapp.support.PushTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 일정 등록·승인·거절·취소·정산이 커밋된 뒤(@TransactionalEventListener AFTER_COMMIT) 올바른 수신자에게
 * 푸시가 나가는지 본다. 발송자는 {@link FakePushSender}가 대체하며, 각 시나리오는 등록된 디바이스 토큰으로
 * "누가 받았는지"를 확인한다.
 */
@Import(PushTestConfig.class)
class NotificationTriggerIntegrationTest extends NotificationApiSupport {

    @Autowired
    private FakePushSender push;

    @BeforeEach
    void resetPush() {
        push.reset();
    }

    @Test
    void confirmed_reservation_notifies_every_member_except_the_creator() {
        String leader = signup("trg-c-l@band.app", "리더");
        String member = signup("trg-c-m@band.app", "멤버");
        long bandId = createBand(leader, "혁오");
        join(member, issueInvite(leader, bandId, null));
        registerToken(leader, "leader-dev", "ANDROID");
        registerToken(member, "member-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        createReservation(leader, bandId, roomId, T10, T13);   // LEADER_ONLY 기본 → 즉시 CONFIRMED

        assertThat(tokensFor("RESERVATION_CREATED")).containsExactly("member-dev");
    }

    @Test
    void approval_required_reservation_notifies_only_the_leader() {
        String leader = signup("trg-ar-l@band.app", "리더");
        String member = signup("trg-ar-m@band.app", "멤버");
        long bandId = createBand(leader, "잔나비");
        join(member, issueInvite(leader, bandId, null));
        setPermission(leader, bandId, "APPROVAL_REQUIRED");
        registerToken(leader, "leader-dev", "ANDROID");
        registerToken(member, "member-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        createReservationExpectPending(member, bandId, roomId);

        assertThat(tokensFor("RESERVATION_APPROVAL_REQUESTED")).containsExactly("leader-dev");
        assertThat(tokensFor("RESERVATION_CREATED")).isEmpty();
    }

    @Test
    void approve_notifies_the_requester() {
        String leader = signup("trg-ap-l@band.app", "리더");
        String member = signup("trg-ap-m@band.app", "멤버");
        long bandId = createBand(leader, "국카스텐");
        join(member, issueInvite(leader, bandId, null));
        setPermission(leader, bandId, "APPROVAL_REQUIRED");
        registerToken(member, "member-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservationExpectPending(member, bandId, roomId);
        push.reset();

        post("/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/approve", "{}", leader);

        assertThat(tokensFor("RESERVATION_APPROVED")).containsExactly("member-dev");
    }

    @Test
    void reject_notifies_the_requester() {
        String leader = signup("trg-rj-l@band.app", "리더");
        String member = signup("trg-rj-m@band.app", "멤버");
        long bandId = createBand(leader, "새소년");
        join(member, issueInvite(leader, bandId, null));
        setPermission(leader, bandId, "APPROVAL_REQUIRED");
        registerToken(member, "member-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservationExpectPending(member, bandId, roomId);
        push.reset();

        post("/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/reject", "{}", leader);

        assertThat(tokensFor("RESERVATION_REJECTED")).containsExactly("member-dev");
    }

    @Test
    void cancelling_twice_only_notifies_once() {
        String leader = signup("trg-cx-l@band.app", "리더");
        String member = signup("trg-cx-m@band.app", "멤버");
        long bandId = createBand(leader, "실리카겔");
        join(member, issueInvite(leader, bandId, null));
        setPermission(leader, bandId, "ANYONE");
        registerToken(member, "member-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        push.reset();

        assertThat(delete("/api/v1/bands/" + bandId + "/reservations/" + reservationId, leader)
                .getStatusCode().value()).isEqualTo(204);
        assertThat(delete("/api/v1/bands/" + bandId + "/reservations/" + reservationId, leader)
                .getStatusCode().value()).isEqualTo(204);

        assertThat(countOf("RESERVATION_CANCELLED")).isEqualTo(1);
        assertThat(tokensFor("RESERVATION_CANCELLED")).containsExactly("member-dev");
    }

    @Test
    void member_who_turned_push_off_is_skipped() {
        String leader = signup("trg-off-l@band.app", "리더");
        String member = signup("trg-off-m@band.app", "멤버");
        long bandId = createBand(leader, "쏜애플");
        join(member, issueInvite(leader, bandId, null));
        registerToken(member, "member-dev", "ANDROID");
        putSettings(member, false);
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        createReservation(leader, bandId, roomId, T10, T13);

        assertThat(tokensFor("RESERVATION_CREATED")).isEmpty();
    }

    @Test
    void settlement_creation_notifies_share_holders_except_the_creator() {
        String leader = signup("trg-st-l@band.app", "리더");
        String member = signup("trg-st-m@band.app", "멤버");
        long bandId = createBand(leader, "혁오둘");
        join(member, issueInvite(leader, bandId, null));
        registerToken(leader, "leader-dev", "ANDROID");
        registerToken(member, "member-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        push.reset();

        post("/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/settlement",
                "{\"totalAmount\":30000,\"splitType\":\"EQUAL\"}", leader);

        assertThat(tokensFor("SETTLEMENT_REQUESTED")).containsExactly("member-dev");
    }

    // --- helpers ---------------------------------------------------------

    private long createReservationExpectPending(String token, long bandId, long roomId) {
        var res = post("/api/v1/bands/" + bandId + "/reservations",
                reservationBody(roomId, T10, T13), token);
        assertThat(res.getStatusCode().value()).isEqualTo(201);
        return reservationOf(res).get("id").asLong();
    }

    private java.util.List<String> tokensFor(String type) {
        return push.sent().stream()
                .filter(s -> type.equals(s.message().data().get("type")))
                .flatMap(s -> s.tokens().stream())
                .toList();
    }

    private long countOf(String type) {
        return push.sent().stream()
                .filter(s -> type.equals(s.message().data().get("type")))
                .count();
    }
}
