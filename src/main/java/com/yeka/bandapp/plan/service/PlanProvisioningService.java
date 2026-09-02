package com.yeka.bandapp.plan.service;

import com.yeka.bandapp.plan.entity.BandPlan;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 밴드 생성 시 기본 FREE 요금제 행을 만드는 창구. {@code BandService.create} 가 호출한다 —
 * 도메인 간 참조는 저장소가 아니라 이 서비스를 통한다(코딩 컨벤션).
 */
@Service
public class PlanProvisioningService {

    private final BandPlanRepository bandPlanRepository;

    public PlanProvisioningService(BandPlanRepository bandPlanRepository) {
        this.bandPlanRepository = bandPlanRepository;
    }

    /** 밴드의 기본 FREE 플랜을 만든다. 호출자({@code BandService.create})의 트랜잭션에 합류한다. */
    @Transactional
    public void createDefaultPlan(long bandId, Instant now) {
        bandPlanRepository.save(BandPlan.freePlan(bandId, now));
    }
}
