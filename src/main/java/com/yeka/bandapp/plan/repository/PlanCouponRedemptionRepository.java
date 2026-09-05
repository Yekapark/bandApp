package com.yeka.bandapp.plan.repository;

import com.yeka.bandapp.plan.entity.PlanCouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanCouponRedemptionRepository extends JpaRepository<PlanCouponRedemption, Long> {

    /** 밴드 삭제 정리용. */
    @Modifying
    @Query("delete from PlanCouponRedemption r where r.bandId = :bandId")
    int deleteByBandId(@Param("bandId") long bandId);
}
