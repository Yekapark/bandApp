package com.yeka.bandapp.reservation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4 완료 기준:
 * <ul>
 *   <li>세 가지 권한 모드(LEADER_ONLY / ANYONE / APPROVAL_REQUIRED)가 각각 의도대로 동작한다.</li>
 *   <li>시간대가 겹치는 일정을 등록해도 정상 저장되며 응답에 겹침 정보가 담긴다.</li>
 * </ul>
 * 여기에 권한(수정/취소/승인)·합주실 usageCount 증감·캘린더 기간 조회·타 밴드 격리·기간 검증을 함께 본다.
 */
class ReservationIntegrationTest extends ReservationApiSupport {

    private static final String SEP_09 = "2026-09-09T00:00:00Z";
    private static final String SEP_11 = "2026-09-11T00:00:00Z";
    private static final String OCT_01 = "2026-10-01T00:00:00Z";
    private static final String OCT_02 = "2026-10-02T00:00:00Z";

    // --- 권한 모드별 초기 status ------------------------------------------------

    /** 완료 기준 ① LEADER_ONLY — 일반 멤버는 등록 불가(403), 밴드장은 즉시 CONFIRMED. */
    @Test
    void leader_only_blocks_member_and_confirms_for_leader() {
        String leader = signup("resv-lo-l@band.app", "리더");
        String member = signup("resv-lo-m@band.app", "멤버");
        long bandId = createBand(leader, "혁오");
        join(member, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"1번방\"}");
        // 기본값이 LEADER_ONLY 라 별도 설정 없음

        ResponseEntity<String> byMember = post("/api/v1/bands/" + bandId + "/reservations",
                reservationBody(roomId, T10, T13), member);
        assertThat(byMember.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(byMember)).isEqualTo("NOT_BAND_LEADER");

        ResponseEntity<String> byLeader = post("/api/v1/bands/" + bandId + "/reservations",
                reservationBody(roomId, T10, T13), leader);
        assertThat(byLeader.getStatusCode().value()).isEqualTo(201);
        assertThat(reservationOf(byLeader).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(overlapsOf(byLeader)).isEmpty();
    }

    /** 완료 기준 ② ANYONE — 일반 멤버도 등록 가능하고 즉시 CONFIRMED. */
    @Test
    void anyone_lets_member_create_confirmed() {
        String leader = signup("resv-any-l@band.app", "리더");
        String member = signup("resv-any-m@band.app", "멤버");
        long bandId = createBand(leader, "잔나비");
        join(member, issueInvite(leader, bandId, null));
        setPermission(leader, bandId, "ANYONE");
        long roomId = createRoom(leader, bandId, "{\"name\":\"연습실\"}");

        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/reservations",
                reservationBody(roomId, T10, T13), member);

        assertThat(res.getStatusCode().value()).isEqualTo(201);
        assertThat(reservationOf(res).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(reservationOf(res).get("roomName").asText()).isEqualTo("연습실");
    }

    /** 완료 기준 ③ APPROVAL_REQUIRED — 멤버 등록은 PENDING, 밴드장 승인으로 CONFIRMED. */
    @Test
    void approval_required_starts_pending_then_leader_approves() {
        String leader = signup("resv-ap-l@band.app", "리더");
        String member = signup("resv-ap-m@band.app", "멤버");
        long bandId = createBand(leader, "국카스텐");
        join(member, issueInvite(leader, bandId, null));
        setPermission(leader, bandId, "APPROVAL_REQUIRED");
        long roomId = createRoom(leader, bandId, "{\"name\":\"큰방\"}");

        long reservationId = data(post("/api/v1/bands/" + bandId + "/reservations",
                reservationBody(roomId, T10, T13), member)).get("reservation").get("id").asLong();
        assertThat(data(get("/api/v1/bands/" + bandId + "/reservations/" + reservationId, member))
                .get("status").asText()).isEqualTo("PENDING");

        // 일반 멤버는 승인 불가
        assertThat(post("/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/approve", null, member)
                .getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> approved = post(
                "/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/approve", null, leader);
        assertThat(approved.getStatusCode().value()).isEqualTo(200);
        assertThat(data(approved).get("status").asText()).isEqualTo("CONFIRMED");

        // 이미 확정된 일정을 다시 승인하면 409
        assertThat(post("/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/approve", null, leader)
                .getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void approval_required_leader_can_reject_and_usage_is_reverted() {
        String leader = signup("resv-rej-l@band.app", "리더");
        String member = signup("resv-rej-m@band.app", "멤버");
        long bandId = createBand(leader, "새소년");
        join(member, issueInvite(leader, bandId, null));
        setPermission(leader, bandId, "APPROVAL_REQUIRED");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        long reservationId = data(post("/api/v1/bands/" + bandId + "/reservations",
                reservationBody(roomId, T10, T13), member)).get("reservation").get("id").asLong();
        assertThat(usageCount(leader, bandId, roomId)).isEqualTo(1);

        ResponseEntity<String> rejected = post(
                "/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/reject", null, leader);
        assertThat(rejected.getStatusCode().value()).isEqualTo(200);
        assertThat(data(rejected).get("status").asText()).isEqualTo("REJECTED");
        assertThat(usageCount(leader, bandId, roomId)).isZero();
    }

    // --- 겹침 경고 -----------------------------------------------------------

    /** 완료 기준 — 겹치는 시간대 등록도 201로 성공하고 overlaps 에 기존 일정이 담긴다. */
    @Test
    void overlapping_reservation_is_saved_and_reported() {
        String leader = signup("resv-ov@band.app", "리더");
        long bandId = createBand(leader, "쏜애플");
        setPermission(leader, bandId, "ANYONE");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        long first = createReservation(leader, bandId, roomId, T10, T13);

        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/reservations",
                reservationBody(roomId, "2026-09-10T12:00:00Z", "2026-09-10T15:00:00Z"), leader);

        assertThat(res.getStatusCode().value()).isEqualTo(201);
        JsonNode overlaps = overlapsOf(res);
        assertThat(overlaps).hasSize(1);
        assertThat(overlaps.get(0).get("id").asLong()).isEqualTo(first);
        assertThat(overlaps.get(0).get("roomName").asText()).isEqualTo("방");
    }

    /** 반열림 구간 — 앞 일정의 종료 == 뒤 일정의 시작이면 겹치지 않는다. */
    @Test
    void adjacent_reservations_do_not_overlap() {
        String leader = signup("resv-adj@band.app", "리더");
        long bandId = createBand(leader, "아도이");
        setPermission(leader, bandId, "ANYONE");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        createReservation(leader, bandId, roomId, T10, T13);

        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/reservations",
                reservationBody(roomId, T13, T16), leader);

        assertThat(res.getStatusCode().value()).isEqualTo(201);
        assertThat(overlapsOf(res)).isEmpty();
    }

    /** 취소된 일정은 겹침 경고에 잡히지 않는다. */
    @Test
    void cancelled_reservation_is_not_reported_as_overlap() {
        String leader = signup("resv-ovc@band.app", "리더");
        long bandId = createBand(leader, "데이먼스이어");
        setPermission(leader, bandId, "ANYONE");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long first = createReservation(leader, bandId, roomId, T10, T13);
        assertThat(delete("/api/v1/bands/" + bandId + "/reservations/" + first, leader)
                .getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/reservations",
                reservationBody(roomId, T10, T13), leader);

        assertThat(res.getStatusCode().value()).isEqualTo(201);
        assertThat(overlapsOf(res)).isEmpty();
    }

    // --- 합주실 usageCount 증감 ---------------------------------------------

    @Test
    void usage_count_increments_on_create_and_reverts_on_cancel_idempotently() {
        String leader = signup("resv-uc@band.app", "리더");
        long bandId = createBand(leader, "실리카겔");
        setPermission(leader, bandId, "ANYONE");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        assertThat(usageCount(leader, bandId, roomId)).isEqualTo(1);

        assertThat(delete("/api/v1/bands/" + bandId + "/reservations/" + reservationId, leader)
                .getStatusCode().value()).isEqualTo(204);
        assertThat(usageCount(leader, bandId, roomId)).isZero();

        // 취소 재호출은 멱등 — 두 번 깎이지 않는다
        assertThat(delete("/api/v1/bands/" + bandId + "/reservations/" + reservationId, leader)
                .getStatusCode().value()).isEqualTo(204);
        assertThat(usageCount(leader, bandId, roomId)).isZero();
    }

    @Test
    void moving_reservation_to_another_room_shifts_usage_count() {
        String leader = signup("resv-move@band.app", "리더");
        long bandId = createBand(leader, "브로콜리너마저");
        setPermission(leader, bandId, "ANYONE");
        long roomA = createRoom(leader, bandId, "{\"name\":\"A방\"}");
        long roomB = createRoom(leader, bandId, "{\"name\":\"B방\"}");

        long reservationId = createReservation(leader, bandId, roomA, T10, T13);
        assertThat(usageCount(leader, bandId, roomA)).isEqualTo(1);

        ResponseEntity<String> res = put("/api/v1/bands/" + bandId + "/reservations/" + reservationId,
                reservationBody(roomB, T10, T13), leader);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(usageCount(leader, bandId, roomA)).isZero();
        assertThat(usageCount(leader, bandId, roomB)).isEqualTo(1);
    }

    // --- 수정 권한 · 재승인 -------------------------------------------------

    @Test
    void only_owner_or_leader_can_update_reservation() {
        String leader = signup("resv-up-l@band.app", "리더");
        String owner = signup("resv-up-o@band.app", "등록자");
        String other = signup("resv-up-x@band.app", "다른멤버");
        long bandId = createBand(leader, "장미여관");
        setPermission(leader, bandId, "ANYONE");
        join(owner, issueInvite(leader, bandId, null));
        join(other, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(owner, bandId, roomId, T10, T13);

        assertThat(put("/api/v1/bands/" + bandId + "/reservations/" + reservationId,
                reservationBody(roomId, T10, T16), other).getStatusCode().value()).isEqualTo(403);
        assertThat(put("/api/v1/bands/" + bandId + "/reservations/" + reservationId,
                reservationBody(roomId, T10, T16), owner).getStatusCode().value()).isEqualTo(200);
        assertThat(put("/api/v1/bands/" + bandId + "/reservations/" + reservationId,
                reservationBody(roomId, T10, T13), leader).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void confirmed_reservation_reverts_to_pending_only_when_time_or_room_changes() {
        String leader = signup("resv-rv-l@band.app", "리더");
        String member = signup("resv-rv-m@band.app", "멤버");
        long bandId = createBand(leader, "혁오투");
        join(member, issueInvite(leader, bandId, null));
        setPermission(leader, bandId, "APPROVAL_REQUIRED");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        long reservationId = data(post("/api/v1/bands/" + bandId + "/reservations",
                reservationBody(roomId, T10, T13), member)).get("reservation").get("id").asLong();
        post("/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/approve", null, leader);

        // 메모만 변경 → CONFIRMED 유지
        ResponseEntity<String> noteOnly = put("/api/v1/bands/" + bandId + "/reservations/" + reservationId,
                reservationBody(roomId, T10, T13, 20000, "주차 정보 추가"), member);
        assertThat(reservationOf(noteOnly).get("status").asText()).isEqualTo("CONFIRMED");

        // 시간 변경 → PENDING 복귀
        ResponseEntity<String> timeChange = put("/api/v1/bands/" + bandId + "/reservations/" + reservationId,
                reservationBody(roomId, T10, T16), member);
        assertThat(reservationOf(timeChange).get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void cancelled_reservation_cannot_be_updated() {
        String leader = signup("resv-cu@band.app", "리더");
        long bandId = createBand(leader, "국카스텐투");
        setPermission(leader, bandId, "ANYONE");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        delete("/api/v1/bands/" + bandId + "/reservations/" + reservationId, leader);

        ResponseEntity<String> res = put("/api/v1/bands/" + bandId + "/reservations/" + reservationId,
                reservationBody(roomId, T10, T16), leader);
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("RESERVATION_NOT_EDITABLE");
    }

    // --- 기간 검증 · 캘린더 · 밴드 격리 -----------------------------------

    @Test
    void end_before_start_is_400() {
        String leader = signup("resv-per@band.app", "리더");
        long bandId = createBand(leader, "쏜애플투");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/reservations",
                reservationBody(roomId, T13, T10), leader);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("INVALID_RESERVATION_PERIOD");
    }

    @Test
    void calendar_query_excludes_out_of_range_reservations() {
        String leader = signup("resv-cal@band.app", "리더");
        long bandId = createBand(leader, "새소년투");
        setPermission(leader, bandId, "ANYONE");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        createReservation(leader, bandId, roomId, T10, T13);

        JsonNode in = data(get("/api/v1/bands/" + bandId + "/reservations?from=" + SEP_09 + "&to=" + SEP_11, leader));
        assertThat(in.get("reservationCount").asInt()).isEqualTo(1);

        JsonNode out = data(get("/api/v1/bands/" + bandId + "/reservations?from=" + OCT_01 + "&to=" + OCT_02, leader));
        assertThat(out.get("reservationCount").asInt()).isZero();
    }

    @Test
    void cancelled_is_hidden_from_calendar_unless_includeInactive() {
        String leader = signup("resv-cal2@band.app", "리더");
        long bandId = createBand(leader, "아도이투");
        setPermission(leader, bandId, "ANYONE");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        delete("/api/v1/bands/" + bandId + "/reservations/" + reservationId, leader);

        assertThat(data(get("/api/v1/bands/" + bandId + "/reservations?from=" + SEP_09 + "&to=" + SEP_11, leader))
                .get("reservationCount").asInt()).isZero();
        assertThat(data(get("/api/v1/bands/" + bandId + "/reservations?from=" + SEP_09 + "&to=" + SEP_11
                + "&includeInactive=true", leader)).get("reservationCount").asInt()).isEqualTo(1);
    }

    @Test
    void other_bands_room_and_reservation_are_not_reachable() {
        String alice = signup("resv-acl-a@band.app", "앨리스");
        String bob = signup("resv-acl-b@band.app", "밥");
        long aliceBand = createBand(alice, "앨리스밴드");
        long bobBand = createBand(bob, "밥밴드");
        long aliceRoom = createRoom(alice, aliceBand, "{\"name\":\"앨리스방\"}");
        long aliceReservation = createReservation(alice, aliceBand, aliceRoom, T10, T13);

        // 밥이 자기 밴드 경로에 앨리스의 roomId 를 끼워 등록 → 404 ROOM_NOT_FOUND
        ResponseEntity<String> crossRoom = post("/api/v1/bands/" + bobBand + "/reservations",
                reservationBody(aliceRoom, T10, T13), bob);
        assertThat(crossRoom.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(crossRoom)).isEqualTo("ROOM_NOT_FOUND");

        // 밥이 앨리스의 일정을 자기 밴드 경로로 조회 → 404 RESERVATION_NOT_FOUND
        ResponseEntity<String> crossResv = get(
                "/api/v1/bands/" + bobBand + "/reservations/" + aliceReservation, bob);
        assertThat(crossResv.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(crossResv)).isEqualTo("RESERVATION_NOT_FOUND");

        // 비멤버의 목록 조회 → 403
        ResponseEntity<String> crossList = get(
                "/api/v1/bands/" + aliceBand + "/reservations?from=" + SEP_09 + "&to=" + SEP_11, bob);
        assertThat(crossList.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(crossList)).isEqualTo("NOT_BAND_MEMBER");
    }
}
