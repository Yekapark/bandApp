package com.yeka.bandapp.settlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.reservation.ReservationApiSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 밴드 정산 목록 — 일정을 하나씩 열어보지 않고 내 미납을 한눈에 보는 화면용 API.
 *
 * <p>정산 생성·재계산·납부 체크 자체는 {@link SettlementIntegrationTest} 가 본다. 여기서는 목록에
 * 내 몫·납부 여부가 제대로 붙는지, 미납 합계, 페이징, 밴드 격리만 확인한다.
 */
class BandSettlementListIntegrationTest extends ReservationApiSupport {

    private String listPath(long bandId) {
        return "/api/v1/bands/" + bandId + "/settlements";
    }

    private JsonNode list(String token, long bandId, String query) {
        ResponseEntity<String> res = get(listPath(bandId) + query, token);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        return data(res);
    }

    private long createSettlement(String token, long bandId, long reservationId, int total) {
        ResponseEntity<String> res = post(
                "/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/settlement",
                "{\"totalAmount\":" + total + ",\"splitType\":\"EQUAL\"}", token);
        assertThat(res.getStatusCode().value()).isEqualTo(201);
        return data(res).get("settlementId").asLong();
    }

    private void markPaid(String token, long bandId, long reservationId, long userId) {
        ResponseEntity<String> res = put(
                "/api/v1/bands/" + bandId + "/reservations/" + reservationId
                        + "/settlement/shares/" + userId,
                "{\"paid\":true}", token);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void list_carries_my_share_and_sums_what_i_still_owe() {
        String leader = signup("bstl-a-l@band.app", "리더");
        String member = signup("bstl-a-m@band.app", "멤버");
        long bandId = createBand(leader, "혁오");
        join(member, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"1번방\"}");
        long r1 = createReservation(leader, bandId, roomId, T10, T13);
        long r2 = createReservation(leader, bandId, roomId, T13, T16);

        createSettlement(leader, bandId, r1, 10_000);   // 2명 → 5,000씩
        createSettlement(leader, bandId, r2, 30_000);   // 2명 → 15,000씩

        JsonNode mine = list(member, bandId, "");

        assertThat(mine.get("settlements")).hasSize(2);
        // 최신순 — 나중에 만든 정산이 먼저.
        JsonNode first = mine.get("settlements").get(0);
        assertThat(first.get("reservationId").asLong()).isEqualTo(r2);
        assertThat(first.get("totalAmount").asInt()).isEqualTo(30_000);
        assertThat(first.get("roomName").asText()).isEqualTo("1번방");
        assertThat(first.get("shareCount").asInt()).isEqualTo(2);
        assertThat(first.get("myAmount").asInt()).isEqualTo(15_000);
        assertThat(first.get("myPaid").asBoolean()).isFalse();

        assertThat(mine.get("myOutstandingTotal").asInt()).isEqualTo(20_000);
    }

    @Test
    void paying_my_share_removes_it_from_the_outstanding_total() {
        String leader = signup("bstl-b-l@band.app", "리더");
        String member = signup("bstl-b-m@band.app", "멤버");
        long bandId = createBand(leader, "국카스텐");
        join(member, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"1번방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        createSettlement(leader, bandId, reservationId, 10_000);

        assertThat(list(member, bandId, "").get("myOutstandingTotal").asInt()).isEqualTo(5_000);

        markPaid(member, bandId, reservationId, myUserId(member));

        JsonNode after = list(member, bandId, "");
        assertThat(after.get("myOutstandingTotal").asInt()).isZero();
        assertThat(after.get("settlements").get(0).get("myPaid").asBoolean()).isTrue();
        assertThat(after.get("settlements").get(0).get("paidCount").asInt()).isEqualTo(1);
    }

    @Test
    void list_pages_by_cursor_newest_first() {
        String leader = signup("bstl-c-l@band.app", "리더");
        long bandId = createBand(leader, "새소년");
        long roomId = createRoom(leader, bandId, "{\"name\":\"1번방\"}");
        long r1 = createReservation(leader, bandId, roomId, T10, T13);
        long r2 = createReservation(leader, bandId, roomId, T13, T16);
        createSettlement(leader, bandId, r1, 10_000);
        createSettlement(leader, bandId, r2, 20_000);

        JsonNode first = list(leader, bandId, "?size=1");
        assertThat(first.get("settlements")).hasSize(1);
        assertThat(first.get("settlements").get(0).get("reservationId").asLong()).isEqualTo(r2);
        assertThat(first.get("nextCursor").isNull()).isFalse();

        JsonNode second =
                list(leader, bandId, "?size=1&cursor=" + first.get("nextCursor").asLong());
        assertThat(second.get("settlements").get(0).get("reservationId").asLong()).isEqualTo(r1);
        assertThat(second.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void other_bands_settlements_are_not_listed_and_non_members_are_refused() {
        String leader = signup("bstl-d-l@band.app", "리더");
        String stranger = signup("bstl-d-x@band.app", "낯선이");
        long bandId = createBand(leader, "잔나비");
        long otherBandId = createBand(stranger, "남의밴드");
        long roomId = createRoom(leader, bandId, "{\"name\":\"1번방\"}");
        createSettlement(leader, bandId, createReservation(leader, bandId, roomId, T10, T13), 10_000);

        // 남의 밴드 목록에는 안 뜬다.
        assertThat(list(stranger, otherBandId, "").get("settlements")).isEmpty();

        // 비멤버는 아예 못 본다.
        assertThat(get(listPath(bandId), stranger).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void settlement_i_am_not_part_of_shows_null_share_and_adds_nothing_to_my_total() {
        String leader = signup("bstl-e-l@band.app", "리더");
        String member = signup("bstl-e-m@band.app", "멤버");
        long bandId = createBand(leader, "쏜애플");
        long roomId = createRoom(leader, bandId, "{\"name\":\"1번방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        // 리더 혼자일 때 정산을 만든 뒤 멤버가 합류 → 멤버는 이 정산의 분담 대상이 아니다.
        createSettlement(leader, bandId, reservationId, 10_000);
        join(member, issueInvite(leader, bandId, null));

        JsonNode mine = list(member, bandId, "");

        assertThat(mine.get("settlements")).hasSize(1);
        assertThat(mine.get("settlements").get(0).get("myAmount").isNull()).isTrue();
        assertThat(mine.get("settlements").get(0).get("myPaid").isNull()).isTrue();
        assertThat(mine.get("myOutstandingTotal").asInt()).isZero();
    }
}
