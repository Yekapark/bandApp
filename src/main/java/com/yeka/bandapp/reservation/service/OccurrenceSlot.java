package com.yeka.bandapp.reservation.service;

import java.time.Instant;

/**
 * 정기 규칙이 만들 회차 한 건의 확정된 UTC 시각. 정기 도메인이 로컬 요일·시각을 이미 시간대 변환해
 * 넘겨주면, 일정 도메인({@link ReservationDirectoryService})은 이 값만으로 {@code Reservation}을 만든다.
 */
public record OccurrenceSlot(Instant startAt, Instant endAt) {
}
