package com.yeka.bandapp.recurring.entity;

/**
 * 반복 주기.
 *
 * <ul>
 *   <li>{@link #WEEKLY} — 매주 같은 요일</li>
 *   <li>{@link #BIWEEKLY} — 2주 간격, 같은 요일</li>
 *   <li>{@link #MONTHLY} — 매월 같은 "주차 + 요일" (예: 매월 둘째 주 토요일).
 *       그 주차가 없는 달은 건너뛴다.</li>
 * </ul>
 */
public enum RecurringFrequency {
    WEEKLY,
    BIWEEKLY,
    MONTHLY
}
