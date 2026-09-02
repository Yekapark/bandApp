package com.yeka.bandapp.notification.schedule;

import com.yeka.bandapp.notification.service.AttendanceNudgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 참석 미응답 독촉 배치(일 1회). cron/zone 은 {@code app.notification.*}. 테스트에서는
 * {@code nudge-cron="-"}로 비활성화하고 {@link AttendanceNudgeService#runOnce}를 직접 호출한다.
 */
@Component
public class AttendanceNudgeJob {

    private static final Logger log = LoggerFactory.getLogger(AttendanceNudgeJob.class);

    private final AttendanceNudgeService attendanceNudgeService;

    public AttendanceNudgeJob(AttendanceNudgeService attendanceNudgeService) {
        this.attendanceNudgeService = attendanceNudgeService;
    }

    @Scheduled(cron = "${app.notification.nudge-cron}", zone = "${app.notification.zone}")
    public void run() {
        int sent = attendanceNudgeService.runOnce(Instant.now());
        if (sent > 0) {
            log.info("참석 미응답 독촉 완료 sent={}", sent);
        }
    }
}
