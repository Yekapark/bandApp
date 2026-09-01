package com.yeka.bandapp.reservation.repository;

import com.yeka.bandapp.reservation.entity.ReservationAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservationAttendanceRepository extends JpaRepository<ReservationAttendance, Long> {

    /** 일정 상세의 참석 현황 — 일정별 저장된 응답 행 전부. */
    List<ReservationAttendance> findByReservationId(Long reservationId);

    /** 본인 응답 upsert 용 — 행이 있으면 갱신, 없으면 INSERT(동시 최초 응답 경합은 유니크 제약이 막는다). */
    Optional<ReservationAttendance> findByReservationIdAndUserId(Long reservationId, Long userId);
}
