package com.yeka.bandapp.settlement.entity;

/**
 * 정산 총액을 나누는 방식.
 *
 * <ul>
 *   <li>{@code EQUAL} — 현재 활성 밴드 멤버 전원에게 균등분배.</li>
 *   <li>{@code ATTENDEES_ONLY} — 참석({@code ATTENDING}) 응답한 멤버에게만 균등분배.
 *       참석자가 0명이면 정산을 만들 수 없다(409 {@code SETTLEMENT_NO_ATTENDEES}).</li>
 * </ul>
 *
 * <p>어느 방식이든 나누어떨어지지 않는 나머지는 "밴드장 먼저 → 가입일 순"으로 앞에서부터 1원씩 더해
 * 몫 합계가 총액과 정확히 일치한다.
 */
public enum SplitType {
    EQUAL,
    ATTENDEES_ONLY
}
