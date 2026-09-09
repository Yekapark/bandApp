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
 * 누가 어느 밴드에 어느 쿠폰을 썼는지. 유니크 제약 둘이 반복 사용을 DB 레벨에서 막는다 —
 * {@code (coupon_id, band_id)} 는 같은 밴드의 재사용을, {@code (coupon_id, redeemed_by)} 는
 * 한 사람이 밴드를 여러 개 만들어 같은 코드를 반복 사용하는 것을 막는다(V18).
 * 서비스는 두 위반을 모두 {@code COUPON_ALREADY_USED} 로 옮긴다.
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
