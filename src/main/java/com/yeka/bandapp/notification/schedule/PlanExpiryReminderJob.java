package com.yeka.bandapp.notification.schedule;

import com.yeka.bandapp.notification.service.PlanExpiryReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * PREMIUM 만료 예고 배치(일 1회). cron/zone 은 {@code app.plan.*}. 테스트에서는
 * {@code expiry-reminder-cron="-"}로 비활성화하고
 * {@link PlanExpiryReminderService#remindExpiringSoon}을 직접 호출한다.
 *
 * <p>강등 배치({@code PlanExpirationJob})와 <b>일부러 나눠 뒀다.</b> 하나는 "미리 알리는 일",
 * 다른 하나는 "실제로 내리는 일"이다. 한 배치에 묶으면 예고 로직을 손볼 때 강등까지 건드리게 된다.
 * 예고가 먼저 돌도록 강등보다 이른 시각에 잡는다.
 */
@Component
public class PlanExpiryReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PlanExpiryReminderJob.class);

    private final PlanExpiryReminderService reminderService;

    public PlanExpiryReminderJob(PlanExpiryReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(cron = "${app.plan.expiry-reminder-cron}", zone = "${app.plan.zone}")
    public void run() {
        int sent = reminderService.remindExpiringSoon(Instant.now());
        if (sent > 0) {
            log.info("요금제 만료 예고 발송 완료 sent={}", sent);
        }
    }
}
