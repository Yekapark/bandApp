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
    void monthly_fifth_week_means_last_week_of_every_month() {
        // 2026-10-31 은 그 달 5번째 토요일 → "매월 마지막 주 토요일" 로 본다(U26). 예전에는 5째 토요일이
        // 있는 달(1년에 몇 번)에만 생겼다.
        LocalDate start = LocalDate.of(2026, 10, 31);
        RecurringRule rule = rule(RecurringFrequency.MONTHLY, DayOfWeek.SATURDAY, start, null);

        List<LocalDate> dates = OccurrenceGenerator.occurrenceDates(rule, LocalDate.of(2027, 3, 31), null);

        assertThat(dates).containsExactly(
                LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 28), LocalDate.of(2026, 12, 26),
                LocalDate.of(2027, 1, 30), LocalDate.of(2027, 2, 27), LocalDate.of(2027, 3, 27));
        assertThat(OccurrenceGenerator.monthlyWeek(rule)).isEqualTo(OccurrenceGenerator.LAST_WEEK);
    }

    @Test
    void monthly_last_week_extension_continues_after_last_generated_without_duplicates() {
        // 배치는 "이미 만든 마지막 회차 다음부터" 요청한다 — 그 날짜는 다시 만들지 않는다.
        LocalDate start = LocalDate.of(2026, 10, 31);
        RecurringRule rule = rule(RecurringFrequency.MONTHLY, DayOfWeek.SATURDAY, start, null);

        List<LocalDate> dates = OccurrenceGenerator.occurrenceDates(
                rule, LocalDate.of(2027, 1, 31), LocalDate.of(2026, 11, 28));

        assertThat(dates).containsExactly(LocalDate.of(2026, 12, 26), LocalDate.of(2027, 1, 30));
    }

    @Test
    void monthly_start_before_weekday_uses_first_matching_day_for_the_week() {
        // 시작일 2026-10-29(목) → 처음 맞는 토요일 10-31 이 5번째 → 마지막 주.
        RecurringRule rule = rule(RecurringFrequency.MONTHLY, DayOfWeek.SATURDAY, LocalDate.of(2026, 10, 29), null);
        assertThat(OccurrenceGenerator.monthlyWeek(rule)).isEqualTo(OccurrenceGenerator.LAST_WEEK);
        // 4번째는 그대로 4번째(마지막 주로 바꾸지 않는다): 2026-10-24 는 4번째 토요일, 2027-01 에는 5번째가 있다.
        RecurringRule fourth = rule(RecurringFrequency.MONTHLY, DayOfWeek.SATURDAY, LocalDate.of(2026, 10, 24), null);
        assertThat(OccurrenceGenerator.monthlyWeek(fourth)).isEqualTo(4);
        assertThat(OccurrenceGenerator.occurrenceDates(fourth, LocalDate.of(2027, 1, 31), null))
                .contains(LocalDate.of(2027, 1, 23));
        assertThat(OccurrenceGenerator.monthlyWeek(rule(RecurringFrequency.WEEKLY, DayOfWeek.SATURDAY,
                LocalDate.of(2026, 10, 31), null))).isNull();
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
    void leap_day_year_end_and_end_date_boundaries() {
        // QA REC-02 — 윤일(2028-02-29 은 그 달 5번째 화요일 → 마지막 주), 종료일 하루 전 회차는 빠진다.
        RecurringRule leap = rule(RecurringFrequency.MONTHLY, DayOfWeek.TUESDAY,
                LocalDate.of(2028, 2, 29), LocalDate.of(2028, 5, 29));
        assertThat(OccurrenceGenerator.occurrenceDates(leap, LocalDate.of(2028, 12, 31), null))
                .containsExactly(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 28), LocalDate.of(2028, 4, 25));

        // 격주가 해를 넘어도 14일 간격, 종료일 당일은 포함·하루 앞당기면 제외.
        LocalDate start = LocalDate.of(2026, 12, 24);
        RecurringRule onEnd = rule(RecurringFrequency.BIWEEKLY, DayOfWeek.THURSDAY, start, LocalDate.of(2027, 1, 21));
        assertThat(OccurrenceGenerator.occurrenceDates(onEnd, LocalDate.of(2027, 3, 1), null))
                .containsExactly(start, LocalDate.of(2027, 1, 7), LocalDate.of(2027, 1, 21));
        RecurringRule beforeEnd = rule(RecurringFrequency.BIWEEKLY, DayOfWeek.THURSDAY, start, LocalDate.of(2027, 1, 20));
        assertThat(OccurrenceGenerator.occurrenceDates(beforeEnd, LocalDate.of(2027, 3, 1), null))
                .containsExactly(start, LocalDate.of(2027, 1, 7));
    }

    @Test
    void empty_when_the_first_occurrence_is_past_the_horizon() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        RecurringRule rule = rule(RecurringFrequency.WEEKLY, start.getDayOfWeek(), start, null);

        List<LocalDate> dates = OccurrenceGenerator.occurrenceDates(rule, LocalDate.of(2026, 5, 1), null);

        assertThat(dates).isEmpty();
    }
}
