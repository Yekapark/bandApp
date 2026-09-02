package com.yeka.bandapp.reservation.repository;

import com.yeka.bandapp.reservation.entity.ReservationAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReservationAttendanceRepository extends JpaRepository<ReservationAttendance, Long> {

    /** 일정 상세의 참석 현황 — 일정별 저장된 응답 행 전부. */
    List<ReservationAttendance> findByReservationId(Long reservationId);

    /** 본인 응답 upsert 용 — 행이 있으면 갱신, 없으면 INSERT(동시 최초 응답 경합은 유니크 제약이 막는다). */
    Optional<ReservationAttendance> findByReservationIdAndUserId(Long reservationId, Long userId);

    /**
     * 참석 미응답 독촉 배치(Phase 9)용 — 이 일정에 이미 응답한(ATTENDING/ABSENT) 멤버의 userId.
     * 독촉 대상은 "현재 활성 멤버 − 이 목록"이다(일정 생성 이후 합류해 행이 없는 멤버도 미응답으로 잡으려면
     * PENDING 행만 훑어서는 안 된다 — {@code AttendanceService.boardFor} 주석 참조).
     */
    @Query("""
            select a.userId from ReservationAttendance a
             where a.reservationId = :reservationId
               and a.status <> com.yeka.bandapp.reservation.entity.AttendanceStatus.PENDING
            """)
    List<Long> findRespondedUserIds(@Param("reservationId") long reservationId);
}
