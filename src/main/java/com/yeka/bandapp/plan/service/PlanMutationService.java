package com.yeka.bandapp.plan.service;

import com.yeka.bandapp.board.service.MediaDirectoryService;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.plan.entity.BandPlan;
import com.yeka.bandapp.plan.entity.Store;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * 요금제 티어 전환의 DB 쓰기 단위. 각 메서드는 하나의 {@code @Transactional} 안에서
 * {@code BandPlan} 엔티티를 수정하고 밴드 미디어 보관기한을 재계산한다 — 티어 플립과 미디어
 * 재계산이 원자적으로 커밋된다.
 *
 * <p>스토어 조회는 이 서비스가 아니라 {@link StoreSubscriptionService} 가 <b>트랜잭션 밖에서</b> 먼저 끝낸다.
 * 그래서 이 메서드들 안에는 외부 I/O 가 없고, 한 트랜잭션으로 묶는 것이 안전하다.
 *
 * <p>사용자 요청 경로(쿠폰 등)는 이미 확인된 선행조건이라 위반 시 예외를 던진다. 웹훅 경로는
 * 중복·순서 뒤바뀜을 전제로 하므로 {@code …IfPremium} 계열이 조용히 no-op 한다.
 */
@Service
public class PlanMutationService {

    private final BandPlanRepository bandPlanRepository;
    private final MediaDirectoryService mediaDirectory;

    public PlanMutationService(BandPlanRepository bandPlanRepository, MediaDirectoryService mediaDirectory) {
        this.bandPlanRepository = bandPlanRepository;
        this.mediaDirectory = mediaDirectory;
    }

    /**
     * FREE → PREMIUM. 이미 PREMIUM 이면 {@code PLAN_ALREADY_PREMIUM}(동시 요청 가드 겸용).
     * 밴드의 기존 READY 미디어 보관기한을 무제한(NULL)으로 만든다.
     *
     * @param store         결제 스토어. 쿠폰이면 null.
     * @param purchaseToken 스토어 구매 토큰. 쿠폰이면 null.
     */
    @Transactional
    public BandPlan applyUpgrade(long bandId, Instant now, Instant periodEnd, String subscriptionRef,
                                 Store store, String purchaseToken) {
        BandPlan plan = requirePlan(bandId);
        if (!plan.isFree()) {
            throw new BusinessException(ErrorCode.PLAN_ALREADY_PREMIUM);
        }
        plan.upgradeToPremium(now, periodEnd, subscriptionRef, store, purchaseToken);
        mediaDirectory.extendRetentionForBand(bandId);
        return plan;
    }

    /**
     * PREMIUM → FREE. 이미 FREE 이면 {@code PLAN_ALREADY_FREE}.
     * 밴드의 기존 READY 미디어 만료 시각을 {@code graceUntil} 로 덮어쓴다(유예기간).
     */
    @Transactional
    public BandPlan applyDowngrade(long bandId, Instant now, Instant graceUntil) {
        BandPlan plan = requirePlan(bandId);
        if (!plan.isPremium()) {
            throw new BusinessException(ErrorCode.PLAN_ALREADY_FREE);
        }
        plan.downgradeToFree(now);
        mediaDirectory.applyGracePeriodForBand(bandId, graceUntil);
        return plan;
    }

    /**
     * 해지 예약. 티어·만료일·미디어 보관기한을 <b>건드리지 않는다</b> — 아직 PREMIUM 이라 첨부는
     * 계속 무제한 보관이고, 만료일 밤에 {@code PlanExpirationJob} 이 강등하며 그때 유예가 시작된다.
     * FREE 면 {@code PLAN_ALREADY_FREE}, 이미 해지 예약됐으면 {@code PLAN_ALREADY_CANCELED}.
     */
    @Transactional
    public BandPlan applyCancelAtPeriodEnd(long bandId, Instant now) {
        BandPlan plan = requirePlan(bandId);
        if (!plan.isPremium()) {
            throw new BusinessException(ErrorCode.PLAN_ALREADY_FREE);
        }
        if (plan.isCanceled()) {
            throw new BusinessException(ErrorCode.PLAN_ALREADY_CANCELED);
        }
        plan.cancelAtPeriodEnd(now);
        return plan;
    }

