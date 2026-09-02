package com.yeka.bandapp.plan.service;

import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.plan.config.PlanProperties;
import com.yeka.bandapp.plan.dto.PlanResponse;
import com.yeka.bandapp.plan.entity.BandPlan;
import com.yeka.bandapp.plan.gateway.PaymentGateway;
import com.yeka.bandapp.plan.gateway.PaymentGateway.CancelCommand;
import com.yeka.bandapp.plan.gateway.PaymentGateway.CancellationResult;
import com.yeka.bandapp.plan.gateway.PaymentGateway.RenewCommand;
import com.yeka.bandapp.plan.gateway.PaymentGateway.SubscribeCommand;
import com.yeka.bandapp.plan.gateway.PaymentGateway.SubscriptionResult;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 요금제 조회·전환 오케스트레이션.
 *
 * <p><b>{@code @Transactional} 없음</b> — 결제 게이트웨이 호출(외부 I/O 가능)이 트랜잭션 안에서
 * 커넥션을 붙잡지 않도록(CLAUDE.md 규칙). 게이트웨이를 트랜잭션 밖에서 먼저 끝내고, 확정된 값으로
 * {@link PlanMutationService} 의 짧은 트랜잭션(티어 플립 + 미디어 재계산)을 호출한다.
 *
 * <p>TODO(PG 어댑터): {@code band_plans.expires_at} 가 지난 PREMIUM 밴드를 자동으로 FREE 로 되돌리는
 * 처리는 실제 PG 연동(웹훅/정산) 시 함께 구현한다. no-op 게이트웨이는 구독을 만료시키지 않는다.
 */
@Service
public class PlanService {

    private final BandAccessGuard accessGuard;
    private final BandPlanRepository bandPlanRepository;
    private final PlanDirectoryService planDirectory;
    private final PlanMutationService planMutationService;
    private final PaymentGateway paymentGateway;
    private final PlanProperties planProperties;

    public PlanService(BandAccessGuard accessGuard, BandPlanRepository bandPlanRepository,
                       PlanDirectoryService planDirectory, PlanMutationService planMutationService,
                       PaymentGateway paymentGateway, PlanProperties planProperties) {
        this.accessGuard = accessGuard;
        this.bandPlanRepository = bandPlanRepository;
        this.planDirectory = planDirectory;
        this.planMutationService = planMutationService;
        this.paymentGateway = paymentGateway;
        this.planProperties = planProperties;
    }

    /** 현재 요금제 조회. 밴드 멤버면 누구나. */
    public PlanResponse view(long bandId, long userId) {
        accessGuard.requireActiveMember(bandId, userId);
        return PlanResponse.from(planDirectory.currentPlan(bandId));
    }

    /** FREE → PREMIUM. 밴드장만. 이미 PREMIUM 이면 409, 결제 실패면 402. */
    public PlanResponse subscribe(long bandId, long userId) {
        accessGuard.requireLeader(bandId, userId);
        BandPlan current = requirePlan(bandId);
        if (current.isPremium()) {
            throw new BusinessException(ErrorCode.PLAN_ALREADY_PREMIUM);
        }

        Instant now = Instant.now();
        SubscriptionResult result = paymentGateway.subscribe(
                new SubscribeCommand(bandId, userId, planProperties.planCode(), now));
        if (!result.success()) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }

        Instant periodEnd = result.currentPeriodEnd() != null
                ? result.currentPeriodEnd()
                : now.plus(planProperties.premiumPeriodDays(), ChronoUnit.DAYS);
        BandPlan updated = planMutationService.applyUpgrade(bandId, now, periodEnd, result.subscriptionRef());
        return PlanResponse.from(updated);
    }

    /** PREMIUM → FREE. 밴드장만. 이미 FREE 이면 409. 기존 미디어에 유예기간(기본 30일)을 준다. */
    public PlanResponse cancel(long bandId, long userId) {
        accessGuard.requireLeader(bandId, userId);
        BandPlan current = requirePlan(bandId);
        if (current.isFree()) {
            throw new BusinessException(ErrorCode.PLAN_ALREADY_FREE);
        }

        Instant now = Instant.now();
        CancellationResult result = paymentGateway.cancel(
                new CancelCommand(bandId, current.getSubscriptionRef(), now));
        if (!result.success()) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }

        Instant graceUntil = now.plus(planProperties.downgradeGraceDays(), ChronoUnit.DAYS);
        BandPlan updated = planMutationService.applyDowngrade(bandId, now, graceUntil);
        return PlanResponse.from(updated);
    }

    /** PREMIUM 구독기간 연장. 밴드장만. FREE 이면 409. */
    public PlanResponse renew(long bandId, long userId) {
        accessGuard.requireLeader(bandId, userId);
        BandPlan current = requirePlan(bandId);
        if (current.isFree()) {
            throw new BusinessException(ErrorCode.PLAN_ALREADY_FREE);
        }

        Instant now = Instant.now();
        SubscriptionResult result = paymentGateway.renew(
                new RenewCommand(bandId, current.getSubscriptionRef(), now));
        if (!result.success()) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }

        Instant periodEnd = result.currentPeriodEnd() != null
                ? result.currentPeriodEnd()
                : now.plus(planProperties.premiumPeriodDays(), ChronoUnit.DAYS);
        BandPlan updated = planMutationService.applyRenew(bandId, now, periodEnd);
        return PlanResponse.from(updated);
    }

    private BandPlan requirePlan(long bandId) {
        return bandPlanRepository.findByBandId(bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
    }
}
