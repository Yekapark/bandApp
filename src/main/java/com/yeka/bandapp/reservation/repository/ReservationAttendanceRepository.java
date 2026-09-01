package com.yeka.bandapp.reservation.repository;

import com.yeka.bandapp.reservation.entity.ReservationAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReservationAttendanceRepository extends JpaRepository<ReservationAttendance, Long> {

    /** 일정 상세의 참석 현황 — 일정별 저장된 응답 행 전부. */
    List<ReservationAttendance> findByReservationId(Long reservationId);

    /**
     * 본인 응답 upsert. 행이 없으면(일정 생성 이후 합류한 멤버) 넣고, 있으면 상태·응답 시각만 바꾼다.
     * Postgres {@code ON CONFLICT}로 <b>한 문장·원자적</b>이라, 같은 멤버의 동시 최초 응답(더블탭)에도
     * {@code (reservation_id, user_id)} 유니크 경합으로 트랜잭션이 깨지지 않는다.
     *
     * <p>{@code responded_at}은 {@code PENDING}(응답 취소)이면 비우고, 그 외에는 {@code now}로 채운다.
     * 네이티브 INSERT라 {@code @CreatedDate} 감사 리스너를 타지 않으므로 {@code created_at}도 여기서 채운다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into reservation_attendances (reservation_id, user_id, status, responded_at, created_at)
            values (:reservationId, :userId, :status,
                    case when :status = 'PENDING' then null else :now end,
                    :now)
            on conflict (reservation_id, user_id)
            do update set status = excluded.status, responded_at = excluded.responded_at
            """, nativeQuery = true)
    void upsertResponse(@Param("reservationId") long reservationId,
                        @Param("userId") long userId,
                        @Param("status") String status,
                        @Param("now") Instant now);
}