    /** PREMIUM 구독기간 연장. FREE 이면 {@code PLAN_ALREADY_FREE}. 미디어 재계산은 없다(이미 무제한). */
    @Transactional
    public BandPlan applyRenew(long bandId, Instant now, Instant newPeriodEnd) {
        BandPlan plan = requirePlan(bandId);
        if (!plan.isPremium()) {
            throw new BusinessException(ErrorCode.PLAN_ALREADY_FREE);
        }
        plan.renew(now, newPeriodEnd);
        return plan;
    }

    /**
     * 스토어 결제로 PREMIUM 연장 — 기간을 늘리고 스토어 식별자를 붙인다(쿠폰으로 PREMIUM 이던 밴드가
     * 이후 실제 결제한 경우 웹훅이 토큰으로 밴드를 찾을 수 있게). FREE 이면 {@code PLAN_ALREADY_FREE}.
     */
    @Transactional
    public BandPlan applyStoreRenew(long bandId, Instant now, Instant newPeriodEnd, String subscriptionRef,
                                    Store store, String purchaseToken) {
        BandPlan plan = requirePlan(bandId);
        if (!plan.isPremium()) {
            throw new BusinessException(ErrorCode.PLAN_ALREADY_FREE);
        }
        plan.renewFromStore(now, newPeriodEnd, subscriptionRef, store, purchaseToken);
        return plan;
    }

    /** 스토어 결제 반영 결과 + 그 직전까지 남아 있던 쿠폰 기간(없으면 0). */
    public record StoreRenewal(BandPlan plan, Duration couponLeft) {
    }

    /**
     * {@link #applyStoreRenew} 와 같고, <b>쿠폰 기간 중이었다면 남은 기간을 함께 돌려준다</b>(B7). 호출자가 그만큼
     * 스토어 결제일을 미뤄 쿠폰 기간을 결제 기간 뒤에 쌓는다. 행 잠금 안에서 판정하므로 결제 확인과 웹훅이 동시에
     * 와도 남은 기간은 한 번만 나온다(두 번째는 이미 스토어 결제로 바뀐 행을 본다).
     */
    @Transactional
    public StoreRenewal applyStoreRenewCarryingCoupon(long bandId, Instant now, Instant newPeriodEnd,
                                                      String subscriptionRef, Store store, String purchaseToken) {
        BandPlan plan = requirePlan(bandId);
        if (!plan.isPremium()) {
            throw new BusinessException(ErrorCode.PLAN_ALREADY_FREE);
        }
        Duration couponLeft = plan.isCouponPeriod() && plan.getExpiresAt() != null && plan.getExpiresAt().isAfter(now)
                ? Duration.between(now, plan.getExpiresAt())
                : Duration.ZERO;
        plan.renewFromStore(now, newPeriodEnd, subscriptionRef, store, purchaseToken);
        return new StoreRenewal(plan, couponLeft);
    }

    // --- 웹훅 경로 (조용히 no-op) --------------------------------------------------------

    /**
     * RTDN "해지" 반영. PREMIUM 이면 해지 예약, 아니면(이미 FREE·이미 해지) 아무것도 안 한다.
     * 웹훅은 중복·재전송이 정상이라 예외를 던지지 않는다.
     *
     * <p><b>쿠폰 기간이면 건드리지 않는다</b> — 보류(ON_HOLD) 뒤 남은 옛 토큰 위에 쿠폰을 쓴 밴드에 그 옛 구독의 알림이
     * 오는 경우다. 쿠폰 표시(coupon-)를 지우면 다음 쿠폰이 끝난 구독에 결제일 연기를 시도해 거절된다.
     *
     * @return 반영 뒤 요금제(요금제 행이 없으면 null)
     */
    @Transactional
    public BandPlan applyCancelAtPeriodEndIfPremium(long bandId, Instant now) {
        BandPlan plan = bandPlanRepository.findByBandIdForUpdate(bandId).orElse(null);
        if (plan == null || !plan.isPremium() || plan.isCanceled() || plan.isCouponPeriod()) {
            return plan;
        }
        plan.cancelAtPeriodEnd(now);
        return plan;
    }

