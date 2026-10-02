package com.yeka.bandapp.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.reservation.ReservationApiSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 7 완료 기준:
 * <ul>
 *   <li>3명이 10,000원을 나누는 등 나머지가 발생하는 케이스에서 share 합계가 총액과 정확히 일치한다.</li>
 *   <li>{@code ATTENDEES_ONLY}인데 참석자가 0명인 경우가 명시적으로 처리된다(거부, 미저장).</li>
 * </ul>
 * 여기에 참석자 기준 분배, 참석자 변경 후 재계산(납부 상태 보존), 총액 수정, 권한(등록자/밴드장),
 * 본인만 납부 체크, 타 밴드 격리, 중복 생성 거부를 함께 본다.
 */
class SettlementIntegrationTest extends ReservationApiSupport {

    // --- 완료 기준 -------------------------------------------------------

    /** 완료 기준 ① — 3명 / 10,000원 EQUAL: 3334 + 3333 + 3333 = 10,000, 나머지는 밴드장이 진다. */
    @Test
    void equal_split_of_three_members_puts_remainder_on_leader() {
        String leader = signup("stl-eq-l@band.app", "리더");
        String m1 = signup("stl-eq-1@band.app", "멤버1");
        String m2 = signup("stl-eq-2@band.app", "멤버2");
        long bandId = createBand(leader, "혁오");
        join(m1, issueInvite(leader, bandId, null));
        join(m2, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"1번방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        long leaderId = myUserId(leader);

        JsonNode s = data(createSettlement(leader, bandId, reservationId, 10_000, "EQUAL"));

        assertThat(s.get("totalAmount").asInt()).isEqualTo(10_000);
        assertThat(s.get("shareCount").asInt()).isEqualTo(3);
        assertThat(sumOfShares(s)).isEqualTo(10_000);
        assertThat(shareOf(s, leaderId).get("amount").asInt()).isEqualTo(3_334);
        assertThat(shareOf(s, myUserId(m1)).get("amount").asInt()).isEqualTo(3_333);
        assertThat(shareOf(s, myUserId(m2)).get("amount").asInt()).isEqualTo(3_333);
    }

    /** 완료 기준 ② — ATTENDEES_ONLY 인데 참석자가 0명이면 409, 정산은 만들어지지 않는다. */
    @Test
    void attendees_only_with_zero_attendees_is_rejected_and_persists_nothing() {
        String leader = signup("stl-none-l@band.app", "리더");
        long bandId = createBand(leader, "잔나비");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);

        ResponseEntity<String> res = post(settlementPath(bandId, reservationId),
                "{\"totalAmount\":30000,\"splitType\":\"ATTENDEES_ONLY\"}", leader);
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("SETTLEMENT_NO_ATTENDEES");

        ResponseEntity<String> get = get(settlementPath(bandId, reservationId), leader);
        assertThat(get.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(get)).isEqualTo("SETTLEMENT_NOT_FOUND");
    }

    // --- 참석자 기준 분배 / 재계산 --------------------------------------

