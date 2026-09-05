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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 요금제 조회·전환 오케스트레이션.
 *
 * <p><b>{@code @Transactional} 없음</b> — 결제 게이트웨이 호출(외부 I/O 가능)이 트랜잭션 안에서
 * 커넥션을 붙잡지 않도록(CLAUDE.md 규칙). 게이트웨이를 트랜잭션 밖에서 먼저 끝내고, 확정된 값으로
 * {@link PlanMutationService} 의 짧은 트랜잭션(티어 플립 + 미디어 재계산)을 호출한다.
 *
 * <p>구독기간이 지난 PREMIUM 을 FREE 로 되돌리는 일은 {@link #expireOverdue(Instant)} 가 맡고,
 * {@code PlanExpirationJob} 이 매일 밤 호출한다. 실제 PG 연동과 무관하게 DB 상태만 정리하면 되는 일이라
 * 게이트웨이 어댑터를 기다리지 않는다.
 */
@Service
public class PlanService {

    /** 만료 강등 한 페이지 크기 ({@code MediaMaintenanceService} 와 같은 값). */
    public static final int PAGE_SIZE = 200;

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

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

    /**
     * 구독기간이 지난 PREMIUM 밴드를 FREE 로 되돌린다. <b>배치 전용</b> — 요청자가 없어
     * {@code accessGuard} 를 타지 않는다({@code PlanExpirationJob} 만 호출한다).
     *
     * <p><b>결제 게이트웨이를 호출하지 않는다.</b> 만료는 PG 쪽에서 이미 끝난 구독을 DB 에 반영하는
     * 것이라 취소를 보낼 대상이 없다. 게이트웨이를 타는 건 사용자가 직접 누르는 {@link #cancel} 뿐이다.
     *
     * <p>유예기간은 수동 해지와 같다({@code downgradeGraceDays}, 기본 30일) — 사용자에게
     * "해지든 만료든 30일" 로 설명이 단순해진다.
     *
     * <p>건별 try/catch 라 한 밴드의 실패가 나머지를 막지 않는다({@code MediaMaintenanceService} 관례).
     * 사용자가 같은 순간 해지를 눌러 이미 FREE 가 됐으면 {@code PLAN_ALREADY_FREE} 가 나고 여기서 삼킨다 —
     * {@code applyDowngrade} 가 행 잠금을 잡으므로 동시성 가드가 따라온다.
     *
     * @return FREE 로 강등한 밴드 수
     */
    public int expireOverdue(Instant now) {
        Instant graceUntil = now.plus(planProperties.downgradeGraceDays(), ChronoUnit.DAYS);
        List<Long> bandIds = bandPlanRepository.findExpiredPremiumBandIds(now, PageRequest.of(0, PAGE_SIZE));
        int done = 0;
        for (Long bandId : bandIds) {
            try {
                planMutationService.applyDowngrade(bandId, now, graceUntil);
                done++;
            } catch (RuntimeException e) {
                log.warn("요금제 만료 강등 실패 bandId={} — 다음 실행에서 재시도한다", bandId, e);
            }
        }
        return done;
    }

    private BandPlan requirePlan(long bandId) {
        return bandPlanRepository.findByBandId(bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
    }
}
