package com.yeka.bandapp.plan.service;

import com.yeka.bandapp.board.service.MediaDirectoryService;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.plan.entity.BandPlan;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 요금제 티어 전환의 DB 쓰기 단위. 각 메서드는 하나의 {@code @Transactional} 안에서
 * {@code BandPlan} 엔티티를 수정하고 밴드 미디어 보관기한을 재계산한다 — 티어 플립과 미디어
 * 재계산이 원자적으로 커밋된다.
 *
 * <p>결제 게이트웨이 호출은 이 서비스가 아니라 {@link PlanService} 가 <b>트랜잭션 밖에서</b> 먼저 끝낸다.
 * 그래서 이 메서드들 안에는 외부 I/O 가 없고, 한 트랜잭션으로 묶는 것이 안전하다.
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
     */
    @Transactional
    public BandPlan applyUpgrade(long bandId, Instant now, Instant periodEnd, String subscriptionRef) {
        BandPlan plan = requirePlan(bandId);
        if (!plan.isFree()) {
            throw new BusinessException(ErrorCode.PLAN_ALREADY_PREMIUM);
        }
        plan.upgradeToPremium(now, periodEnd, subscriptionRef);
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

    private BandPlan requirePlan(long bandId) {
        return bandPlanRepository.findByBandIdForUpdate(bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
    }
}