    /** ATTENDEES_ONLY 는 참석(ATTENDING) 멤버만 나눈다. 참석 안 한 멤버는 몫이 없다. */
    @Test
    void attendees_only_splits_among_attending_members() {
        String leader = signup("stl-att-l@band.app", "리더");
        String m1 = signup("stl-att-1@band.app", "멤버1");
        String m2 = signup("stl-att-2@band.app", "멤버2");
        long bandId = createBand(leader, "국카스텐");
        join(m1, issueInvite(leader, bandId, null));
        join(m2, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        long leaderId = myUserId(leader);
        long m1Id = myUserId(m1);

        setAttendance(leader, bandId, reservationId, leaderId, "ATTENDING");
        setAttendance(m1, bandId, reservationId, m1Id, "ATTENDING");
        // m2 는 미응답(PENDING)

        JsonNode s = data(createSettlement(leader, bandId, reservationId, 10_000, "ATTENDEES_ONLY"));

        assertThat(s.get("shareCount").asInt()).isEqualTo(2);
        assertThat(sumOfShares(s)).isEqualTo(10_000);
        assertThat(shareOf(s, leaderId).get("amount").asInt()).isEqualTo(5_000);
        assertThat(shareOf(s, m1Id).get("amount").asInt()).isEqualTo(5_000);
        assertThat(hasShare(s, myUserId(m2))).isFalse();
    }

    /** 재계산 — 새로 참석한 멤버가 추가되고, 몫이 바뀐 납부자는 체크가 풀린다(4,500 낸 사람의 몫이 3,000 이 됨). */
    @Test
    void recalculate_adds_new_attendee_and_unmarks_paid_share_whose_amount_changed() {
        String leader = signup("stl-rec-l@band.app", "리더");
        String m1 = signup("stl-rec-1@band.app", "멤버1");
        String m2 = signup("stl-rec-2@band.app", "멤버2");
        long bandId = createBand(leader, "새소년");
        join(m1, issueInvite(leader, bandId, null));
        join(m2, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        long leaderId = myUserId(leader);
        long m1Id = myUserId(m1);
        long m2Id = myUserId(m2);

        setAttendance(leader, bandId, reservationId, leaderId, "ATTENDING");
        setAttendance(m1, bandId, reservationId, m1Id, "ATTENDING");
        createSettlement(leader, bandId, reservationId, 9_000, "ATTENDEES_ONLY"); // 4500 + 4500

        // m1 이 본인 몫 납부 체크
        JsonNode afterPaid = data(markPaid(m1, bandId, reservationId, m1Id, true));
        assertThat(shareOf(afterPaid, m1Id).get("paid").asBoolean()).isTrue();

        // m2 가 뒤늦게 참석 → 재계산
        setAttendance(m2, bandId, reservationId, m2Id, "ATTENDING");
        JsonNode s = data(recalculate(leader, bandId, reservationId, "{}"));

        assertThat(s.get("shareCount").asInt()).isEqualTo(3);
        assertThat(sumOfShares(s)).isEqualTo(9_000);            // 3000 * 3
        assertThat(shareOf(s, m1Id).get("amount").asInt()).isEqualTo(3_000);
        assertThat(shareOf(s, m1Id).get("paid").asBoolean()).isFalse();  // 4500 만 냈으므로 풀림
        assertThat(shareOf(s, m1Id).get("paidAt").isNull()).isTrue();
        assertThat(shareOf(s, m2Id).get("paid").asBoolean()).isFalse();  // 신규는 미납
        assertThat(s.get("paidAmount").asInt()).isZero();
        assertThat(s.get("outstandingAmount").asInt()).isEqualTo(9_000);
    }

    /**
     * 항목 1 — 40,000원 / 4명 = 10,000. A 가 내고 체크 → 같은 금액으로 재계산하면 체크 유지,
     * 60,000원으로 재계산하면 A 몫 15,000 이 되며 체크가 풀린다(예전엔 "낸 사람" 으로 남아 5,000 이 사라졌다).
     */
    @Test
    void paid_mark_survives_unchanged_amount_but_is_cleared_when_amount_changes() {
        String leader = signup("stl-chg-l@band.app", "리더");
        String a = signup("stl-chg-a@band.app", "에이");
        String b = signup("stl-chg-b@band.app", "비");
        String c = signup("stl-chg-c@band.app", "씨");
        long bandId = createBand(leader, "변경밴드");
        join(a, issueInvite(leader, bandId, null));
        join(b, issueInvite(leader, bandId, null));
        join(c, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        long aId = myUserId(a);

        createSettlement(leader, bandId, reservationId, 40_000, "EQUAL");
        markPaid(a, bandId, reservationId, aId, true);

        JsonNode same = data(recalculate(leader, bandId, reservationId, "{}"));
        assertThat(shareOf(same, aId).get("amount").asInt()).isEqualTo(10_000);
        assertThat(shareOf(same, aId).get("paid").asBoolean()).isTrue();
        assertThat(same.get("paidAmount").asInt()).isEqualTo(10_000);

        JsonNode s = data(recalculate(leader, bandId, reservationId, "{\"totalAmount\":60000}"));
        assertThat(shareOf(s, aId).get("amount").asInt()).isEqualTo(15_000);
        assertThat(shareOf(s, aId).get("paid").asBoolean()).isFalse();
        assertThat(shareOf(s, aId).get("paidAt").isNull()).isTrue();
        assertThat(sumOfShares(s)).isEqualTo(60_000);
        assertThat(s.get("paidCount").asInt()).isZero();
        assertThat(s.get("outstandingAmount").asInt()).isEqualTo(60_000);

        JsonNode list = data(get("/api/v1/bands/" + bandId + "/settlements", a));
        assertThat(list.get("myOutstandingTotal").asLong()).isEqualTo(15_000);
    }

    /**
     * 항목 2 — 40,000원 / 4명 = 10,000. A 가 내고 밴드를 떠남 → 재계산해도 A 의 10,000(납부) 행은 남고,
     * 남은 30,000 만 3명이 10,000씩. 예전엔 A 행이 지워지고 40,000 을 3명이 13,334/13,333/13,333 으로 나눠
     * 10,000 을 더 걷었다. 총액을 A 가 낸 금액보다 작게 하면 409, 같게 하면 나머지는 0원.
     */
    @Test
    void paid_share_of_member_who_left_is_kept_and_only_the_rest_is_split() {
        String leader = signup("stl-left-l@band.app", "리더");
        String a = signup("stl-left-a@band.app", "떠난에이");
        String b = signup("stl-left-b@band.app", "비");
        String c = signup("stl-left-c@band.app", "씨");
        long bandId = createBand(leader, "떠남밴드");
        join(a, issueInvite(leader, bandId, null));
        join(b, issueInvite(leader, bandId, null));
        join(c, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        long leaderId = myUserId(leader);
        long aId = myUserId(a);
        long bId = myUserId(b);

        createSettlement(leader, bandId, reservationId, 40_000, "EQUAL");
        markPaid(a, bandId, reservationId, aId, true);
        assertThat(post("/api/v1/bands/" + bandId + "/members/leave", "", a).getStatusCode().is2xxSuccessful())
                .isTrue();

        JsonNode s = data(recalculate(leader, bandId, reservationId, "{}"));
        assertThat(s.get("shareCount").asInt()).isEqualTo(4);
        assertThat(sumOfShares(s)).isEqualTo(40_000);
        JsonNode aShare = shareOf(s, aId);
        assertThat(aShare.get("amount").asInt()).isEqualTo(10_000);
        assertThat(aShare.get("paid").asBoolean()).isTrue();
        assertThat(aShare.get("name").asText()).isEqualTo("떠난에이");   // 떠났어도 이름이 보인다
        assertThat(shareOf(s, leaderId).get("amount").asInt()).isEqualTo(10_000);
        assertThat(shareOf(s, bId).get("amount").asInt()).isEqualTo(10_000);
        assertThat(s.get("paidAmount").asInt()).isEqualTo(10_000);
        assertThat(s.get("outstandingAmount").asInt()).isEqualTo(30_000);

        // 총액 32,000 → 남은 22,000 / 3 = 7,334(밴드장) + 7,333 + 7,333
        JsonNode lower = data(recalculate(leader, bandId, reservationId, "{\"totalAmount\":32000}"));
        assertThat(shareOf(lower, aId).get("amount").asInt()).isEqualTo(10_000);
        assertThat(shareOf(lower, leaderId).get("amount").asInt()).isEqualTo(7_334);
        assertThat(shareOf(lower, bId).get("amount").asInt()).isEqualTo(7_333);
        assertThat(sumOfShares(lower)).isEqualTo(32_000);

        // A 가 낸 10,000 보다 작은 총액은 거부(음수 몫을 만들지 않는다). 아무것도 바뀌지 않는다.
        ResponseEntity<String> tooLow = post(settlementPath(bandId, reservationId) + "/recalculate",
                "{\"totalAmount\":5000}", leader);
        assertThat(tooLow.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(tooLow)).isEqualTo("SETTLEMENT_TOTAL_BELOW_PAID");
        assertThat(data(get(settlementPath(bandId, reservationId), leader)).get("totalAmount").asInt())
                .isEqualTo(32_000);

        // 딱 10,000 이면 남은 사람은 0원.
        JsonNode exact = data(recalculate(leader, bandId, reservationId, "{\"totalAmount\":10000}"));
        assertThat(sumOfShares(exact)).isEqualTo(10_000);
        assertThat(shareOf(exact, leaderId).get("amount").asInt()).isZero();
        assertThat(exact.get("outstandingAmount").asInt()).isZero();
    }

    /** LAUNCH_REVIEW L8 — 탈퇴한 사람의 몫은 90일 파기 전이라도 바로 "탈퇴한 사용자" 로 보인다(실명 노출 금지). */
    @Test
    void withdrawn_members_share_shows_withdrawn_name_immediately() {
        String leader = signup("stl-wd-l@band.app", "리더");
        String a = signup("stl-wd-a@band.app", "실명에이");
        long bandId = createBand(leader, "탈퇴밴드");
        join(a, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        long aId = myUserId(a);
        createSettlement(leader, bandId, reservationId, 20_000, "EQUAL");
        markPaid(a, bandId, reservationId, aId, true);

        withdraw(a);

        JsonNode s = data(get(settlementPath(bandId, reservationId), leader));
        assertThat(shareOf(s, aId).get("name").asText()).isEqualTo("탈퇴한 사용자");
        assertThat(s.toString()).doesNotContain("실명에이");
    }

    /**
     * 두 규칙을 함께 — ATTENDEES_ONLY 40,000원 / 4명 = 10,000. A·B 가 냄 → A 는 불참으로 바꿈 →
     * 총액 46,000 으로 재계산. A 의 10,000 은 그대로 남고(빠진 사람의 낸 몫 고정), 남은 36,000 을
     * 리더·B·C 가 12,000 씩. B 는 계속 참석자인데 몫이 10,000 → 12,000 으로 바뀌어 체크가 풀린다.
     */
    @Test
    void excluded_paid_share_is_fixed_while_participant_paid_share_is_recalculated() {
        String leader = signup("stl-mix-l@band.app", "리더");
        String a = signup("stl-mix-a@band.app", "에이");
        String b = signup("stl-mix-b@band.app", "비");
        String c = signup("stl-mix-c@band.app", "씨");
        long bandId = createBand(leader, "섞임밴드");
        join(a, issueInvite(leader, bandId, null));
        join(b, issueInvite(leader, bandId, null));
        join(c, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        long leaderId = myUserId(leader);
        long aId = myUserId(a);
        long bId = myUserId(b);
        long cId = myUserId(c);
        setAttendance(leader, bandId, reservationId, leaderId, "ATTENDING");
        setAttendance(a, bandId, reservationId, aId, "ATTENDING");
        setAttendance(b, bandId, reservationId, bId, "ATTENDING");
        setAttendance(c, bandId, reservationId, cId, "ATTENDING");

        createSettlement(leader, bandId, reservationId, 40_000, "ATTENDEES_ONLY");
        markPaid(a, bandId, reservationId, aId, true);
        markPaid(b, bandId, reservationId, bId, true);
        setAttendance(a, bandId, reservationId, aId, "ABSENT");

        JsonNode s = data(recalculate(leader, bandId, reservationId, "{\"totalAmount\":46000}"));

        assertThat(s.get("shareCount").asInt()).isEqualTo(4);
        assertThat(sumOfShares(s)).isEqualTo(46_000);
        assertThat(shareOf(s, aId).get("amount").asInt()).isEqualTo(10_000);
        assertThat(shareOf(s, aId).get("paid").asBoolean()).isTrue();
        assertThat(shareOf(s, bId).get("amount").asInt()).isEqualTo(12_000);
        assertThat(shareOf(s, bId).get("paid").asBoolean()).isFalse();
        assertThat(shareOf(s, leaderId).get("amount").asInt()).isEqualTo(12_000);
        assertThat(shareOf(s, cId).get("amount").asInt()).isEqualTo(12_000);
        assertThat(s.get("paidCount").asInt()).isEqualTo(1);
        assertThat(s.get("paidAmount").asInt()).isEqualTo(10_000);
        assertThat(s.get("outstandingAmount").asInt()).isEqualTo(36_000);

        JsonNode list = data(get("/api/v1/bands/" + bandId + "/settlements", b));
        JsonNode item = list.get("settlements").get(0);
        assertThat(item.get("shareCount").asInt()).isEqualTo(4);
        assertThat(item.get("paidCount").asInt()).isEqualTo(1);
        assertThat(item.get("myAmount").asInt()).isEqualTo(12_000);
        assertThat(list.get("myOutstandingTotal").asLong()).isEqualTo(12_000);
    }

    /** 재계산 본문에 totalAmount 를 넘기면 그 값으로 갱신된다(splitType 은 유지). */
    @Test
    void recalculate_can_update_total_amount() {
        String leader = signup("stl-amt-l@band.app", "리더");
        String m1 = signup("stl-amt-1@band.app", "멤버1");
        String m2 = signup("stl-amt-2@band.app", "멤버2");
        long bandId = createBand(leader, "실리카겔");
        join(m1, issueInvite(leader, bandId, null));
        join(m2, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);

        createSettlement(leader, bandId, reservationId, 9_000, "EQUAL"); // 3000씩
        JsonNode s = data(recalculate(leader, bandId, reservationId, "{\"totalAmount\":12000}"));

        assertThat(s.get("totalAmount").asInt()).isEqualTo(12_000);
        assertThat(s.get("splitType").asText()).isEqualTo("EQUAL");
        assertThat(s.get("shareCount").asInt()).isEqualTo(3);
        assertThat(sumOfShares(s)).isEqualTo(12_000);
        s.get("shares").forEach(sh -> assertThat(sh.get("amount").asInt()).isEqualTo(4_000));
    }

    // --- 권한 ----------------------------------------------------------

    /** 일정 등록자도 밴드장도 아닌 멤버는 정산을 만들거나 재계산할 수 없다(403). */
    @Test
    void only_owner_or_leader_can_create_or_recalculate() {
        String leader = signup("stl-perm-l@band.app", "리더");
        String other = signup("stl-perm-o@band.app", "제3자");
        long bandId = createBand(leader, "쏜애플");
        join(other, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);

        ResponseEntity<String> create = post(settlementPath(bandId, reservationId),
                "{\"totalAmount\":10000,\"splitType\":\"EQUAL\"}", other);
        assertThat(create.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(create)).isEqualTo("NOT_SETTLEMENT_MANAGER");

        createSettlement(leader, bandId, reservationId, 10_000, "EQUAL");
        ResponseEntity<String> recalc = post(settlementPath(bandId, reservationId) + "/recalculate", "{}", other);
        assertThat(recalc.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(recalc)).isEqualTo("NOT_SETTLEMENT_MANAGER");
    }

    /** 밴드장이 아니어도 일정 등록자 본인이면 정산을 만들 수 있다. */
    @Test
    void reservation_owner_who_is_not_leader_can_create() {
        String leader = signup("stl-own-l@band.app", "리더");
        String owner = signup("stl-own-m@band.app", "등록자");
        long bandId = createBand(leader, "혁오둘");
        join(owner, issueInvite(leader, bandId, null));
        setPermission(leader, bandId, "ANYONE");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(owner, bandId, roomId, T10, T13);

        ResponseEntity<String> res = post(settlementPath(bandId, reservationId),
                "{\"totalAmount\":10000,\"splitType\":\"EQUAL\"}", owner);
        assertThat(res.getStatusCode().value()).isEqualTo(201);
        assertThat(sumOfShares(data(res))).isEqualTo(10_000);
    }

    /** 같은 일정에 두 번 만들면 두 번째는 409. */
    @Test
    void duplicate_create_is_conflict() {
        String leader = signup("stl-dup-l@band.app", "리더");
        long bandId = createBand(leader, "국카스텐둘");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);

        createSettlement(leader, bandId, reservationId, 10_000, "EQUAL");
        ResponseEntity<String> again = post(settlementPath(bandId, reservationId),
                "{\"totalAmount\":20000,\"splitType\":\"EQUAL\"}", leader);
        assertThat(again.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(again)).isEqualTo("SETTLEMENT_ALREADY_EXISTS");
    }

    // --- 납부 체크 ----------------------------------------------------

    /** 납부 체크는 본인 몫만. 타인 몫 변경은 403, 분담 대상이 아니면 404. */
    @Test
    void marking_paid_is_self_only() {
        String leader = signup("stl-pay-l@band.app", "리더");
        String m1 = signup("stl-pay-1@band.app", "멤버1");
        long bandId = createBand(leader, "잔나비둘");
        join(m1, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        long leaderId = myUserId(leader);
        long m1Id = myUserId(m1);
        setAttendance(leader, bandId, reservationId, leaderId, "ATTENDING");
        createSettlement(leader, bandId, reservationId, 10_000, "ATTENDEES_ONLY"); // 리더만 대상

        // m1 이 리더의 몫을 건드림 → 403
        ResponseEntity<String> forbidden = put(
                settlementPath(bandId, reservationId) + "/shares/" + leaderId, "{\"paid\":true}", m1);
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(forbidden)).isEqualTo("NOT_SETTLEMENT_SHARE_OWNER");

        // m1 은 분담 대상이 아님(불참) → 본인 몫 체크해도 404
        ResponseEntity<String> notRecipient = put(
                settlementPath(bandId, reservationId) + "/shares/" + m1Id, "{\"paid\":true}", m1);
        assertThat(notRecipient.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(notRecipient)).isEqualTo("SETTLEMENT_SHARE_NOT_FOUND");

        // 리더가 본인 몫 체크 → 200, 집계 반영
        JsonNode s = data(markPaid(leader, bandId, reservationId, leaderId, true));
        assertThat(shareOf(s, leaderId).get("paid").asBoolean()).isTrue();
        assertThat(s.get("paidCount").asInt()).isEqualTo(1);
        assertThat(s.get("outstandingAmount").asInt()).isZero();

        // 체크 취소 → paidAt 도 사라진다
        JsonNode after = data(markPaid(leader, bandId, reservationId, leaderId, false));
        assertThat(shareOf(after, leaderId).get("paid").asBoolean()).isFalse();
        assertThat(shareOf(after, leaderId).get("paidAt").isNull()).isTrue();
    }

    /**
     * 납부 체크와 재계산이 같은 정산에서 동시에 돌아도(재계산이 그 멤버의 몫을 지웠다 되살리는 사이에
     * 납부 체크가 끼어들어도) 크래시(500) 없이, 각 요청은 깨끗한 상태를 본다 — 납부 체크는 200(몫이
     * 있을 때) 또는 404 `SETTLEMENT_SHARE_NOT_FOUND`(그 순간 몫이 없을 때), 재계산은 200.
     * 마지막에 정산을 안정 상태로 되돌리면 몫 합계는 총액과 일치한다. (markPaid 가 recalculate 와
     * 같은 정산 행 락을 잡아 직렬화되는지 검증.)
     */
    @Test
    void concurrent_mark_paid_and_recalculate_never_corrupt_state() throws Exception {
        String leader = signup("stl-cc-l@band.app", "리더");
        String m1 = signup("stl-cc-1@band.app", "멤버1");
        String m2 = signup("stl-cc-2@band.app", "멤버2");
        long bandId = createBand(leader, "혁오씨씨");
        join(m1, issueInvite(leader, bandId, null));
        join(m2, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        long leaderId = myUserId(leader);
        long m1Id = myUserId(m1);
        long m2Id = myUserId(m2);
        setAttendance(leader, bandId, reservationId, leaderId, "ATTENDING");
        setAttendance(m1, bandId, reservationId, m1Id, "ATTENDING");
        setAttendance(m2, bandId, reservationId, m2Id, "ATTENDING");
        createSettlement(leader, bandId, reservationId, 9_000, "ATTENDEES_ONLY"); // 3000 * 3

        String sharePath = settlementPath(bandId, reservationId) + "/shares/" + m1Id;
        String recalcPath = settlementPath(bandId, reservationId) + "/recalculate";
        String m1AttPath = "/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/attendances/" + m1Id;

        int rounds = 12;
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            // m1 은 자기 몫을 껐다 켰다 한다
            tasks.add(() -> put(sharePath, "{\"paid\":true}", m1).getStatusCode().value());
            tasks.add(() -> put(sharePath, "{\"paid\":false}", m1).getStatusCode().value());
            // 매니저는 m1 의 참석을 뒤집으며 재계산 → m1 몫이 삭제/재생성을 반복한다
            tasks.add(() -> {
                put(m1AttPath, "{\"status\":\"ABSENT\"}", m1);
                return post(recalcPath, "{}", leader).getStatusCode().value();
            });
            tasks.add(() -> {
                put(m1AttPath, "{\"status\":\"ATTENDING\"}", m1);
                return post(recalcPath, "{}", leader).getStatusCode().value();
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Integer> codes;
        try {
            codes = pool.invokeAll(tasks).stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).toList();
        } finally {
            pool.shutdownNow();
        }

        // 어떤 요청도 500 이 아니고, 납부 체크는 200/404, 재계산은 200 만 나온다.
        assertThat(codes).allSatisfy(c -> assertThat(c).isIn(200, 404));

        // 안정 상태로 되돌리고 불변식 확인: 몫 합계 = 총액.
        put(m1AttPath, "{\"status\":\"ATTENDING\"}", m1);
        JsonNode s = data(recalculate(leader, bandId, reservationId, "{}"));
        assertThat(s.get("shareCount").asInt()).isEqualTo(3);
        assertThat(sumOfShares(s)).isEqualTo(9_000);
    }

    // --- 격리 / 조회 -------------------------------------------------

    /** 정산이 없으면 404. 타 밴드 경로의 일정은 404, 비멤버는 403. */
    @Test
    void settlement_is_isolated_between_bands() {
        String alice = signup("stl-iso-a@band.app", "앨리스");
        String bob = signup("stl-iso-b@band.app", "밥");
        long aliceBand = createBand(alice, "앨리스밴드");
        long bobBand = createBand(bob, "밥밴드");
        long aliceRoom = createRoom(alice, aliceBand, "{\"name\":\"방\"}");
        long aliceReservation = createReservation(alice, aliceBand, aliceRoom, T10, T13);

        ResponseEntity<String> beforeCreate = get(settlementPath(aliceBand, aliceReservation), alice);
        assertThat(beforeCreate.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(beforeCreate)).isEqualTo("SETTLEMENT_NOT_FOUND");

        createSettlement(alice, aliceBand, aliceReservation, 10_000, "EQUAL");

        ResponseEntity<String> nonMember = get(settlementPath(aliceBand, aliceReservation), bob);
        assertThat(nonMember.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(nonMember)).isEqualTo("NOT_BAND_MEMBER");

        ResponseEntity<String> crossBand = get(settlementPath(bobBand, aliceReservation), bob);
        assertThat(crossBand.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(crossBand)).isEqualTo("RESERVATION_NOT_FOUND");
    }

    // --- 헬퍼 -------------------------------------------------------

    private String settlementPath(long bandId, long reservationId) {
        return "/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/settlement";
    }

    private ResponseEntity<String> createSettlement(String token, long bandId, long reservationId,
                                                    int total, String splitType) {
        ResponseEntity<String> res = post(settlementPath(bandId, reservationId),
                "{\"totalAmount\":" + total + ",\"splitType\":\"" + splitType + "\"}", token);
        if (res.getStatusCode().value() != 201) {
            throw new IllegalStateException("정산 생성 실패: " + res.getBody());
        }
        return res;
    }

    private ResponseEntity<String> recalculate(String token, long bandId, long reservationId, String body) {
        ResponseEntity<String> res = post(settlementPath(bandId, reservationId) + "/recalculate", body, token);
        if (res.getStatusCode().value() != 200) {
            throw new IllegalStateException("정산 재계산 실패: " + res.getBody());
        }
        return res;
    }

    private ResponseEntity<String> markPaid(String token, long bandId, long reservationId,
                                            long userId, boolean paid) {
        ResponseEntity<String> res = put(settlementPath(bandId, reservationId) + "/shares/" + userId,
                "{\"paid\":" + paid + "}", token);
        if (res.getStatusCode().value() != 200) {
            throw new IllegalStateException("납부 체크 실패: " + res.getBody());
        }
        return res;
    }

    private void setAttendance(String token, long bandId, long reservationId, long userId, String status) {
        ResponseEntity<String> res = put(
                "/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/attendances/" + userId,
                "{\"status\":\"" + status + "\"}", token);
        if (res.getStatusCode().value() != 200) {
            throw new IllegalStateException("참석 상태 변경 실패: " + res.getBody());
        }
    }

    private static int sumOfShares(JsonNode settlement) {
        int sum = 0;
        for (JsonNode sh : settlement.get("shares")) {
            sum += sh.get("amount").asInt();
        }
        return sum;
    }

    private static JsonNode shareOf(JsonNode settlement, long userId) {
        for (JsonNode sh : settlement.get("shares")) {
            if (sh.get("userId").asLong() == userId) {
                return sh;
            }
        }
        throw new IllegalStateException("정산에 userId=" + userId + " 몫 없음: " + settlement);
    }

    private static boolean hasShare(JsonNode settlement, long userId) {
        for (JsonNode sh : settlement.get("shares")) {
            if (sh.get("userId").asLong() == userId) {
                return true;
            }
        }
        return false;
    }
}
