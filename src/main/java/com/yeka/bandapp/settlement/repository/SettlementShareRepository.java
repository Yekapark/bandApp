package com.yeka.bandapp.settlement.repository;

import com.yeka.bandapp.settlement.entity.SettlementShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SettlementShareRepository extends JpaRepository<SettlementShare, Long> {

    /** 정산 현황 — 정산별 몫 전부. */
    List<SettlementShare> findBySettlementId(Long settlementId);

    /** 목록 화면용 — 페이지에 든 정산들의 몫을 한 번에 읽는다(정산마다 조회하지 않는다). */
    List<SettlementShare> findBySettlementIdIn(Collection<Long> settlementIds);

    /** 본인 납부 체크용 — 요청자의 몫 한 건. 없으면 그 사람은 분담 대상이 아니다. */
    Optional<SettlementShare> findBySettlementIdAndUserId(Long settlementId, Long userId);

    /**
     * 홈 요약 카드용 — 그 밴드에서 내가 아직 안 낸 몫(미납·면제 아님)의 합계와 건수.
     * 정산 목록 화면과 같은 범위(그 밴드 일정에 달린 정산 전부)를 센다.
     */
    @Query("""
            select new com.yeka.bandapp.settlement.repository.MyOutstandingRow(
                       coalesce(sum(sh.amount), 0L), count(sh))
            from SettlementShare sh, Settlement s, Reservation r
            where s.id = sh.settlementId and r.id = s.reservationId and r.bandId = :bandId
              and sh.userId = :userId and sh.paid = false and sh.exempt = false
            """)
    MyOutstandingRow sumMyOutstanding(@Param("bandId") long bandId, @Param("userId") long userId);
}
