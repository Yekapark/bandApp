package com.yeka.bandapp.settlement.repository;

import com.yeka.bandapp.settlement.entity.SettlementShare;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
