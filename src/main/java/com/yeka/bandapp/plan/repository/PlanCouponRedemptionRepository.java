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

    /** 사용 기록 되돌리기 — 쿠폰을 스토어 결제일에 쌓으려다 실패했을 때(B7). */
    @Modifying
    @Query("delete from PlanCouponRedemption r where r.couponId = :couponId and r.bandId = :bandId")
    int deleteByCouponIdAndBandId(@Param("couponId") long couponId, @Param("bandId") long bandId);
}
