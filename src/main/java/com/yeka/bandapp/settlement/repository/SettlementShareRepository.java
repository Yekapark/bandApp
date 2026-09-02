package com.yeka.bandapp.settlement.repository;

import com.yeka.bandapp.settlement.entity.SettlementShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementShareRepository extends JpaRepository<SettlementShare, Long> {

    /** 정산 현황 — 정산별 몫 전부. */
    List<SettlementShare> findBySettlementId(Long settlementId);

    /** 본인 납부 체크용 — 요청자의 몫 한 건. 없으면 그 사람은 분담 대상이 아니다. */
    Optional<SettlementShare> findBySettlementIdAndUserId(Long settlementId, Long userId);
}
