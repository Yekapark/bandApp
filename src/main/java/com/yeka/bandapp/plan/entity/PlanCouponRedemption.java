package com.yeka.bandapp.plan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 어느 밴드가 어느 쿠폰을 썼는지. {@code (coupon_id, band_id)} 유니크가 같은 밴드의 반복 사용을
 * DB 레벨에서 막는다 — 서비스는 이 제약 위반을 {@code COUPON_ALREADY_USED} 로 옮긴다.
 */
@Entity
@Table(name = "plan_coupon_redemptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanCouponRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "band_id", nullable = false)
    private Long bandId;

    @Column(name = "redeemed_by", nullable = false)
    private Long redeemedBy;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt;

    private PlanCouponRedemption(long couponId, long bandId, long redeemedBy, Instant redeemedAt) {
        this.couponId = couponId;
        this.bandId = bandId;
        this.redeemedBy = redeemedBy;
        this.redeemedAt = redeemedAt;
    }

    public static PlanCouponRedemption of(long couponId, long bandId, long redeemedBy, Instant redeemedAt) {
        return new PlanCouponRedemption(couponId, bandId, redeemedBy, redeemedAt);
    }
}
