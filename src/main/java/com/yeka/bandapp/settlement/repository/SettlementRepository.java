package com.yeka.bandapp.settlement.repository;

import com.yeka.bandapp.settlement.entity.Settlement;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    /**
     * 밴드의 정산 목록 한 페이지. 정산 등록 순서의 역순({@code id desc})이며, 커서도 같은 id 다.
     *
     * <p>정산은 일정에만 매달려 있어 밴드로 거르려면 일정을 조인해야 한다. 연관관계 매핑 없이
     * id 로 이어 둔 구조라 명시적 조인으로 쓴다.
     */
    @Query("""
            select new com.yeka.bandapp.settlement.repository.BandSettlementRow(
                       s.id, s.reservationId, r.startAt, r.roomId, s.totalAmount)
            from Settlement s, Reservation r
            where r.id = s.reservationId and r.bandId = :bandId
              and (:cursor is null or s.id < :cursor)
            order by s.id desc
            """)
    List<BandSettlementRow> findBandFeed(@Param("bandId") long bandId,
                                         @Param("cursor") Long cursor, Pageable pageable);

    /** 현황 조회·중복 확인용(잠금 없음). 일정당 정산은 하나뿐이다(유니크). */
    Optional<Settlement> findByReservationId(Long reservationId);

    /**
     * 재계산용 — 정산 행에 {@code PESSIMISTIC_WRITE}(= {@code SELECT … FOR UPDATE})를 걸어 같은 일정에
     * 대한 동시 재계산을 직렬화한다. 트랜잭션 안에서만 호출한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Settlement s where s.reservationId = :reservationId")
    Optional<Settlement> findByReservationIdForUpdate(@Param("reservationId") long reservationId);

    /**
     * 밴드 삭제 정리 — 그 밴드 일정에 달린 정산을 모두 지운다.
     * {@code settlement_shares} 는 FK 에 {@code ON DELETE CASCADE} 가 걸려 있어 따라 지워진다
     * (스키마 전체에서 유일한 cascade — V7__settlement.sql).
     */
    @Modifying
    @Query("delete from Settlement s where s.reservationId in "
            + "(select r.id from Reservation r where r.bandId = :bandId)")
    int deleteByBandId(@Param("bandId") long bandId);
}
