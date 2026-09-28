package com.yeka.bandapp.plan.service;

import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.plan.dto.PlanResponse;
import com.yeka.bandapp.plan.entity.BandPlan;
import com.yeka.bandapp.plan.entity.PlanCoupon;
import com.yeka.bandapp.plan.entity.PlanCouponRedemption;
import com.yeka.bandapp.plan.entity.Store;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway;
import com.yeka.bandapp.plan.gateway.StoreBillingUnavailableException;
import com.yeka.bandapp.plan.gateway.StoreDeferRejectedException;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import com.yeka.bandapp.plan.repository.PlanCouponRedemptionRepository;
import com.yeka.bandapp.plan.repository.PlanCouponRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * PREMIUM 맛보기 쿠폰 사용. 발급은 운영자가 SQL 로 한다(앱 전역 관리자 역할이 없다 — V12 주석 참조).
 *
 * <p><b>두 경로가 있다.</b>
 * <ul>
 *   <li><b>일반</b>(FREE, 쿠폰 PREMIUM, 해지 없이 끝난 밴드 등) — 외부 호출 없이 한 트랜잭션에서 사용 기록·횟수 차감·
 *       기간 연장을 함께 한다. 따로 커밋되면 중간에 실패했을 때 "쓴 걸로 기록됐는데 기간은 안 늘어난" 상태가 남는다.
 *   <li><b>스토어 결제 중</b> — 쿠폰 기간을 결제 기간에 <b>쌓는다</b>(LAUNCH_REVIEW B7). 우리 DB 만 늘리면 다음 갱신
 *       알림이 스토어 만료일로 덮어써 쿠폰 기간이 사라지므로, 스토어에 결제일 연기(defer)를 요청해 스토어 날짜를 옮긴다.
 *       외부 호출이라 트랜잭션 밖에서 해야 해서(CLAUDE.md) 순서를 나눈다: ① 사용 기록·횟수 차감을 먼저 커밋(중복 사용
 *       방어는 그대로) → ② 스토어 연기 → ③ 성공하면 스토어가 준 새 만료일로 맞추고, 실패하면 ①을 되돌린다.
 * </ul>
 *
 * <p>중복 사용 방어는 세 겹이다: ① 밴드별 유니크({@code ux_plan_coupon_redemptions})가 같은 밴드의
 * 재사용을 막고, ② 계정별 유니크({@code ux_plan_coupon_redemptions_user}, V18)가 한 사람이 밴드를
 * 여러 개 만들어 같은 코드를 반복 사용하는 것을 막고 — 밴드 생성에 개수 제한이 없어서 이게 없으면
 * 코드 한 장이 새는 순간 한 사람이 {@code max_uses} 를 혼자 다 태울 수 있다 —, ③ 남은 횟수 검사를
 * WHERE 에 넣은 조건부 UPDATE 가 여러 밴드의 마지막 한 장 경합을 막는다.
 * ①②의 유니크 위반은 모두 {@code COUPON_ALREADY_USED} 로 옮긴다(CLAUDE.md 규칙).
 */
@Service
public class PlanCouponService {

    private static final Logger log = LoggerFactory.getLogger(PlanCouponService.class);

    private final BandAccessGuard accessGuard;
    private final BandPlanRepository bandPlanRepository;
    private final PlanCouponRepository couponRepository;
    private final PlanCouponRedemptionRepository redemptionRepository;
    private final PlanMutationService planMutationService;
    private final StoreBillingGateway billingGateway;
    private final TransactionTemplate tx;

