package com.yeka.bandapp.recurring;

import com.yeka.bandapp.recurring.entity.RecurringFrequency;
import com.yeka.bandapp.recurring.entity.RecurringRule;
import com.yeka.bandapp.recurring.service.OccurrenceGenerator;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회차 날짜 계산({@link OccurrenceGenerator})의 경계 동작. 컨테이너 없이 도는 순수 단위 테스트다.
 */
class OccurrenceGeneratorTest {

    private static RecurringRule rule(RecurringFrequency freq, DayOfWeek dow,
                                      LocalDate startDate, LocalDate endDate) {
        return RecurringRule.create(1L, 1L, freq, dow,
                LocalTime.of(15, 0), LocalTime.of(18, 0), startDate, endDate, null, null, 1L);
    }

    @Test
    void weekly_steps_by_seven_from_the_start_date() {
        LocalDate start = LocalDate.of(2026, 1, 5);
        RecurringRule rule = rule(RecurringFrequency.WEEKLY, start.getDayOfWeek(), start, null);

        List<LocalDate> dates = OccurrenceGenerator.occurrenceDates(rule, start.plusWeeks(10), null);

        assertThat(dates).hasSize(11);
        assertThat(dates.get(0)).isEqualTo(start);
        for (int i = 1; i < dates.size(); i++) {
            assertThat(ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i))).isEqualTo(7);
        }
    }

    @Test
    void biweekly_steps_by_fourteen() {
        LocalDate start = LocalDate.of(2026, 1, 5);
        RecurringRule rule = rule(RecurringFrequency.BIWEEKLY, start.getDayOfWeek(), start, null);

        List<LocalDate> dates = OccurrenceGenerator.occurrenceDates(rule, start.plusWeeks(10), null);

        assertThat(dates).hasSize(6);
        for (int i = 1; i < dates.size(); i++) {
            assertThat(ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i))).isEqualTo(14);
        }
    }

    @Test
    void anchor_moves_forward_to_the_next_matching_weekday() {
        LocalDate wednesday = LocalDate.of(2026, 1, 7);   // 수요일
        RecurringRule rule = rule(RecurringFrequency.WEEKLY, DayOfWeek.FRIDAY, wednesday, null);

        List<LocalDate> dates = OccurrenceGenerator.occurrenceDates(rule, wednesday.plusWeeks(4), null);

        assertThat(dates.get(0)).isEqualTo(wednesday.plusDays(2));   // 다음 금요일
        assertThat(dates.get(0).getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
    }

    @Test
    void end_date_caps_before_the_horizon() {
        LocalDate start = LocalDate.of(2026, 1, 5);
        RecurringRule rule = rule(RecurringFrequency.WEEKLY, start.getDayOfWeek(), start, start.plusWeeks(3));

        List<LocalDate> dates = OccurrenceGenerator.occurrenceDates(rule, start.plusWeeks(10), null);

        assertThat(dates).containsExactly(start, start.plusWeeks(1), start.plusWeeks(2), start.plusWeeks(3));
    }

    @Test
    void exclusive_after_starts_generation_past_that_date() {
        LocalDate start = LocalDate.of(2026, 1, 5);
        RecurringRule rule = rule(RecurringFrequency.WEEKLY, start.getDayOfWeek(), start, null);

        List<LocalDate> dates = OccurrenceGenerator.occurrenceDates(
                rule, start.plusWeeks(10), start.plusWeeks(2));

        assertThat(dates.get(0)).isEqualTo(start.plusWeeks(3));
    }

    @Test
    void monthly_keeps_the_week_ordinal_and_skips_months_that_lack_it() {
        LocalDate start = LocalDate.of(2026, 5, 29);   // 그 달 해당 요일의 5번째 (29 → 5주차)
        DayOfWeek dow = start.getDayOfWeek();
        RecurringRule rule = rule(RecurringFrequency.MONTHLY, dow, start, null);

        List<LocalDate> dates = OccurrenceGenerator.occurrenceDates(rule, start.plusMonths(12), null);

        assertThat(dates).isNotEmpty();
        assertThat(dates.get(0)).isEqualTo(start);
        assertThat(dates.size()).isLessThan(12);   // 5주차가 없는 달은 건너뛴다
        Integer prevKey = null;
        for (LocalDate d : dates) {
            assertThat(d.getDayOfWeek()).isEqualTo(dow);
            assertThat(((d.getDayOfMonth() - 1) / 7) + 1).isEqualTo(5);
            int key = d.getYear() * 12 + d.getMonthValue();
            if (prevKey != null) {
                assertThat(key).isGreaterThan(prevKey);
            }
            prevKey = key;
        }
    }

    @Test
    void monthly_common_case_repeats_every_month() {
        LocalDate start = LocalDate.of(2026, 6, 8);   // 2주차
        DayOfWeek dow = start.getDayOfWeek();
        RecurringRule rule = rule(RecurringFrequency.MONTHLY, dow, start, null);

        List<LocalDate> dates = OccurrenceGenerator.occurrenceDates(rule, start.plusMonths(6), null);

        assertThat(dates).hasSizeGreaterThanOrEqualTo(6);
        for (LocalDate d : dates) {
            assertThat(d.getDayOfWeek()).isEqualTo(dow);
            assertThat(((d.getDayOfMonth() - 1) / 7) + 1).isEqualTo(2);
        }
    }

    @Test
    void caps_at_max_occurrences_per_run() {
        LocalDate start = LocalDate.of(2000, 1, 3);
        RecurringRule rule = rule(RecurringFrequency.WEEKLY, start.getDayOfWeek(), start, null);

        List<LocalDate> dates = OccurrenceGenerator.occurrenceDates(rule, LocalDate.of(2100, 1, 1), null);

        assertThat(dates).hasSize(OccurrenceGenerator.MAX_OCCURRENCES_PER_RUN);
    }

    @Test
    void empty_when_the_first_occurrence_is_past_the_horizon() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        RecurringRule rule = rule(RecurringFrequency.WEEKLY, start.getDayOfWeek(), start, null);

        List<LocalDate> dates = OccurrenceGenerator.occurrenceDates(rule, LocalDate.of(2026, 5, 1), null);

        assertThat(dates).isEmpty();
    }
}
