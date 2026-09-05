package com.yeka.bandapp.plan.schedule;

import com.yeka.bandapp.plan.service.PlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 구독기간이 지난 PREMIUM 밴드를 FREE 로 되돌리는 야간 배치.
 * cron/zone 은 {@code app.plan.*} 에서 온다. 테스트에서는 {@code expire-cron="-"} 로 비활성화한다
 * ({@code WithdrawnUserPurgeJob} 선례).
 */
@Component
public class PlanExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(PlanExpirationJob.class);
    private static final int MAX_BATCHES = 100;

    private final PlanService planService;

    public PlanExpirationJob(PlanService planService) {
        this.planService = planService;
    }

    @Scheduled(cron = "${app.plan.expire-cron}", zone = "${app.plan.zone}")
    public void expire() {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int done = planService.expireOverdue(Instant.now());
            total += done;
            if (done < PlanService.PAGE_SIZE) {
                break;
            }
        }
        if (total > 0) {
            log.info("PREMIUM 구독기간 만료 강등 완료 count={}", total);
        }
    }
}
