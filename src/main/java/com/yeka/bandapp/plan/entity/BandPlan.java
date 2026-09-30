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
import java.util.Objects;

/**
 * 밴드별 요금제. 밴드당 한 행이며(band_id 유니크), 티어 변경은 새 행이 아니라 이 행을 제자리 수정한다.
 *
 * <p>불변식(DB {@code ck_band_plans_retention} 이 강제): FREE ⇒ {@code mediaRetentionDays}=30,
 * {@code expiresAt}=null / PREMIUM ⇒ {@code mediaRetentionDays}=null(무제한). 아래 상태 변경 메서드가
 * 두 필드를 항상 짝으로 맞춘다.
 *
 * <p>{@code expiresAt} 은 PREMIUM 구독기간(밴드별 1년) 종료 시각이다. 이 시각이 지나면
 * {@code PlanExpirationJob} 이 매일 밤 FREE 로 되돌리고 기존 미디어에 30일 유예를 준다.
 */
@Entity
@Table(name = "band_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BandPlan extends BaseTimeEntity {

    /** FREE 플랜 미디어 보관일수. V10 마이그레이션의 백필 리터럴과 일치시킨다. */
    public static final int FREE_RETENTION_DAYS = 30;

    /** 쿠폰으로 받은 PREMIUM 의 {@code subscriptionRef} 접두사({@code coupon-코드}). */
    public static final String COUPON_REF_PREFIX = "coupon-";

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

    /** 결제 스토어. 쿠폰·no-op 업그레이드는 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "store", length = 20)
    private Store store;

    /**
     * Google Play 구독 구매 토큰 — 서버가 구독 상태를 재조회하고 웹훅이 밴드를 찾는 키. 스토어 결제로 PREMIUM 이
     * 된 뒤로는 FREE 로 내려와도 남는다(보류·만료 후 복구를 받기 위해). 환불·쿠폰 전환·밴드 삭제 때만 비워진다.
     */
    @Column(name = "purchase_token")
    private String purchaseToken;

    /**
     * 지금 {@link #purchaseToken} 의 구독을 결제한 회원 — 결제 뒤 검증을 보낸 사람. 모르면 null(웹훅만으로 반영된 구매,
     * 이 칼럼 이전의 구매). 결제자가 탈퇴하면 서버가 자동 갱신을 해지하는 데 쓴다(LAUNCH_REVIEW B13). 토큰이 바뀌면 비운다 —
     * 다른 결제의 결제자로 남으면 엉뚱한 사람이 탈퇴할 때 남의 구독을 해지하게 된다.
     */
    @Column(name = "purchased_by_user_id")
    private Long purchasedByUserId;

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

    /**
     * FREE → PREMIUM. 보관기한 무제한(NULL), 구독기간 종료일과 구독 식별자를 기록한다.
     *
     * @param store         결제 스토어. 쿠폰이면 null.
     * @param purchaseToken 스토어 구매 토큰. 쿠폰이면 null. ({@code store} 와 짝 — DB CHECK 가 강제)
     */
    public void upgradeToPremium(Instant now, Instant periodEnd, String subscriptionRef,
                                 Store store, String purchaseToken) {
        forgetPurchaserIfTokenChanges(purchaseToken);
        this.tier = PlanTier.PREMIUM;
        this.mediaRetentionDays = null;
        this.subscriptionRef = subscriptionRef;
        this.store = store;
        this.purchaseToken = purchaseToken;
        this.startedAt = now;
        this.expiresAt = periodEnd;
        this.updatedAt = now;
    }

    /**
     * PREMIUM → FREE (만료·계정 보류·해지 후 기간 종료). 보관기한 30일로 복귀, 구독기간·식별자를 비운다.
     *
     * <p><b>스토어 구매 토큰은 남긴다.</b> Google Play 구독은 FREE 로 내려온 뒤에도 같은 토큰으로 되살아난다 —
     * 계정 보류(ON_HOLD) 뒤 결제 수단을 고치면 RECOVERED, 만료 뒤 재구독하면 RESTARTED, 갱신 알림이 늦으면
     * RENEWED 가 그 토큰으로 온다. 예전에는 여기서 토큰을 지워 웹훅이 밴드를 찾지 못했고, 사용자는 돈을 내고도
     * FREE 로 남아 30일 뒤 사진·영상을 잃었다(LAUNCH_REVIEW B1). 토큰을 버리는 것은 환불({@link #revokeToFree}) 뿐이다.
     */
    public void downgradeToFree(Instant now) {
        this.tier = PlanTier.FREE;
        this.mediaRetentionDays = FREE_RETENTION_DAYS;
        this.subscriptionRef = null;
        this.startedAt = now;
        this.expiresAt = null;
        this.updatedAt = now;
    }

    /**
     * 환불·강제 취소(REVOKED)로 FREE. {@link #downgradeToFree} 와 같고 스토어 정보까지 지운다 —
     * 취소된 구매는 되살아나지 않으므로 그 토큰으로 이 밴드를 다시 찾을 이유가 없다.
     */
    public void revokeToFree(Instant now) {
        downgradeToFree(now);
        this.store = null;
        this.purchaseToken = null;
        this.purchasedByUserId = null;
    }

    /**
     * 해지 예약 — <b>티어와 {@code expiresAt} 은 건드리지 않는다.</b> 구독 식별자만 비운다.
     *
     * <p>결제한 기간의 혜택은 끝까지 준다. 실제 강등은 만료일 밤에 {@code PlanExpirationJob} 이
     * 하고, 미디어 유예도 그때 시작된다. 예전에는 해지 버튼을 누른 자리에서 FREE 로 내렸는데,
     * 1년치를 결제하고 하루 뒤 해지하면 364일이 증발했다. 스토어 인앱결제(구독 취소 = 자동 갱신
     * 중지, 기간 끝까지 이용)와도 어긋났다.
     *
     * <p>{@code subscriptionRef} 를 비우는 것이 "해지했다" 는 표시를 겸한다 — 게이트웨이의 구독이
     * 실제로 사라졌으니 의미상 맞고, 컬럼을 늘리지 않아도 {@link #isCanceled()} 로 구분된다.
     * 실제 PG 를 붙여 해지 뒤에도 식별자가 필요해지면 그때 {@code canceled_at} 을 따로 둔다.
     */
    public void cancelAtPeriodEnd(Instant now) {
        this.subscriptionRef = null;
        this.updatedAt = now;
    }

    /** 해지 예약된 PREMIUM(만료일까지는 계속 이용). {@link #cancelAtPeriodEnd} 참고. */
    public boolean isCanceled() {
        return tier == PlanTier.PREMIUM && subscriptionRef == null;
    }

    /** PREMIUM 구독기간 연장. PREMIUM 이 아니면 호출 오류다. */
    public void renew(Instant now, Instant newPeriodEnd) {
        if (tier != PlanTier.PREMIUM) {
            throw new IllegalStateException("PREMIUM 이 아닌 플랜은 갱신할 수 없습니다: bandId=" + bandId);
        }
        this.expiresAt = newPeriodEnd;
        this.updatedAt = now;
    }

    /**
     * 스토어 결제로 PREMIUM 을 연장 — 기간을 늘리고 스토어 식별자를 (없었으면) 붙인다.
     * 쿠폰으로 PREMIUM 이 된 밴드가 나중에 실제로 결제한 경우, 이후 RTDN 웹훅이 이 토큰으로 밴드를
     * 찾을 수 있게 한다. PREMIUM 이 아니면 호출 오류다.
     */
    public void renewFromStore(Instant now, Instant newPeriodEnd, String subscriptionRef,
                               Store store, String purchaseToken) {
        if (tier != PlanTier.PREMIUM) {
            throw new IllegalStateException("PREMIUM 이 아닌 플랜은 갱신할 수 없습니다: bandId=" + bandId);
        }
        forgetPurchaserIfTokenChanges(purchaseToken);
        this.expiresAt = newPeriodEnd;
        this.subscriptionRef = subscriptionRef;
        this.store = store;
        this.purchaseToken = purchaseToken;
        this.updatedAt = now;
    }

    /**
     * 결제한 회원을 적는다 — 그 회원이 보낸 구매 토큰이 <b>지금 이 밴드의 토큰일 때만</b>. 검증 사이에 다른 결제·환불로 토큰이
     * 바뀌었으면 적지 않는다(엉뚱한 구독에 결제자가 붙지 않게).
     */
    public void recordPurchaser(String purchaseToken, long userId) {
        if (purchaseToken != null && purchaseToken.equals(this.purchaseToken)) {
            this.purchasedByUserId = userId;
        }
    }

    /** 결제자 연결을 끊는다 — 결제자가 탈퇴해 자동 갱신 해지를 마쳤을 때. 구독 자체는 건드리지 않는다. */
    public void forgetPurchaser() {
        this.purchasedByUserId = null;
    }

    private void forgetPurchaserIfTokenChanges(String newToken) {
        if (!Objects.equals(this.purchaseToken, newToken)) {
            this.purchasedByUserId = null;
        }
    }

    public boolean isPremium() {
        return tier == PlanTier.PREMIUM;
    }

    /**
     * 스토어 결제로 PREMIUM 이고 해지 예약도 안 했다 = 다음 주기에 Google 이 또 청구한다.
     * 쿠폰 PREMIUM(스토어 없음, 또는 보류 뒤 남은 토큰 위에 쿠폰을 쓴 것)·해지 예약·FREE 는 false.
     * 이 밴드를 지우면 청구는 계속되는데 반영할 밴드가 없어진다(LAUNCH_REVIEW B5).
     */
    public boolean isAutoRenewingStoreSubscription() {
        return isPremium() && store != null && !isCanceled() && !isCouponPeriod();
    }

    /** 지금 PREMIUM 이 쿠폰으로 받은 기간인가({@code subscriptionRef} 가 {@link #COUPON_REF_PREFIX} 로 시작). */
    public boolean isCouponPeriod() {
        return isPremium() && subscriptionRef != null && subscriptionRef.startsWith(COUPON_REF_PREFIX);
    }

    public boolean isFree() {
        return tier == PlanTier.FREE;
    }

    /** 미디어 보관일수(FREE=30, PREMIUM=null). {@code null} 은 무제한을 뜻한다. */
    public Integer retentionDaysOrNull() {
        return mediaRetentionDays;
    }
}
