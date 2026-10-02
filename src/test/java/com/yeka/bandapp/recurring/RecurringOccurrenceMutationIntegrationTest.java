package com.yeka.bandapp.recurring;

import com.yeka.bandapp.recurring.service.RecurringRuleService;
import com.yeka.bandapp.reservation.entity.Reservation;
import com.yeka.bandapp.reservation.entity.ReservationStatus;
import com.yeka.bandapp.reservation.repository.ReservationAttendanceRepository;
import com.yeka.bandapp.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringOccurrenceMutationIntegrationTest extends RecurringApiSupport {
    @Autowired ReservationRepository reservations;
    @Autowired ReservationAttendanceRepository attendances;
    @Autowired RecurringRuleService rules;

    @ParameterizedTest
    @ValueSource(ints = {-1, 400})
    void moving_the_last_occurrence_does_not_recreate_its_original_slot(int days) {
        String leader = signup("slot-owner@band.app", "owner");
        long band = createBand(leader, "original slots");
        long room = createRoom(leader, band, "{\"name\":\"room\"}");
        long rule = weeklyRule(leader, band, room);
        var before = reservations.findByRecurringRuleIdOrderByStartAtAsc(rule);
        var last = before.getLast();
        Instant moved = last.getStartAt().plus(Duration.ofDays(days));

        move(leader, band, last, room, moved);

        assertThat(rules.extendRule(rule)).isZero();
        assertThat(rules.extendRule(rule)).isZero();
        assertThat(reservations.findByRecurringRuleIdOrderByStartAtAsc(rule)).hasSize(before.size());
        var saved = reservations.findById(last.getId()).orElseThrow();
        assertThat(saved.getStartAt()).isEqualTo(moved);
        assertThat(saved.getOriginalStartAt()).isEqualTo(last.getStartAt());
        assertThat(usageCount(leader, band, room)).isEqualTo(before.size());
    }

    @Test
    void an_occurrence_moved_far_ahead_does_not_stop_normal_extension() {
        String leader = signup("slot-owner@band.app", "owner");
        long band = createBand(leader, "continue extension");
        long room = createRoom(leader, band, "{\"name\":\"room\"}");
        long rule = weeklyRule(leader, band, room);
        var rows = reservations.findByRecurringRuleIdOrderByStartAtAsc(rule);
        assertThat(rows.size()).isGreaterThan(4);
        var first = rows.getFirst();
        Instant moved = first.getStartAt().plus(Duration.ofDays(400));
        move(leader, band, first, room, moved);
        // 기존 연장 테스트와 동일: 마지막 3개를 아직 생성되지 않은 미래 회차로 가정한다.
        for (var row : rows.subList(rows.size() - 3, rows.size())) {
            attendances.deleteAll(attendances.findByReservationId(row.getId()));
            reservations.deleteById(row.getId());
        }

        assertThat(rules.extendRule(rule)).isEqualTo(3);
        assertThat(rules.extendRule(rule)).isZero();
        assertThat(reservations.findByRecurringRuleIdOrderByStartAtAsc(rule)).hasSize(rows.size());
        assertThat(reservations.findById(first.getId()).orElseThrow().getStartAt()).isEqualTo(moved);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recurring_siblings_can_overlap_in_the_same_or_different_room(boolean otherRoom) {
        String leader = signup("slot-owner@band.app", "owner");
        long band = createBand(leader, "overlap is allowed");
        long room = createRoom(leader, band, "{\"name\":\"room\"}");
        long rule = weeklyRule(leader, band, room);
        var rows = reservations.findByRecurringRuleIdOrderByStartAtAsc(rule);
        var first = rows.getFirst();
        var next = rows.get(1);
        long destination = otherRoom ? createRoom(leader, band, "{\"name\":\"other\"}") : room;
        var response = put("/api/v1/bands/" + band + "/reservations/" + first.getId(),
                reservationBody(destination, next.getStartAt().toString(), next.getEndAt().toString()), leader);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(overlapsOf(response)).anySatisfy(overlap ->
                assertThat(overlap.get("id").asLong()).isEqualTo(next.getId()));
        var saved = reservations.findById(first.getId()).orElseThrow();
        assertThat(saved.getStartAt()).isEqualTo(next.getStartAt());
        assertThat(saved.getOriginalStartAt()).isEqualTo(first.getStartAt());
        assertThat(saved.getRoomId()).isEqualTo(destination);
        assertThat(rules.extendRule(rule)).isZero();
        assertThat(reservations.findByRecurringRuleIdOrderByStartAtAsc(rule)).hasSize(rows.size());
    }

    @Test
    void deleting_a_rule_preserves_the_past_settlement_and_paid_record() {
        String leader = signup("history-owner@band.app", "owner");
        String member = signup("history-member@band.app", "member");
        String third = signup("history-third@band.app", "third");
        long band = createBand(leader, "settlement history");
        String code = issueInvite(leader, band, null);
        assertThat(join(member, code).getStatusCode().value()).isEqualTo(200);
        assertThat(join(third, code).getStatusCode().value()).isEqualTo(200);
        long room = createRoom(leader, band, "{\"name\":\"room\"}");
        var start = today().minusWeeks(2);
        long rule = createRule(leader, band,
                ruleBody(room, "WEEKLY", start.getDayOfWeek(), "15:00", "18:00", start, null));
        seedPastOccurrences(leader, band, room, rule, 1);
        var past = reservations.findByRecurringRuleIdOrderByStartAtAsc(rule).getFirst();
        assertThat(past.getStartAt()).isBefore(Instant.now());
        String path = "/api/v1/bands/" + band + "/reservations/" + past.getId() + "/settlement";
        assertThat(post(path, "{\"totalAmount\":10000,\"splitType\":\"EQUAL\"}", leader)
                .getStatusCode().value()).isEqualTo(201);
        assertThat(put(path + "/shares/" + myUserId(member), "{\"paid\":true}", member)
                .getStatusCode().value()).isEqualTo(200);
        var before = data(get(path, leader));

        assertThat(delete("/api/v1/bands/" + band + "/recurring-rules/" + rule, leader)
                .getStatusCode().value()).isEqualTo(204);

        assertThat(data(get(path, leader))).isEqualTo(before);
        assertThat(before.get("totalAmount").asInt()).isEqualTo(10000);
        assertThat(before.get("paidCount").asInt()).isEqualTo(1);
        assertThat(reservations.findById(past.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    private long weeklyRule(String leader, long band, long room) {
        var start = today().plusDays(1);
        return createRule(leader, band,
                ruleBody(room, "WEEKLY", start.getDayOfWeek(), "15:00", "18:00", start, null));
    }

    private void move(String leader, long band, Reservation reservation, long room, Instant start) {
        Instant end = start.plus(Duration.between(reservation.getStartAt(), reservation.getEndAt()));
        assertThat(put("/api/v1/bands/" + band + "/reservations/" + reservation.getId(),
                reservationBody(room, start.toString(), end.toString()), leader).getStatusCode().value()).isEqualTo(200);
    }
}
