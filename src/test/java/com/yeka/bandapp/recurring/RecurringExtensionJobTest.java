package com.yeka.bandapp.recurring;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.recurring.service.RecurringRuleService;
import com.yeka.bandapp.reservation.entity.Reservation;
import com.yeka.bandapp.reservation.repository.ReservationAttendanceRepository;
import com.yeka.bandapp.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
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

    @Autowired
    com.yeka.bandapp.plan.service.PlanMutationService planMutationService;

    @Test
    void extend_pauses_while_the_band_is_free_and_resumes_without_backfilling() {
        // LAUNCH_REVIEW B11 — 무료로 내려가면 새 회차를 안 만들고, 프리미엄으로 돌아오면 오늘부터 이어 간다.
        String leader = signup("rec-pause@band.app", "리더");
        long bandId = createBand(leader, "쉬는밴드");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        LocalDate firstDate = today().plusDays(1);
        long ruleId = createRule(leader, bandId, ruleBody(
                roomId, "WEEKLY", firstDate.getDayOfWeek(), "15:00", "18:00", firstDate, null));
        List<Reservation> occ = reservationRepository.findByRecurringRuleIdOrderByStartAtAsc(ruleId);
        int full = occ.size();
        hardDeleteOccurrences(occ.subList(full - 2, full)); // "아직 안 만든 미래분" 2건

        planMutationService.applyRevoke(bandId, java.time.Instant.now()); // FREE 로
        assertThat(recurringRuleService.extendRule(ruleId)).isZero();
        assertThat(reservationRepository.findByRecurringRuleIdOrderByStartAtAsc(ruleId)).hasSize(full - 2);

        makePremium(leader, bandId); // 다시 PREMIUM
        assertThat(recurringRuleService.extendRule(ruleId)).isEqualTo(2);
        assertThat(reservationRepository.findByRecurringRuleIdOrderByStartAtAsc(ruleId)).hasSize(full);
    }

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void resume_after_a_long_pause_does_not_backfill_the_paused_weeks() {
        // QA REC-07 — 마지막 회차가 3주 전에 멈춘 채 PREMIUM 으로 돌아오면, 그 사이 3주는 채우지 않고 오늘부터 만든다.
        String leader = signup("rec-resume@band.app", "리더");
        long bandId = createBand(leader, "복귀밴드");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");
        LocalDate firstDate = today().plusDays(1);
        long ruleId = createRule(leader, bandId, ruleBody(
                roomId, "WEEKLY", firstDate.getDayOfWeek(), "15:00", "18:00", firstDate, null));
        List<Reservation> occ = reservationRepository.findByRecurringRuleIdOrderByStartAtAsc(ruleId);
        int full = occ.size();
        hardDeleteOccurrences(occ.subList(1, full));
        // 남은 첫 회차를 4주 전으로 — "3주 넘게 쉬었다" 를 흉내 낸다. 배치는 원래 슬롯(original_start_at)으로
        // 연장 위치를 정하므로 그것도 함께 옮긴다(시각만 옮기면 "사용자가 옮긴 회차" 가 된다).
        jdbc.update("update reservations set start_at = start_at - interval '28 days', "
                + "end_at = end_at - interval '28 days', original_start_at = original_start_at - interval '28 days' "
                + "where id = ?", occ.get(0).getId());

        planMutationService.applyRevoke(bandId, java.time.Instant.now());
        assertThat(recurringRuleService.extendRule(ruleId)).isZero();
        makePremium(leader, bandId);
        recurringRuleService.extendRule(ruleId);

        java.time.Instant todayStart = today().atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant();
        List<Reservation> after = reservationRepository.findByRecurringRuleIdOrderByStartAtAsc(ruleId);
        assertThat(after.subList(1, after.size()))
                .allSatisfy(r -> assertThat(r.getStartAt()).isAfterOrEqualTo(todayStart));
        assertThat(after).hasSize(full + 1); // 4주 전 1건 + 오늘부터 지평선까지(처음과 같은 날짜들)
        assertThat(recurringRuleService.extendRule(ruleId)).isZero(); // 다시 돌려도 늘지 않는다
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

    /**
     * QA REC-08 — "매월 마지막 주" 규칙이 실제 DB·연장 배치에서도 달이 바뀔 때마다 그 달 마지막 요일로 이어진다
     * (예: 10/31 → 11/28 → 12/26). 날짜를 고정할 수 없어(시계 주입 없음) 지평선 안에 마지막 요일이 두 번
     * 드는 요일을 고르고, 시작일은 과거의 5번째 요일로 둔다(과거 시작일도 회차는 오늘부터).
     */
    @Test
    void monthly_last_week_rule_extends_into_the_next_month_on_its_last_weekday() {
        String leader = signup("rec-job-lastweek@band.app", "리더");
        long bandId = createBand(leader, "마지막주밴드");
        long roomId = createRoom(leader, bandId, "{\"name\":\"방\"}");

        LocalDate today = today();
        DayOfWeek dow = java.util.Arrays.stream(DayOfWeek.values())
                .filter(d -> lastWeekdaysWithin(d, today, today.plusWeeks(8)).size() >= 2)
                .findFirst().orElseThrow();
        LocalDate start = today.minusDays(1);
        while (start.getDayOfWeek() != dow || start.getDayOfMonth() < 29) {
            start = start.minusDays(1);
        }

        long ruleId = createRule(leader, bandId, ruleBody(roomId, "MONTHLY", dow, "19:00", "22:00", start, null));
        List<Reservation> occ = reservationRepository.findByRecurringRuleIdOrderByStartAtAsc(ruleId);
        List<LocalDate> expected = lastWeekdaysWithin(dow, today, today.plusWeeks(8));
        assertThat(datesOf(occ)).isEqualTo(expected);

        // 첫 회차만 남기고 지워 "다음 달 회차는 아직 안 만듦" 을 흉내 낸 뒤 배치가 다음 달 마지막 요일을 채우는지.
        hardDeleteOccurrences(occ.subList(1, occ.size()));
        assertThat(recurringRuleService.extendRule(ruleId)).isEqualTo(expected.size() - 1);
        assertThat(datesOf(reservationRepository.findByRecurringRuleIdOrderByStartAtAsc(ruleId))).isEqualTo(expected);
        assertThat(recurringRuleService.extendRule(ruleId)).isZero();
    }

    private static List<LocalDate> lastWeekdaysWithin(DayOfWeek dow, LocalDate today, LocalDate horizonEnd) {
        return today.withDayOfMonth(1).datesUntil(horizonEnd.plusMonths(1), java.time.Period.ofMonths(1))
                .map(m -> m.with(java.time.temporal.TemporalAdjusters.lastInMonth(dow)))
                .filter(d -> !d.isBefore(today) && !d.isAfter(horizonEnd))
                .toList();
    }

    private static List<LocalDate> datesOf(List<Reservation> occ) {
        return occ.stream().map(r -> r.getStartAt().atZone(SEOUL).toLocalDate()).toList();
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
