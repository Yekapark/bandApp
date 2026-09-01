package com.yeka.bandapp.recurring.service;

import com.yeka.bandapp.recurring.entity.RecurringRule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 규칙 하나가 만들어야 할 회차 날짜를 계산하는 순수 함수 모음. 스프링 빈이 아니며 상태도 시계도 갖지
 * 않는다 — 컨테이너 없이 단위 테스트할 수 있게 이렇게 둔다. {@code java.time}만 쓰고 외부 반복 규칙
 * 라이브러리(iCal 등)를 쓰지 않는다(BUILD_PLAN 2장 6번).
 */
public final class OccurrenceGenerator {

    /**
     * 한 번의 계산이 만들 수 있는 회차 상한. {@code startDate}가 과거로 아주 멀거나 규칙이 오작동해도
     * 응답·배치가 폭주하지 않게 막는 안전장치다.
     */
    public static final int MAX_OCCURRENCES_PER_RUN = 200;

    private OccurrenceGenerator() {
    }

    /**
     * 규칙이 만들어야 할 로컬 시작일 목록. 구간은 {@code (exclusiveAfter, horizonEndInclusive]}이며
     * {@code startDate}·{@code endDate}로 한 번 더 좁힌다.
     *
     * @param horizonEndInclusive 이 날짜(포함)까지만 만든다. 보통 "오늘 + N주".
     * @param exclusiveAfter      이 날짜는 제외하고 그 <b>다음</b>부터 만든다. {@code null}이면 {@code startDate}부터.
     *                            배치가 "이미 만든 마지막 회차 다음부터"를 요청할 때 쓴다.
     */
    public static List<LocalDate> occurrenceDates(RecurringRule rule, LocalDate horizonEndInclusive,
                                                  LocalDate exclusiveAfter) {
        LocalDate anchor = firstOnOrAfter(rule.getStartDate(), rule.getDayOfWeek());
        LocalDate hardEnd = rule.getEndDate() == null
                ? horizonEndInclusive
                : earlier(horizonEndInclusive, rule.getEndDate());
        if (hardEnd.isBefore(anchor)) {
            return List.of();
        }

        List<LocalDate> out = new ArrayList<>();
        switch (rule.getFrequency()) {
            case WEEKLY -> stepByDays(anchor, hardEnd, exclusiveAfter, 7, out);
            case BIWEEKLY -> stepByDays(anchor, hardEnd, exclusiveAfter, 14, out);
            case MONTHLY -> monthly(anchor, hardEnd, exclusiveAfter, rule.getDayOfWeek(), out);
        }
        return out;
    }

    /** 로컬 날짜 + 로컬 시각을 주어진 시간대로 해석해 UTC {@link Instant}로. 서울은 DST가 없어 모호하지 않다. */
    public static Instant toInstant(LocalDate date, LocalTime time, ZoneId zone) {
        return LocalDateTime.of(date, time).atZone(zone).toInstant();
    }

    // --- 내부 -----------------------------------------------------------------

    private static void stepByDays(LocalDate anchor, LocalDate hardEnd, LocalDate exclusiveAfter,
                                   int step, List<LocalDate> out) {
        for (LocalDate d = anchor;
             !d.isAfter(hardEnd) && out.size() < MAX_OCCURRENCES_PER_RUN;
             d = d.plusDays(step)) {
            if (exclusiveAfter == null || d.isAfter(exclusiveAfter)) {
                out.add(d);
            }
        }
    }

    private static void monthly(LocalDate anchor, LocalDate hardEnd, LocalDate exclusiveAfter,
                                DayOfWeek dow, List<LocalDate> out) {
        int ordinal = ((anchor.getDayOfMonth() - 1) / 7) + 1;   // anchor가 그 달의 몇 번째 dow인지
        YearMonth month = YearMonth.from(anchor);
        YearMonth lastMonth = YearMonth.from(hardEnd);
        while (!month.isAfter(lastMonth) && out.size() < MAX_OCCURRENCES_PER_RUN) {
            LocalDate d = nthWeekdayOfMonth(month, dow, ordinal);
            if (d != null
                    && !d.isBefore(anchor)
                    && !d.isAfter(hardEnd)
                    && (exclusiveAfter == null || d.isAfter(exclusiveAfter))) {
                out.add(d);
            }
            month = month.plusMonths(1);
        }
    }

    /** 그 달의 {@code n}번째 {@code dow}. 그런 날이 없으면(예: 5번째 토요일이 없는 달) {@code null}. */
    private static LocalDate nthWeekdayOfMonth(YearMonth month, DayOfWeek dow, int n) {
        LocalDate first = month.atDay(1);
        int shift = Math.floorMod(dow.getValue() - first.getDayOfWeek().getValue(), 7);
        LocalDate candidate = first.plusDays(shift).plusWeeks(n - 1L);
        return candidate.getMonth() == first.getMonth() ? candidate : null;
    }

    private static LocalDate firstOnOrAfter(LocalDate date, DayOfWeek dow) {
        int shift = Math.floorMod(dow.getValue() - date.getDayOfWeek().getValue(), 7);
        return date.plusDays(shift);
    }

    private static LocalDate earlier(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
