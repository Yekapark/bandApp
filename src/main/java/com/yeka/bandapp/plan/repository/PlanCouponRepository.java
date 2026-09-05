package com.yeka.bandapp.plan.repository;

import com.yeka.bandapp.plan.entity.PlanCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PlanCouponRepository extends JpaRepository<PlanCoupon, Long> {

    Optional<PlanCoupon> findByCode(String code);

    /**
     * 사용 횟수 +1. <b>남은 횟수 검사를 WHERE 절에 넣어</b> 두 밴드가 같은 순간 마지막 한 장을 쓰는
     * 경합을 DB 가 판정하게 한다 — 읽고 나서 더하면 둘 다 통과한다.
     *
     * @return 1 이면 확보, 0 이면 이미 소진됐거나 무효화된 것
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PlanCoupon c set c.usedCount = c.usedCount + 1 "
            + "where c.id = :id and c.revoked = false "
            + "and (c.maxUses is null or c.usedCount < c.maxUses)")
    int consume(@Param("id") long id);
}
