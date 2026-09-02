package com.yeka.bandapp.plan.service;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.plan.entity.BandPlan;
import com.yeka.bandapp.plan.entity.PlanTier;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 다른 도메인(게시판 미디어 등)이 밴드 요금제를 읽을 때 쓰는 창구. 도메인 간 참조는 저장소가 아니라 이
 * 서비스를 통한다(코딩 컨벤션). {@link com.yeka.bandapp.room.service.RoomDirectoryService} 와 같은 역할.
 *
 * <p>엔티티를 반환하지 않는다 — 필요한 값만 {@link PlanView} 로 추려서 준다.
 */
@Service
public class PlanDirectoryService {

    private static final Logger log = LoggerFactory.getLogger(PlanDirectoryService.class);

    private final BandPlanRepository bandPlanRepository;

    public PlanDirectoryService(BandPlanRepository bandPlanRepository) {
        this.bandPlanRepository = bandPlanRepository;
    }

    /**
     * 업로드 완료 시각 기준 미디어 보관기한. PREMIUM 이면 {@code null}(무제한).
     *
     * <p>요금제 행이 없으면(있어서는 안 되지만 — 백필+생성 시 provisioning 으로 커버) 미디어 업로드가
     * 깨지지 않도록 FREE/30 으로 폴백하고 {@code warn} 로그만 남긴다.
     */
    @Transactional(readOnly = true)
    public Instant mediaExpiresAt(long bandId, Instant uploadedAt) {
        BandPlan plan = bandPlanRepository.findByBandId(bandId).orElse(null);
        if (plan == null) {
            log.warn("밴드 {} 의 요금제 행이 없어 FREE/{}일로 폴백한다", bandId, MediaRetention.FREE_RETENTION_DAYS);
            return MediaRetention.expiresAt(uploadedAt, MediaRetention.FREE_RETENTION_DAYS);
        }
        return MediaRetention.expiresAt(uploadedAt, plan.retentionDaysOrNull());
    }

    /** 밴드의 현재 요금제 요약. 행이 없으면 {@code PLAN_NOT_FOUND}. */
    @Transactional(readOnly = true)
    public PlanView currentPlan(long bandId) {
        return bandPlanRepository.findByBandId(bandId)
                .map(PlanView::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
    }

    /** 요금제 표시용 요약. 컨트롤러 응답과 테스트가 쓴다. */
    public record PlanView(PlanTier tier, Integer mediaRetentionDays, Instant startedAt, Instant expiresAt) {

        static PlanView from(BandPlan plan) {
            return new PlanView(plan.getTier(), plan.retentionDaysOrNull(), plan.getStartedAt(),
                    plan.getExpiresAt());
        }
    }
}
