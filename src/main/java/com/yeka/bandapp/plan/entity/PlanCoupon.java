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
 * PREMIUM 맛보기 쿠폰. 운영자가 발급하고(앱에 발급 화면은 없다 — V12 주석 참조) 밴드장이 사용한다.
 *
 * <p>사용하면 {@code grantDays} 만큼 PREMIUM 기간을 준다. 이미 PREMIUM 인 밴드가 쓰면
 * 남은 기간에 <b>더한다</b>(만료일을 앞당기지 않는다).
 *
 * <p>사용 횟수 증가는 이 엔티티가 아니라 저장소의 조건부 UPDATE 가 한다 — 동시 사용 경합을
 * WHERE 절에서 걸러야 해서다({@code PlanCouponRepository#consume}).
 */
@Entity
@Table(name = "plan_coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 8)
    private String code;

    @Column(name = "grant_days", nullable = false)
    private int grantDays;

    /** {@code null} 이면 사용 횟수 무제한. */
    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    /** 쿠폰 자체를 쓸 수 있는 기한. {@code null} 이면 무기한. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 무효화됐거나 기한이 지났으면 쓸 수 없다. 소진 여부는 조건부 UPDATE 가 판정한다. */
    public boolean isUsable(Instant now) {
        return !revoked && (expiresAt == null || expiresAt.isAfter(now));
    }

    public boolean isExhausted() {
        return maxUses != null && usedCount >= maxUses;
    }
}