    /**
     * RTDN "만료·보류" 반영. PREMIUM 이면 유예기간을 주고 FREE 로, 아니면 no-op.
     * 유예 길이·의미는 {@link #applyDowngrade} 와 같다(수동 만료 배치와 동일하게 보이도록).
     *
     * <p><b>쿠폰 기간이면 내리지 않는다</b> — 보류 뒤 FREE 가 된 밴드가 쿠폰을 쓰면 옛 토큰이 남아 있어(B1), 그 옛
     * 구독이 보류 끝에 만료(EXPIRED)되면 이 알림이 온다. 예전에는 받은 쿠폰 기간이 그 자리에서 사라졌다.
     * 쿠폰 기간의 끝은 만료 배치가 맡는다.
     */
    @Transactional
    public void applyDowngradeIfPremium(long bandId, Instant now, Instant graceUntil) {
        BandPlan plan = bandPlanRepository.findByBandIdForUpdate(bandId).orElse(null);
        if (plan == null || !plan.isPremium() || plan.isCouponPeriod()) {
            return;
        }
        plan.downgradeToFree(now);
        mediaDirectory.applyGracePeriodForBand(bandId, graceUntil);
    }

    /**
     * RTDN "환불·강제취소" 반영. <b>즉시</b> FREE 로 내리고 미디어 유예를 주지 않는다(만료 시각 = now).
     * 돈을 돌려줬으니 혜택도 바로 거둔다(BUILD_PLAN Phase 12). PREMIUM 이 아니면 no-op.
     */
    @Transactional
    public void applyRevoke(long bandId, Instant now) {
        BandPlan plan = bandPlanRepository.findByBandIdForUpdate(bandId).orElse(null);
        if (plan == null || !plan.isPremium()) {
            return;
        }
        plan.revokeToFree(now);
        mediaDirectory.applyGracePeriodForBand(bandId, now);
    }

    /**
     * 스토어 구독의 결제자를 적는다(LAUNCH_REVIEW B13). 요청의 토큰이 지금 이 밴드의 토큰일 때만 — {@link BandPlan#recordPurchaser}.
     * 검증 응답을 깨지 않도록 요금제가 없으면 조용히 넘어간다.
     */
    @Transactional
    public void recordPurchaser(long bandId, String purchaseToken, long userId) {
        bandPlanRepository.findByBandIdForUpdate(bandId)
                .ifPresent(plan -> plan.recordPurchaser(purchaseToken, userId));
    }

    /**
     * 탈퇴한 결제자의 구독 해지를 마친 뒤 결제자 연결을 끊는다 — 그 사이 토큰·결제자가 바뀌지 않았을 때만.
     * 커밋이 끝난 탈퇴 트랜잭션의 {@code afterCommit} 에서도 부르므로 새 트랜잭션으로 연다(이미 커밋된
     * 트랜잭션에 참여하면 쓰기가 반영되지 않는다).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forgetCanceledPurchaser(long bandId, String purchaseToken, long userId) {
        bandPlanRepository.findByBandIdForUpdate(bandId)
                .filter(p -> purchaseToken.equals(p.getPurchaseToken())
                        && Long.valueOf(userId).equals(p.getPurchasedByUserId()))
                .ifPresent(BandPlan::forgetPurchaser);
    }

    private BandPlan requirePlan(long bandId) {
        return bandPlanRepository.findByBandIdForUpdate(bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
    }
}
