package com.yeka.bandapp.reservation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 6 완료 기준:
 * <ul>
 *   <li>일정 생성 이후 밴드에 합류한 멤버도 참석 응답이 가능하다.</li>
 *   <li>타인의 참석 상태 변경이 403으로 차단된다.</li>
 * </ul>
 * 여기에 일정 상세의 참석 현황·집계 포함, PENDING 초기화, 취소 일정 응답 거부, 타 밴드 격리,
 * 셋리스트 CRUD·재정렬·권한을 함께 본다.
 */
class AttendanceSetlistIntegrationTest extends ReservationApiSupport {

    // --- 참석 체크(RSVP) ---------------------------------------------------

    /**
     * 완료 기준 ① — 일정이 만들어진 뒤 합류한 멤버도 참석 응답을 할 수 있고, 집계에 반영된다.
     */
    @Test
    void member_who_joined_after_creation_can_respond() {
        String leader = signup("rsvp-late-l@band.app", "리더");
        String latecomer = signup("rsvp-late-m@band.app", "지각멤버");
        long bandId = createBand(leader, "혁오");
        long roomId = createRoom(leader, bandId, "{\"name\":\"1번방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);

        // 생성 시점엔 리더 한 명뿐 → 전체 1, 참석 0(모두 PENDING)
        JsonNode before = board(leader, bandId, reservationId);
        assertThat(before.get("memberCount").asInt()).isEqualTo(1);
        assertThat(before.get("attendingCount").asInt()).isZero();
        assertThat(before.get("members").get(0).get("status").asText()).isEqualTo("PENDING");

        assertThat(join(latecomer, issueInvite(leader, bandId, null)).getStatusCode().value()).isEqualTo(200);
        long latecomerId = myUserId(latecomer);

        // 합류 직후: 현황에 새 멤버가 PENDING 으로 나온다(참석 행은 아직 없음)
        JsonNode afterJoin = board(leader, bandId, reservationId);
        assertThat(afterJoin.get("memberCount").asInt()).isEqualTo(2);
        assertThat(entryFor(afterJoin, latecomerId).get("status").asText()).isEqualTo("PENDING");

        // 새 멤버가 본인 상태를 ATTENDING 으로 — 200, 집계 반영
        ResponseEntity<String> res = put(attendancePath(bandId, reservationId, latecomerId),
                "{\"status\":\"ATTENDING\"}", latecomer);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(data(res).get("attendingCount").asInt()).isEqualTo(1);
        assertThat(data(res).get("memberCount").asInt()).isEqualTo(2);
        assertThat(entryFor(data(res), latecomerId).get("status").asText()).isEqualTo("ATTENDING");
        assertThat(entryFor(data(res), latecomerId).get("respondedAt").isNull()).isFalse();
    }

    /** 완료 기준 ② — 타인의 참석 상태는 바꿀 수 없다(403 NOT_ATTENDANCE_OWNER). */
    @Test
    void changing_another_members_attendance_is_forbidden() {
        String leader = signup("rsvp-own-l@band.app", "리더");
        String member = signup("rsvp-own-m@band.app", "멤버");
        long bandId = createBand(leader, "잔나비");
        join(member, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        long leaderId = myUserId(leader);

        ResponseEntity<String> res = put(attendancePath(bandId, reservationId, leaderId),
                "{\"status\":\"ABSENT\"}", member);

        assertThat(res.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("NOT_ATTENDANCE_OWNER");
    }

    /** 일정 생성 시 밴드 멤버 전원이 PENDING 으로 만들어진다. */
    @Test
    void all_members_start_pending_on_creation() {
        String leader = signup("rsvp-init-l@band.app", "리더");
        String m1 = signup("rsvp-init-1@band.app", "멤버1");
        String m2 = signup("rsvp-init-2@band.app", "멤버2");
        long bandId = createBand(leader, "국카스텐");
        join(m1, issueInvite(leader, bandId, null));
        join(m2, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);

        JsonNode b = board(m1, bandId, reservationId);
        assertThat(b.get("memberCount").asInt()).isEqualTo(3);
        assertThat(b.get("attendingCount").asInt()).isZero();
        assertThat(b.get("members")).hasSize(3);
        b.get("members").forEach(e -> {
            assertThat(e.get("status").asText()).isEqualTo("PENDING");
            assertThat(e.get("respondedAt").isNull()).isTrue();
        });
    }

    /** 일정 상세(GET /reservations/{id})에 참석 현황·집계와 셋리스트가 함께 담긴다. */
    @Test
    void reservation_detail_embeds_attendance_and_setlist() {
        String leader = signup("rsvp-detail-l@band.app", "리더");
        String member = signup("rsvp-detail-m@band.app", "멤버");
        long bandId = createBand(leader, "새소년");
        join(member, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        long memberId = myUserId(member);

        put(attendancePath(bandId, reservationId, memberId), "{\"status\":\"ATTENDING\"}", member);
        post(setlistPath(bandId, reservationId), "{\"title\":\"Tomboy\",\"artist\":\"혁오\"}", leader);

        JsonNode detail = data(get("/api/v1/bands/" + bandId + "/reservations/" + reservationId, member));
        // 일정 자체 필드는 그대로 최상위에 있다(목록 응답과 동일)
        assertThat(detail.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(detail.get("id").asLong()).isEqualTo(reservationId);
        // 참석
        assertThat(detail.get("attendance").get("attendingCount").asInt()).isEqualTo(1);
        assertThat(detail.get("attendance").get("memberCount").asInt()).isEqualTo(2);
        // 셋리스트
        assertThat(detail.get("setlist").get("itemCount").asInt()).isEqualTo(1);
        assertThat(detail.get("setlist").get("items").get(0).get("title").asText()).isEqualTo("Tomboy");
    }

    /** 취소된 일정에는 참석 응답을 할 수 없다(409). */
    @Test
    void cannot_respond_to_cancelled_reservation() {
        String leader = signup("rsvp-canc-l@band.app", "리더");
        long bandId = createBand(leader, "쏜애플");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        long leaderId = myUserId(leader);
        delete("/api/v1/bands/" + bandId + "/reservations/" + reservationId, leader);

        ResponseEntity<String> res = put(attendancePath(bandId, reservationId, leaderId),
                "{\"status\":\"ATTENDING\"}", leader);
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("RESERVATION_NOT_EDITABLE");
    }

    /**
     * 같은 멤버가 참석 응답을 동시에 여러 번 눌러도(더블탭) 전부 200이고, 참석 행은 하나만 남는다.
     * upsert 가 Postgres ON CONFLICT 로 원자적이라 유니크 경합에 트랜잭션이 깨지지 않는다.
     */
    @Test
    void concurrent_first_response_by_same_member_is_idempotent() throws Exception {
        String leader = signup("rsvp-cc-l@band.app", "리더");
        String member = signup("rsvp-cc-m@band.app", "멤버");
        long bandId = createBand(leader, "쏜애플둘");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        // 일정 생성 이후 합류 → 이 멤버는 초기 참석 행이 없다(= 첫 응답이 INSERT).
        assertThat(join(member, issueInvite(leader, bandId, null)).getStatusCode().value()).isEqualTo(200);
        long memberId = myUserId(member);

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Integer>> calls = Collections.nCopies(threads, () ->
                    put(attendancePath(bandId, reservationId, memberId), "{\"status\":\"ATTENDING\"}", member)
                            .getStatusCode().value());
            List<Integer> codes = pool.invokeAll(calls).stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).toList();

            assertThat(codes).allSatisfy(c -> assertThat(c).isEqualTo(200));
        } finally {
            pool.shutdownNow();
        }

        JsonNode b = board(leader, bandId, reservationId);
        assertThat(b.get("memberCount").asInt()).isEqualTo(2);
        assertThat(b.get("attendingCount").asInt()).isEqualTo(1);
        assertThat(entryFor(b, memberId).get("status").asText()).isEqualTo("ATTENDING");
    }

    /** 비멤버는 참석 현황을 볼 수 없고(403), 타 밴드 경로의 일정은 404. */
    @Test
    void attendance_is_isolated_between_bands() {
        String alice = signup("rsvp-iso-a@band.app", "앨리스");
        String bob = signup("rsvp-iso-b@band.app", "밥");
        long aliceBand = createBand(alice, "앨리스밴드");
        long bobBand = createBand(bob, "밥밴드");
        long aliceRoom = createRoom(alice, aliceBand, "{\"name\":\"방\"}");
        long aliceReservation = createReservation(alice, aliceBand, aliceRoom, T10, T13);

        ResponseEntity<String> nonMember = get(
                "/api/v1/bands/" + aliceBand + "/reservations/" + aliceReservation + "/attendances", bob);
        assertThat(nonMember.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(nonMember)).isEqualTo("NOT_BAND_MEMBER");

        ResponseEntity<String> crossBand = get(
                "/api/v1/bands/" + bobBand + "/reservations/" + aliceReservation + "/attendances", bob);
        assertThat(crossBand.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(crossBand)).isEqualTo("RESERVATION_NOT_FOUND");
    }

    // --- 셋리스트 --------------------------------------------------------

    /** 곡 추가는 맨 뒤에 붙고(orderNo 1,2,3…), 조회는 순서대로, 재정렬은 1..N 을 다시 매긴다. */
    @Test
    void setlist_crud_and_reorder() {
        String leader = signup("setl-crud-l@band.app", "리더");
        String member = signup("setl-crud-m@band.app", "멤버");
        long bandId = createBand(leader, "실리카겔");
        join(member, issueInvite(leader, bandId, null));
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        String setlist = setlistPath(bandId, reservationId);

        long a = data(post(setlist, "{\"title\":\"곡A\"}", leader)).get("id").asLong();
        // 밴드 멤버 누구나 편집 가능 — 등록자가 아니어도 추가된다
        long b = data(post(setlist, "{\"title\":\"곡B\",\"artist\":\"밴드\"}", member)).get("id").asLong();
        long c = data(post(setlist, "{\"title\":\"곡C\",\"referenceUrl\":\"https://youtu.be/x\"}", leader)).get("id").asLong();

        JsonNode listed = data(get(setlist, member));
        assertThat(listed.get("itemCount").asInt()).isEqualTo(3);
        assertThat(orderNos(listed)).containsExactly(1, 2, 3);
        assertThat(idsInOrder(listed)).containsExactly(a, b, c);

        // 수정
        ResponseEntity<String> updated = put(setlist + "/" + b,
                "{\"title\":\"곡B수정\",\"artist\":\"새밴드\"}", member);
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        assertThat(data(updated).get("title").asText()).isEqualTo("곡B수정");

        // 재정렬: C, A, B
        ResponseEntity<String> reordered = put(setlist + "/reorder",
                "{\"itemIds\":[" + c + "," + a + "," + b + "]}", leader);
        assertThat(reordered.getStatusCode().value()).isEqualTo(200);
        assertThat(idsInOrder(data(reordered))).containsExactly(c, a, b);
        assertThat(orderNos(data(reordered))).containsExactly(1, 2, 3);

        // 삭제
        assertThat(delete(setlist + "/" + a, leader).getStatusCode().value()).isEqualTo(204);
        assertThat(data(get(setlist, leader)).get("itemCount").asInt()).isEqualTo(2);
    }

    /** 재정렬 목록이 현재 항목과 일치하지 않으면 400. */
    @Test
    void reorder_with_mismatched_ids_is_400() {
        String leader = signup("setl-mis-l@band.app", "리더");
        long bandId = createBand(leader, "아도이");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        long reservationId = createReservation(leader, bandId, roomId, T10, T13);
        String setlist = setlistPath(bandId, reservationId);
        long a = data(post(setlist, "{\"title\":\"곡A\"}", leader)).get("id").asLong();
        long b = data(post(setlist, "{\"title\":\"곡B\"}", leader)).get("id").asLong();

        ResponseEntity<String> res = put(setlist + "/reorder", "{\"itemIds\":[" + a + "]}", leader);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("SETLIST_REORDER_MISMATCH");

        // 있지도 않은 id
        ResponseEntity<String> res2 = put(setlist + "/reorder",
                "{\"itemIds\":[" + a + "," + b + ",999999]}", leader);
        assertThat(res2.getStatusCode().value()).isEqualTo(400);
    }

    /** 비멤버는 셋리스트를 못 만지고(403), 다른 일정의 itemId는 404. */
    @Test
    void setlist_is_isolated_between_bands_and_reservations() {
        String alice = signup("setl-iso-a@band.app", "앨리스");
        String bob = signup("setl-iso-b@band.app", "밥");
        long aliceBand = createBand(alice, "A밴드");
        long aliceRoom = createRoom(alice, aliceBand, "{\"name\":\"방\"}");
        long r1 = createReservation(alice, aliceBand, aliceRoom, T10, T13);
        long r2 = createReservation(alice, aliceBand, aliceRoom, T13, T16);
        long item = data(post(setlistPath(aliceBand, r1), "{\"title\":\"곡\"}", alice)).get("id").asLong();

        // 비멤버
        assertThat(post(setlistPath(aliceBand, r1), "{\"title\":\"침입\"}", bob).getStatusCode().value())
                .isEqualTo(403);

        // r1 의 항목을 r2 경로로 수정 → 404
        ResponseEntity<String> wrongReservation = put(
                setlistPath(aliceBand, r2) + "/" + item, "{\"title\":\"엉뚱\"}", alice);
        assertThat(wrongReservation.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(wrongReservation)).isEqualTo("SETLIST_ITEM_NOT_FOUND");
    }

    // --- 헬퍼 ------------------------------------------------------------

    private String attendancePath(long bandId, long reservationId, long userId) {
        return "/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/attendances/" + userId;
    }

    private String setlistPath(long bandId, long reservationId) {
        return "/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/setlist";
    }

    private JsonNode board(String token, long bandId, long reservationId) {
        ResponseEntity<String> res = get(
                "/api/v1/bands/" + bandId + "/reservations/" + reservationId + "/attendances", token);
        if (res.getStatusCode().value() != 200) {
            throw new IllegalStateException("참석 현황 조회 실패: " + res.getBody());
        }
        return data(res);
    }

    private JsonNode entryFor(JsonNode board, long userId) {
        for (JsonNode e : board.get("members")) {
            if (e.get("userId").asLong() == userId) {
                return e;
            }
        }
        throw new IllegalStateException("참석 현황에 userId=" + userId + " 없음: " + board);
    }

    private static java.util.List<Integer> orderNos(JsonNode setlist) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        setlist.get("items").forEach(i -> out.add(i.get("orderNo").asInt()));
        return out;
    }

    private static java.util.List<Long> idsInOrder(JsonNode setlist) {
        java.util.List<Long> out = new java.util.ArrayList<>();
        setlist.get("items").forEach(i -> out.add(i.get("id").asLong()));
        return out;
    }
}
