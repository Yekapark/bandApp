package com.yeka.bandapp.reservation;

import com.yeka.bandapp.band.service.BandPurgeService;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.recurring.RecurringApiSupport;
import com.yeka.bandapp.recurring.service.RecurringRuleService;
import com.yeka.bandapp.reservation.dto.CreateSetlistItemRequest;
import com.yeka.bandapp.reservation.dto.ReorderSetlistRequest;
import com.yeka.bandapp.reservation.dto.UpdateReservationRequest;
import com.yeka.bandapp.reservation.dto.UpdateSetlistItemRequest;
import com.yeka.bandapp.reservation.entity.ReservationStatus;
import com.yeka.bandapp.reservation.entity.SetlistItem;
import com.yeka.bandapp.reservation.repository.ReservationAttendanceRepository;
import com.yeka.bandapp.reservation.repository.ReservationRepository;
import com.yeka.bandapp.reservation.repository.SetlistItemRepository;
import com.yeka.bandapp.reservation.service.ReservationService;
import com.yeka.bandapp.reservation.service.SetlistService;
import com.yeka.bandapp.support.ConcurrentTransactions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleMutationConcurrencyIntegrationTest extends RecurringApiSupport {
    @Autowired ReservationRepository reservations;
    @Autowired ReservationAttendanceRepository attendances;
    @Autowired ReservationService reservationService;
    @Autowired RecurringRuleService rules;
    @Autowired SetlistService setlists;
    @Autowired SetlistItemRepository items;
    @Autowired BandPurgeService purge;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rule_deletion_and_individual_cancellation_decrement_once(boolean ruleFirst) throws Exception {
        Fixture f = fixture();
        long standalone = createReservation(f.token, f.band, f.room, T10, T13);
        Runnable individual = () -> reservationService.cancel(f.band, f.occurrence, f.owner);
        Runnable all = () -> rules.delete(f.band, f.rule, f.owner);

        assertThat(overlap(ruleFirst ? all : individual, ruleFirst ? individual : all)).isNull();

        assertThat(usageCount(f.token, f.band, f.room)).isEqualTo(1);
        assertThat(reservations.findById(standalone).orElseThrow().getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservations.findById(f.occurrence).orElseThrow().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void deletion_counts_the_new_room_after_a_concurrent_move() throws Exception {
        Fixture f = fixture();
        createReservation(f.token, f.band, f.room, T10, T13);
        long destination = createRoom(f.token, f.band, "{\"name\":\"destination\"}");
        var occurrence = reservations.findById(f.occurrence).orElseThrow();

        assertThat(overlap(() -> reservationService.update(f.band, f.occurrence, f.owner,
                        new UpdateReservationRequest(destination, occurrence.getStartAt(), occurrence.getEndAt(), null, null)),
                () -> rules.delete(f.band, f.rule, f.owner))).isNull();

        assertThat(usageCount(f.token, f.band, f.room)).isEqualTo(1);
        assertThat(usageCount(f.token, f.band, destination)).isZero();
        var saved = reservations.findById(f.occurrence).orElseThrow();
        assertThat(saved.getRoomId()).isEqualTo(destination);
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void deletion_preserves_an_occurrence_moved_to_the_past_while_waiting() throws Exception {
        Fixture f = fixture();
        Instant past = Instant.now().minus(Duration.ofDays(1));

        assertThat(overlap(() -> reservationService.update(f.band, f.occurrence, f.owner,
                        new UpdateReservationRequest(f.room, past, past.plus(Duration.ofHours(3)), null, null)),
                () -> rules.delete(f.band, f.rule, f.owner))).isNull();

        assertThat(usageCount(f.token, f.band, f.room)).isEqualTo(1);
        assertThat(reservations.findById(f.occurrence).orElseThrow().getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void an_update_waiting_for_rule_deletion_cannot_restore_a_cancelled_occurrence() throws Exception {
        Fixture f = fixture();
        var occurrence = reservations.findById(f.occurrence).orElseThrow();

        assertThat(overlap(() -> rules.delete(f.band, f.rule, f.owner),
                () -> reservationService.update(f.band, f.occurrence, f.owner,
                        new UpdateReservationRequest(f.room, occurrence.getStartAt(), occurrence.getEndAt(), 123, null))))
                .isEqualTo(ErrorCode.RESERVATION_NOT_EDITABLE);

        assertThat(usageCount(f.token, f.band, f.room)).isZero();
        assertThat(reservations.findById(f.occurrence).orElseThrow().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    enum Edit { UPDATE, ADD, DELETE }

    @ParameterizedTest
    @CsvSource({"UPDATE,false", "UPDATE,true", "ADD,false", "ADD,true", "DELETE,false", "DELETE,true"})
    void reordering_and_other_edits_use_the_current_list(Edit edit, boolean reorderFirst) throws Exception {
        Fixture f = fixture();
        long a = add(f, "A");
        long b = add(f, "B");
        Runnable reorder = () -> setlists.reorder(f.band, f.occurrence, f.owner, new ReorderSetlistRequest(List.of(b, a)));
        Runnable other = () -> {
            switch (edit) {
                case UPDATE -> setlists.update(f.band, f.occurrence, a, f.owner,
                        new UpdateSetlistItemRequest("A edited", null, null));
                case ADD -> setlists.add(f.band, f.occurrence, f.owner, new CreateSetlistItemRequest("C", null, null));
                case DELETE -> setlists.delete(f.band, f.occurrence, a, f.owner);
            }
        };

        ErrorCode result = overlap(reorderFirst ? reorder : other, reorderFirst ? other : reorder);

        if (!reorderFirst && edit != Edit.UPDATE) {
            assertThat(result).isEqualTo(ErrorCode.SETLIST_REORDER_MISMATCH);
        } else {
            assertThat(result).isNull();
        }
        var saved = items.findByReservationIdOrderByOrderNoAscIdAsc(f.occurrence);
        assertThat(saved).extracting(SetlistItem::getOrderNo).doesNotHaveDuplicates();
        switch (edit) {
            case UPDATE -> {
                assertThat(saved).extracting(SetlistItem::getId).containsExactly(b, a);
                assertThat(saved).extracting(SetlistItem::getTitle).containsExactly("B", "A edited");
                assertThat(saved).extracting(SetlistItem::getOrderNo).containsExactly(1, 2);
            }
            case ADD -> assertThat(saved).extracting(SetlistItem::getTitle)
                    .containsExactlyElementsOf(reorderFirst ? List.of("B", "A", "C") : List.of("A", "B", "C"));
            case DELETE -> assertThat(saved).extracting(SetlistItem::getId).containsExactly(b);
        }
    }

    @Test
    void two_simultaneous_additions_have_distinct_positions() throws Exception {
        Fixture f = fixture();

        assertThat(overlap(() -> add(f, "A"), () -> add(f, "B"))).isNull();

        var saved = items.findByReservationIdOrderByOrderNoAscIdAsc(f.occurrence);
        assertThat(saved).extracting(SetlistItem::getTitle).containsExactly("A", "B");
        assertThat(saved).extracting(SetlistItem::getOrderNo).containsExactly(1, 2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void band_purge_and_song_edit_finish_without_deadlock(boolean purgeFirst) throws Exception {
        Fixture f = fixture();
        long item = add(f, "song");
        Runnable deletion = () -> purge.purge(f.band);
        Runnable edit = () -> setlists.update(f.band, f.occurrence, item, f.owner,
                new UpdateSetlistItemRequest("edited", null, null));

        ErrorCode result = overlap(purgeFirst ? deletion : edit, purgeFirst ? edit : deletion);

        assertThat(result).isEqualTo(purgeFirst ? ErrorCode.RESERVATION_NOT_FOUND : null);
        assertThat(reservations.findById(f.occurrence)).isEmpty();
        assertThat(items.findById(item)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void band_purge_and_rule_extension_use_the_same_lock_order(boolean purgeFirst) throws Exception {
        Fixture f = fixture();
        // 아직 생성하지 않은 회차로 가정하여 연장 작업이 실제 INSERT 와 FK 검증까지 하게 한다.
        attendances.deleteAll(attendances.findByReservationId(f.occurrence));
        reservations.deleteById(f.occurrence);
        Runnable deletion = () -> purge.purge(f.band);
        Runnable extension = () -> rules.extendRule(f.rule);

        assertThat(overlap(purgeFirst ? deletion : extension, purgeFirst ? extension : deletion)).isNull();

        assertThat(reservations.findByRecurringRuleIdOrderByStartAtAsc(f.rule)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from recurring_rules where id = ?", Integer.class, f.rule)).isZero();
    }

    private long add(Fixture f, String title) {
        return setlists.add(f.band, f.occurrence, f.owner, new CreateSetlistItemRequest(title, null, null)).id();
    }

    private ErrorCode overlap(Runnable first, Runnable second) throws Exception {
        return ConcurrentTransactions.overlap(transactions, jdbc, first, second);
    }

    private Fixture fixture() {
        String leader = signup("mutation-owner@band.app", "owner");
        long owner = myUserId(leader);
        long band = createBand(leader, "mutation test");
        long room = createRoom(leader, band, "{\"name\":\"room\"}");
        var start = today().plusDays(1);
        long rule = createRule(leader, band,
                ruleBody(room, "WEEKLY", start.getDayOfWeek(), "15:00", "18:00", start, start));
        long occurrence = reservations.findByRecurringRuleIdOrderByStartAtAsc(rule).getFirst().getId();
        return new Fixture(leader, owner, band, room, rule, occurrence);
    }

    private record Fixture(String token, long owner, long band, long room, long rule, long occurrence) { }
}
