package com.yeka.bandapp.plan.entity;

import com.yeka.bandapp.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 밴드별 요금제. 밴드당 한 행이며(band_id 유니크), 티어 변경은 새 행이 아니라 이 행을 제자리 수정한다.
 *
 * <p>불변식(DB {@code ck_band_plans_retention} 이 강제): FREE ⇒ {@code mediaRetentionDays}=30,
 * {@code expiresAt}=null / PREMIUM ⇒ {@code mediaRetentionDays}=null(무제한). 아래 상태 변경 메서드가
 * 두 필드를 항상 짝으로 맞춘다.
 *
 * <p>{@code expiresAt} 은 PREMIUM 구독기간 종료 시각이지만 이번 릴리스에서는 정보성이다 — 경과해도
 * 자동으로 FREE 로 되돌리지 않는다(실제 PG 연동 시 처리).
 */
@Entity
@Table(name = "band_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BandPlan extends BaseTimeEntity {

    /** FREE 플랜 미디어 보관일수. V10 마이그레이션의 백필 리터럴과 일치시킨다. */
    public static final int FREE_RETENTION_DAYS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "band_id", nullable = false)
    private Long bandId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanTier tier;

    @Column(name = "media_retention_days")
    private Integer mediaRetentionDays;

    @Column(name = "subscription_ref", length = 100)
    private String subscriptionRef;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private BandPlan(long bandId, Instant now) {
        this.bandId = bandId;
        this.tier = PlanTier.FREE;
        this.mediaRetentionDays = FREE_RETENTION_DAYS;
        this.subscriptionRef = null;
        this.startedAt = now;
        this.expiresAt = null;
        this.updatedAt = now;
    }

    /** 밴드 생성 시 붙는 기본 FREE 플랜. */
    public static BandPlan freePlan(long bandId, Instant now) {
        return new BandPlan(bandId, now);
    }

    /** FREE → PREMIUM. 보관기한 무제한(NULL), 구독기간 종료일과 구독 식별자를 기록한다. */
    public void upgradeToPremium(Instant now, Instant periodEnd, String subscriptionRef) {
        this.tier = PlanTier.PREMIUM;
        this.mediaRetentionDays = null;
        this.subscriptionRef = subscriptionRef;
        this.startedAt = now;
        this.expiresAt = periodEnd;
        this.updatedAt = now;
    }

    /** PREMIUM → FREE. 보관기한 30일로 복귀, 구독기간·식별자를 비운다. */
    public void downgradeToFree(Instant now) {
        this.tier = PlanTier.FREE;
        this.mediaRetentionDays = FREE_RETENTION_DAYS;
        this.subscriptionRef = null;
        this.startedAt = now;
        this.expiresAt = null;
        this.updatedAt = now;
    }

    /** PREMIUM 구독기간 연장. PREMIUM 이 아니면 호출 오류다. */
    public void renew(Instant now, Instant newPeriodEnd) {
        if (tier != PlanTier.PREMIUM) {
            throw new IllegalStateException("PREMIUM 이 아닌 플랜은 갱신할 수 없습니다: bandId=" + bandId);
        }
        this.expiresAt = newPeriodEnd;
        this.updatedAt = now;
    }

    public boolean isPremium() {
        return tier == PlanTier.PREMIUM;
    }

    public boolean isFree() {
        return tier == PlanTier.FREE;
    }

    /** 미디어 보관일수(FREE=30, PREMIUM=null). {@code null} 은 무제한을 뜻한다. */
    public Integer retentionDaysOrNull() {
        return mediaRetentionDays;
    }
}
