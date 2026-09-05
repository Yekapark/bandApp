package com.yeka.bandapp.plan.service;

import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.plan.dto.PlanResponse;
import com.yeka.bandapp.plan.entity.BandPlan;
import com.yeka.bandapp.plan.entity.PlanCoupon;
import com.yeka.bandapp.plan.entity.PlanCouponRedemption;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import com.yeka.bandapp.plan.repository.PlanCouponRedemptionRepository;
import com.yeka.bandapp.plan.repository.PlanCouponRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * PREMIUM 맛보기 쿠폰 사용. 발급은 운영자가 SQL 로 한다(앱 전역 관리자 역할이 없다 — V12 주석 참조).
 *
 * <p><b>결제 게이트웨이를 타지 않는다</b> — 쿠폰은 결제가 아니라 기간 부여다. 그래서 {@link PlanService}
 * 와 달리 외부 HTTP 가 전혀 없고, {@code @Transactional} 로 묶어도 커넥션을 왕복 시간 동안 붙잡을
 * 일이 없다. 오히려 <b>묶어야 한다</b> — 사용 기록·횟수 차감·티어 변경 셋이 따로 커밋되면 중간에
 * 실패했을 때 "쓴 걸로 기록됐는데 기간은 안 늘어난" 상태가 남는다.
 *
 * <p>중복 사용 방어는 두 겹이다: ① 밴드별 사용 기록의 유니크 제약({@code ux_plan_coupon_redemptions})
 * 이 같은 밴드의 재사용을 막고, ② 남은 횟수 검사를 WHERE 에 넣은 조건부 UPDATE 가 여러 밴드의
 * 마지막 한 장 경합을 막는다. 유니크 위반은 {@code COUPON_ALREADY_USED} 로 옮긴다(CLAUDE.md 규칙).
 */
@Service
public class PlanCouponService {

    private final BandAccessGuard accessGuard;
    private final BandPlanRepository bandPlanRepository;
    private final PlanCouponRepository couponRepository;
    private final PlanCouponRedemptionRepository redemptionRepository;
    private final PlanMutationService planMutationService;

    public PlanCouponService(BandAccessGuard accessGuard, BandPlanRepository bandPlanRepository,
                             PlanCouponRepository couponRepository,
                             PlanCouponRedemptionRepository redemptionRepository,
                             PlanMutationService planMutationService) {
        this.accessGuard = accessGuard;
        this.bandPlanRepository = bandPlanRepository;
        this.couponRepository = couponRepository;
        this.redemptionRepository = redemptionRepository;
        this.planMutationService = planMutationService;
    }

    /**
     * 쿠폰을 써서 PREMIUM 기간을 얻는다. 밴드장만.
     *
     * <p>이미 PREMIUM 이면 <b>남은 기간에 더한다</b> — 쿠폰을 쓴 사람이 손해 보지 않게. FREE 면
     * 지금부터 시작한다. 두 경우 모두 기준 시각만 다르고 계산은 하나다.
     */
    @Transactional
    public PlanResponse redeem(long bandId, long userId, String rawCode) {
        accessGuard.requireLeader(bandId, userId);

        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Instant now = Instant.now();
        PlanCoupon coupon = couponRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_NOT_FOUND));
        if (!coupon.isUsable(now)) {
            // 무효화된 쿠폰도 "기한이 지났다" 로 묶는다 — 존재 여부 외에 상태를 더 알려줄 이유가 없다.
            throw new BusinessException(ErrorCode.COUPON_EXPIRED);
        }
        if (coupon.isExhausted()) {
            throw new BusinessException(ErrorCode.COUPON_EXHAUSTED);
        }

        // 이 밴드가 이미 썼는지 먼저 본다 — 소진되지도 않았는데 남의 횟수를 깎지 않도록.
        recordRedemption(coupon.getId(), bandId, userId, now);

        if (couponRepository.consume(coupon.getId()) == 0) {
            // 마지막 한 장을 다른 밴드가 먼저 가져갔다. 트랜잭션이 롤백되며 위 사용 기록도 함께 사라진다.
            throw new BusinessException(ErrorCode.COUPON_EXHAUSTED);
        }

        BandPlan current = bandPlanRepository.findByBandId(bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        Instant base = (current.getExpiresAt() != null && current.getExpiresAt().isAfter(now))
                ? current.getExpiresAt()
                : now;
        Instant newEnd = base.plus(coupon.getGrantDays(), ChronoUnit.DAYS);

        BandPlan updated = current.isFree()
                ? planMutationService.applyUpgrade(bandId, now, newEnd, "coupon-" + coupon.getCode())
                : planMutationService.applyRenew(bandId, now, newEnd);
        return PlanResponse.from(updated);
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
