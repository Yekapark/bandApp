package com.yeka.bandapp.reservation.entity;

/**
 * 일정에 대한 멤버의 참석 응답.
 *
 * <ul>
 *   <li>{@code PENDING} — 아직 응답하지 않음(일정 생성 시 밴드 멤버 전원의 초기값).</li>
 *   <li>{@code ATTENDING} — 참석.</li>
 *   <li>{@code ABSENT} — 불참.</li>
 * </ul>
 */
public enum AttendanceStatus {
    ATTENDING,
    ABSENT,
    PENDING
}
