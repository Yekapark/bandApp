package com.yeka.bandapp.settlement.repository;

import com.yeka.bandapp.settlement.entity.Settlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    /** 현황 조회·중복 확인용(잠금 없음). 일정당 정산은 하나뿐이다(유니크). */
    Optional<Settlement> findByReservationId(Long reservationId);

    /**
     * 재계산용 — 정산 행에 {@code PESSIMISTIC_WRITE}(= {@code SELECT … FOR UPDATE})를 걸어 같은 일정에
     * 대한 동시 재계산을 직렬화한다. 트랜잭션 안에서만 호출한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.reservationId = :reservationId")
    Optional<Settlement> findByReservationIdForUpdate(@Param("reservationId") long reservationId);
}
