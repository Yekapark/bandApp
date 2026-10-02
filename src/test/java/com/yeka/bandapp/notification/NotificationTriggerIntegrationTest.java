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

    /** 승인제 밴드에서 확정 일정을 고쳐 다시 대기로 돌아가면, 재승인 요청과 재승인 알림이 다시 나간다. */
    @Test
    void editing_a_confirmed_reservation_in_approval_mode_requests_and_announces_approval_again() {
        String leader = signup("trg-reap-l@band.app", "리더");
        String member = signup("trg-reap-m@band.app", "멤버");
        long bandId = createBand(leader, "재승인");
        join(member, issueInvite(leader, bandId, null));
        setPermission(leader, bandId, "APPROVAL_REQUIRED");
        registerToken(leader, "leader-dev", "ANDROID");
        registerToken(member, "member-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservationExpectPending(member, bandId, roomId);
        String base = "/api/v1/bands/" + bandId + "/reservations/" + reservationId;
        post(base + "/approve", "{}", leader);
        push.reset();

        assertThat(put(base, "{\"roomId\":" + roomId + ",\"startAt\":\"" + T13 + "\",\"endAt\":\"" + T16 + "\"}",
                member).getStatusCode().value()).isEqualTo(200);
        assertThat(tokensFor("RESERVATION_APPROVAL_REQUESTED")).containsExactly("leader-dev");

        post(base + "/approve", "{}", leader);
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
        long reservationId = createReservation(leader, bandId, roomId, F10, F13);
        push.reset();

        assertThat(delete("/api/v1/bands/" + bandId + "/reservations/" + reservationId, leader)
                .getStatusCode().value()).isEqualTo(204);
        assertThat(delete("/api/v1/bands/" + bandId + "/reservations/" + reservationId, leader)
                .getStatusCode().value()).isEqualTo(204);

        assertThat(countOf("RESERVATION_CANCELLED")).isEqualTo(1);
        assertThat(tokensFor("RESERVATION_CANCELLED")).containsExactly("member-dev");
    }

    /** 결정 #28 — 푸시를 꺼도 앱 안 알림 목록에는 쌓인다. FCM 만 건너뛴다. */
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
        assertThat(data(get(NOTIFICATIONS + "?bandId=" + bandId, member)).get("notifications")).hasSize(1);
    }

    /** 결정 #27 — 푸시 제목 앞에 밴드 이름. 앱 안 목록(밴드별)은 원래 제목 그대로. */
    @Test
    void push_title_starts_with_the_band_name() {
        String leader = signup("trg-bn-l@band.app", "리더");
        String member = signup("trg-bn-m@band.app", "멤버");
        long bandId = createBand(leader, "노을밴드");
        join(member, issueInvite(leader, bandId, null));
        registerToken(member, "member-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        createReservation(leader, bandId, roomId, T10, T13);

        assertThat(push.sent().get(0).message().title()).isEqualTo("[노을밴드] 새 합주 일정");
        assertThat(data(get(NOTIFICATIONS + "?bandId=" + bandId, member)).get("notifications").get(0)
                .get("title").asText()).isEqualTo("새 합주 일정");
    }

    /** 결정 #18 — 확정 일정의 시간·장소가 바뀌면 수정자 말고 다른 멤버에게, 고칠 때마다. 비고만 바뀌면 안 보낸다. */
    @Test
    void changing_a_confirmed_reservation_tells_the_other_members_every_time() {
        String leader = signup("trg-chg-l@band.app", "리더");
        String member = signup("trg-chg-m@band.app", "멤버");
        long bandId = createBand(leader, "변경알림");
        join(member, issueInvite(leader, bandId, null));
        registerToken(leader, "leader-dev", "ANDROID");
        registerToken(member, "member-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long otherRoom = createRoom(leader, bandId, "{\"name\":\"다른 방\"}");
        long reservationId = createReservation(leader, bandId, roomId, F10, F13);
        String base = "/api/v1/bands/" + bandId + "/reservations/" + reservationId;
        push.reset();

        put(base, "{\"roomId\":" + roomId + ",\"startAt\":\"" + F10 + "\",\"endAt\":\"" + F13
                + "\",\"note\":\"메모만\"}", leader);
        assertThat(tokensFor("RESERVATION_CHANGED")).isEmpty();

        put(base, reservationBody(roomId, F13, F16), leader);
        put(base, reservationBody(otherRoom, F13, F16), leader);

        assertThat(tokensFor("RESERVATION_CHANGED")).containsExactly("member-dev", "member-dev");
        assertThat(push.sent().get(0).message().data()).containsEntry("reservationId", Long.toString(reservationId));
    }

    /** 결정 #19 — 승인제 밴드라도 밴드장이 등록·수정한 일정은 바로 확정. */
    @Test
    void leader_reservations_skip_approval_in_approval_mode() {
        String leader = signup("trg-la-l@band.app", "리더");
        String member = signup("trg-la-m@band.app", "멤버");
        long bandId = createBand(leader, "밴드장확정");
        join(member, issueInvite(leader, bandId, null));
        setPermission(leader, bandId, "APPROVAL_REQUIRED");
        registerToken(leader, "leader-dev", "ANDROID");
        registerToken(member, "member-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        var created = post("/api/v1/bands/" + bandId + "/reservations", reservationBody(roomId, F10, F13), leader);
        assertThat(reservationOf(created).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(tokensFor("RESERVATION_CREATED")).containsExactly("member-dev");
        assertThat(tokensFor("RESERVATION_APPROVAL_REQUESTED")).isEmpty();

        long reservationId = reservationOf(created).get("id").asLong();
        var updated = put("/api/v1/bands/" + bandId + "/reservations/" + reservationId,
                reservationBody(roomId, F13, F16), leader);
        assertThat(reservationOf(updated).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(tokensFor("RESERVATION_APPROVAL_REQUESTED")).isEmpty();
    }

    /** 결정 #20 — 이미 끝난 일정을 취소하면 알리지 않는다. */
    @Test
    void cancelling_a_finished_reservation_sends_no_push() {
        String leader = signup("trg-pcx-l@band.app", "리더");
        String member = signup("trg-pcx-m@band.app", "멤버");
        long bandId = createBand(leader, "지난일정");
        join(member, issueInvite(leader, bandId, null));
        registerToken(member, "member-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);   // 2026-09-10 — 지난 일정
        push.reset();

        assertThat(delete("/api/v1/bands/" + bandId + "/reservations/" + reservationId, leader)
                .getStatusCode().value()).isEqualTo(204);
        assertThat(tokensFor("RESERVATION_CANCELLED")).isEmpty();
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

    /** 재계산으로 총액이 바뀌면 다시 알린다. 예전에는 variant 0 고정이라 "이미 보냄" 으로 걸러져 아무도 몰랐다. */
    @Test
    void recalculating_with_a_new_total_notifies_again_but_same_total_does_not() {
        String leader = signup("trg-rc-l@band.app", "리더");
        String member = signup("trg-rc-m@band.app", "멤버");
        long bandId = createBand(leader, "혁오셋");
        join(member, issueInvite(leader, bandId, null));
        registerToken(member, "member-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        String path = "/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/settlement";
        post(path, "{\"totalAmount\":30000,\"splitType\":\"EQUAL\"}", leader);
        push.reset();

        post(path + "/recalculate", "{}", leader);   // 같은 총액 — 다시 보낼 필요 없음
        assertThat(tokensFor("SETTLEMENT_REQUESTED")).isEmpty();

        post(path + "/recalculate", "{\"totalAmount\":40000}", leader);
        assertThat(tokensFor("SETTLEMENT_REQUESTED")).containsExactly("member-dev");
        assertThat(push.sent().get(0).message().body()).contains("40,000원");
    }

    /**
     * 재계산으로 몫이 바뀌어 납부 체크가 풀린 사람은 "정산 금액이 바뀌었어요 (이전 → 새)" 를 한 번만 받고,
     * 아직 안 낸 사람은 평소처럼 새 총액의 정산 요청을 받는다.
     */
    @Test
    void recalculation_that_changes_a_paid_share_tells_that_member_the_old_and_new_amount() {
        String leader = signup("trg-pc-l@band.app", "리더");
        String payer = signup("trg-pc-p@band.app", "낸사람");
        String other = signup("trg-pc-o@band.app", "안낸사람");
        long bandId = createBand(leader, "혁오넷");
        join(payer, issueInvite(leader, bandId, null));
        join(other, issueInvite(leader, bandId, null));
        registerToken(payer, "payer-dev", "ANDROID");
        registerToken(other, "other-dev", "ANDROID");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        String path = "/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/settlement";
        post(path, "{\"totalAmount\":30000,\"splitType\":\"EQUAL\"}", leader);   // 10,000 씩
        long payerId = myUserId(payer);
        assertThat(put(path + "/shares/" + payerId, "{\"paid\":true}", payer).getStatusCode().value())
                .isEqualTo(200);
        push.reset();

        post(path + "/recalculate", "{\"totalAmount\":45000}", leader);           // 15,000 씩

        var payerPushes = push.sent().stream().filter(p -> p.tokens().contains("payer-dev")).toList();
        assertThat(payerPushes).hasSize(1);
        assertThat(payerPushes.get(0).message().body()).contains("10,000원 → 15,000원");
        assertThat(payerPushes.get(0).message().data()).containsEntry("type", "SETTLEMENT_REQUESTED");
        var otherPushes = push.sent().stream().filter(p -> p.tokens().contains("other-dev")).toList();
        assertThat(otherPushes).hasSize(1);
        assertThat(otherPushes.get(0).message().body()).contains("45,000원");
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