    public PlanCouponService(BandAccessGuard accessGuard, BandPlanRepository bandPlanRepository,
                             PlanCouponRepository couponRepository,
                             PlanCouponRedemptionRepository redemptionRepository,
                             PlanMutationService planMutationService,
                             StoreBillingGateway billingGateway,
                             PlatformTransactionManager transactionManager) {
        this.accessGuard = accessGuard;
        this.bandPlanRepository = bandPlanRepository;
        this.couponRepository = couponRepository;
        this.redemptionRepository = redemptionRepository;
        this.planMutationService = planMutationService;
        this.billingGateway = billingGateway;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * 쿠폰을 써서 PREMIUM 기간을 얻는다. 밴드장만.
     *
     * <p>이미 PREMIUM 이면 <b>남은 기간에 더한다</b> — 쿠폰을 쓴 사람이 손해 보지 않게. FREE 면 지금부터 시작한다.
     * 스토어 결제 중이면 스토어 결제일을 쿠폰 일수만큼 미룬다(다음 청구도 그만큼 늦어진다).
     */
    public PlanResponse redeem(long bandId, long userId, String rawCode) {
        accessGuard.requireLeader(bandId, userId);

        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        BandPlan snapshot = bandPlanRepository.findByBandId(bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        if (isPayingThroughStore(snapshot)) {
            return redeemOntoStoreSubscription(bandId, userId, code, snapshot.getPurchaseToken());
        }
        return tx.execute(status -> {
            Instant now = Instant.now();
            PlanCoupon coupon = reserve(code, bandId, userId, now);
            BandPlan current = bandPlanRepository.findByBandId(bandId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
            Instant base = (current.getExpiresAt() != null && current.getExpiresAt().isAfter(now))
                    ? current.getExpiresAt()
                    : now;
            Instant newEnd = base.plus(coupon.getGrantDays(), ChronoUnit.DAYS);

            // FREE 인데 스토어 토큰이 남아 있으면(계정 보류 뒤 — B1) 그대로 둔다. 지우면 보류가 풀려 RECOVERED 가
            // 와도 밴드를 못 찾는다. 쿠폰 표시(coupon-)가 붙으니 자동 갱신 구독으로 보지는 않는다.
            BandPlan updated = current.isFree()
                    ? planMutationService.applyUpgrade(bandId, now, newEnd,
                            BandPlan.COUPON_REF_PREFIX + coupon.getCode(), current.getStore(), current.getPurchaseToken())
                    : planMutationService.applyRenew(bandId, now, newEnd);
            return PlanResponse.from(updated);
        });
    }

    /** 스토어로 결제 중인 PREMIUM(쿠폰 기간이 아닌) — 쿠폰 기간을 스토어 결제일에 쌓아야 하는 밴드. */
    private static boolean isPayingThroughStore(BandPlan plan) {
        return plan.isPremium() && plan.getStore() == Store.GOOGLE_PLAY && plan.getPurchaseToken() != null
                && !plan.isCouponPeriod();
    }

    private PlanResponse redeemOntoStoreSubscription(long bandId, long userId, String code, String purchaseToken) {
        // ① 사용 기록·횟수 차감을 먼저 커밋한다 — 중복 사용·마지막 한 장 경합 방어가 그대로 걸린다.
        PlanCoupon coupon = tx.execute(status -> reserve(code, bandId, userId, Instant.now()));

        // ② 트랜잭션 밖에서 스토어 결제일을 미룬다.
        Instant newExpiry;
        try {
            newExpiry = billingGateway.defer(Store.GOOGLE_PLAY, purchaseToken,
                    Duration.ofDays(coupon.getGrantDays()));
        } catch (StoreBillingUnavailableException unavailable) {
            log.warn("쿠폰을 스토어 결제일에 쌓지 못함(일시 장애) — 사용 기록을 되돌린다 bandId={}", bandId, unavailable);
            undoReservation(coupon.getId(), bandId);
            throw new BusinessException(ErrorCode.COUPON_STORE_UNAVAILABLE);
        } catch (StoreDeferRejectedException rejected) {
            log.warn("스토어가 결제일 연기를 거절 — 사용 기록을 되돌린다 bandId={}", bandId, rejected);
            undoReservation(coupon.getId(), bandId);
            throw new BusinessException(ErrorCode.COUPON_STORE_REJECTED);
        }

        // ③ 스토어가 정한 새 만료일로 맞춘다 — 다음 갱신 알림이 같은 날짜를 가져온다.
        log.info("쿠폰 {}일을 스토어 결제일에 쌓음 bandId={} newExpiry={}", coupon.getGrantDays(), bandId, newExpiry);
        return PlanResponse.from(planMutationService.applyRenew(bandId, Instant.now(), newExpiry));
    }

    /** 쿠폰 확인 + 사용 기록 + 횟수 차감. 호출자의 트랜잭션 안에서 돈다. */
    private PlanCoupon reserve(String code, long bandId, long userId, Instant now) {
        PlanCoupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        if (!coupon.isUsable(now)) {
            // 무효화된 쿠폰도 "기한이 지났다" 로 묶는다 — 존재 여부 외에 상태를 더 알려줄 이유가 없다.
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
        if (coupon.isExhausted()) {
            throw new BusinessException(ErrorCode.COUPON_EXHAUSTED);
        }

        // 이 밴드·이 계정이 이미 썼는지 먼저 본다 — 소진되지도 않았는데 남의 횟수를 깎지 않도록.
        recordRedemption(coupon.getId(), bandId, userId, now);

        if (couponRepository.consume(coupon.getId()) == 0) {
            // 마지막 한 장을 다른 밴드가 먼저 가져갔다. 트랜잭션이 롤백되며 위 사용 기록도 함께 사라진다.
            throw new BusinessException(ErrorCode.COUPON_EXHAUSTED);
        }
        return coupon;
    }

    private void undoReservation(long couponId, long bandId) {
        tx.executeWithoutResult(status -> {
            redemptionRepository.deleteByCouponIdAndBandId(couponId, bandId);
            couponRepository.release(couponId);
        });
    }

    private void recordRedemption(long couponId, long bandId, long userId, Instant now) {
        try {
            redemptionRepository.saveAndFlush(
                    PlanCouponRedemption.of(couponId, bandId, userId, now));
        } catch (DataIntegrityViolationException duplicate) {
            throw new BusinessException(ErrorCode.COUPON_ALREADY_USED);
        }
    }
}
