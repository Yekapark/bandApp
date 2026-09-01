package com.yeka.bandapp.recurring;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.recurring.service.RecurringRuleService;
import com.yeka.bandapp.reservation.entity.Reservation;
import com.yeka.bandapp.reservation.repository.ReservationAttendanceRepository;
import com.yeka.bandapp.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회차 연장 배치의 핵심 동작. 스케줄러를 기다리지 않고 {@link RecurringRuleService#extendRule}을 직접
 * 호출해 검증한다({@code WithdrawnUserPurgeJobTest}와 같은 방식).
 *
 * <p>등록 시 이미 지평선까지 회차가 차므로, "아직 안 만든 미래분"을 흉내 내려고 뒤쪽 회차 몇 건을
 * 하드 삭제한 뒤 연장이 그만큼 다시 채우는지 본다.
 */
class RecurringExtensionJobTest extends RecurringApiSupport {

    @Autowired
    RecurringRuleService recurringRuleService;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    ReservationAttendanceRepository attendanceRepository;

    /**
     * 회차를 raw 로 하드 삭제해 "아직 안 만든 미래분"을 흉내 낸다. Phase 6 부터 회차마다
     * PENDING 참석 행이 딸리므로(FK, cascade 없음) 그 자식 행부터 지운다. 운영엔 회차 하드 삭제
     * 경로가 없다(규칙·회차 삭제는 soft cancel).
     */
    private void hardDeleteOccurrences(List<Reservation> occurrences) {
        for (Reservation r : occurrences) {
            attendanceRepository.deleteAll(attendanceRepository.findByReservationId(r.getId()));
        }
        reservationRepository.deleteAll(occurrences);
    }

    @Test
    void extend_fills_missing_future_occurrences_and_is_idempotent() {
        String leader = signup("rec-job-l@band.app", "리더");
        long bandId = createBand(leader, "혁오");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        LocalDate firstDate = today().plusDays(1);
        long ruleId = createRule(leader, bandId, ruleBody(
                roomId, "WEEKLY", firstDate.getDayOfWeek(), "15:00", "18:00", firstDate, null));

        List<Reservation> occ = reservationRepository.findByRecurringRuleIdOrderByStartAtAsc(ruleId);
        int full = occ.size();
        assertThat(full).isGreaterThanOrEqualTo(4);

        // 뒤쪽 3건을 지운다 = "아직 만들지 않은 미래분".
        hardDeleteOccurrences(occ.subList(full - 3, full));
        assertThat(reservationRepository.findByRecurringRuleIdOrderByStartAtAsc(ruleId)).hasSize(full - 3);

        int created = recurringRuleService.extendRule(ruleId);
        assertThat(created).isEqualTo(3);
        assertThat(reservationRepository.findByRecurringRuleIdOrderByStartAtAsc(ruleId)).hasSize(full);

        // 다시 호출해도 늘지 않는다.
        assertThat(recurringRuleService.extendRule(ruleId)).isZero();
        assertThat(reservationRepository.findByRecurringRuleIdOrderByStartAtAsc(ruleId)).hasSize(full);
    }

    @Test
    void extend_never_generates_past_the_end_date() {
        String leader = signup("rec-job-end-l@band.app", "리더");
        long bandId = createBand(leader, "잔나비");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        LocalDate firstDate = today().plusDays(1);
        LocalDate endDate = today().plusWeeks(2);
        long ruleId = createRule(leader, bandId, ruleBody(
                roomId, "WEEKLY", firstDate.getDayOfWeek(), "15:00", "18:00", firstDate, endDate));

        List<Reservation> occ = reservationRepository.findByRecurringRuleIdOrderByStartAtAsc(ruleId);
        assertThat(occ).isNotEmpty();
        for (Reservation r : occ) {
            assertThat(r.getStartAt().atZone(SEOUL).toLocalDate()).isBeforeOrEqualTo(endDate);
        }

        int fullCount = occ.size();
        hardDeleteOccurrences(occ.subList(fullCount - 1, fullCount));
        int created = recurringRuleService.extendRule(ruleId);
        assertThat(created).isEqualTo(1);   // 지운 1건만 복구, endDate 너머로는 안 만든다

        List<Reservation> after = reservationRepository.findByRecurringRuleIdOrderByStartAtAsc(ruleId);
        assertThat(after).hasSize(fullCount);
        for (Reservation r : after) {
            assertThat(r.getStartAt().atZone(SEOUL).toLocalDate()).isBeforeOrEqualTo(endDate);
        }
    }

    /** 규칙이 삭제된 뒤 연장을 호출해도 아무 회차도 만들지 않는다. */
    @Test
    void extend_does_nothing_for_a_deleted_rule() {
        String leader = signup("rec-job-del-l@band.app", "리더");
        long bandId = createBand(leader, "새소년");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        LocalDate firstDate = today().plusDays(1);
        long ruleId = createRule(leader, bandId, ruleBody(
                roomId, "WEEKLY", firstDate.getDayOfWeek(), "15:00", "18:00", firstDate, null));

        JsonNode occ = ruleDetail(leader, bandId, ruleId).get("occurrences");
        long anyFuture = occ.get(occ.size() - 1).get("id").asLong();
        attendanceRepository.deleteAll(attendanceRepository.findByReservationId(anyFuture));
        reservationRepository.deleteById(anyFuture);

        delete("/api/v1/bands/" + bandId + "/recurring-rules/" + ruleId, leader);

        assertThat(recurringRuleService.extendRule(ruleId)).isZero();
    }
}
