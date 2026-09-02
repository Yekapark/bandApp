package com.yeka.bandapp.notification.schedule;

import com.yeka.bandapp.notification.NotificationProperties;
import com.yeka.bandapp.notification.service.NotificationSender;
import com.yeka.bandapp.notification.service.ReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 일정 리마인더 발송 배치. cron/zone 은 {@code app.notification.*}에서 온다. 테스트에서는
 * {@code reminder-cron="-"}로 비활성화하고 {@link ReminderService#runOnce}를 직접 호출해 검증한다
 * ({@code RecurringExtensionJob} 선례). 단일 VM 전제라 분산 락은 없다({@code SchedulingConfig}).
 *
 * <p>실행 끝에 보관기한이 지난 발송 이력도 함께 정리한다(별도 배치를 늘리지 않는다).
 */
@Component
public class ReservationReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationReminderJob.class);

    private final ReminderService reminderService;
    private final NotificationSender notificationSender;
    private final NotificationProperties properties;

    public ReservationReminderJob(ReminderService reminderService,
                                  NotificationSender notificationSender,
                                  NotificationProperties properties) {
        this.reminderService = reminderService;
        this.notificationSender = notificationSender;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.notification.reminder-cron}", zone = "${app.notification.zone}")
    public void run() {
        Instant now = Instant.now();
        int sent = reminderService.runOnce(now);
        int purged = notificationSender.purgeDispatchesBefore(
                now.minus(Duration.ofDays(properties.dispatchRetentionDays())));
        if (sent > 0 || purged > 0) {
            log.info("일정 리마인더 완료 sent={} 이력정리={}", sent, purged);
        }
    }
}
