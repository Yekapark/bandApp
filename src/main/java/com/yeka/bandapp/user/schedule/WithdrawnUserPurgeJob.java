package com.yeka.bandapp.user.schedule;

import com.yeka.bandapp.user.WithdrawalProperties;
import com.yeka.bandapp.user.service.UserAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 탈퇴 후 보관기간이 지난 계정의 개인정보를 파기하는 야간 배치.
 * cron/zone은 {@code app.withdrawal.*}에서 온다. 테스트에서는 {@code purge-cron="-"}로 비활성화한다.
 */
@Component
public class WithdrawnUserPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(WithdrawnUserPurgeJob.class);
    private static final int MAX_BATCHES = 100;

    private final UserAccountService userAccountService;
    private final WithdrawalProperties properties;

    public WithdrawnUserPurgeJob(UserAccountService userAccountService, WithdrawalProperties properties) {
        this.userAccountService = userAccountService;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.withdrawal.purge-cron}", zone = "${app.withdrawal.purge-zone}")
    public void purge() {
        Instant threshold = Instant.now().minus(properties.retentionDays(), ChronoUnit.DAYS);
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int done = userAccountService.anonymizeWithdrawnBefore(threshold);
            total += done;
            if (done < UserAccountService.PURGE_BATCH_SIZE) {
                break;
            }
        }
        if (total > 0) {
            log.info("탈퇴 계정 개인정보 파기 완료 count={}", total);
        }
    }
}
