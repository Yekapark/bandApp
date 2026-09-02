package com.yeka.bandapp.board.schedule;

import com.yeka.bandapp.board.service.MediaMaintenanceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 배치 1(일 1회) — 보관기한이 지난 READY 미디어를 R2 에서 지우고 EXPIRED 로 돌린다.
 * cron/zone 은 {@code app.media.*}. 테스트에서는 {@code expire-cron="-"}로 비활성화하고
 * {@link MediaMaintenanceService#expireOverdue}를 직접 호출한다.
 */
@Component
public class MediaExpirationJob {

    private final MediaMaintenanceService mediaMaintenanceService;

    public MediaExpirationJob(MediaMaintenanceService mediaMaintenanceService) {
        this.mediaMaintenanceService = mediaMaintenanceService;
    }

    @Scheduled(cron = "${app.media.expire-cron}", zone = "${app.media.zone}")
    public void run() {
        mediaMaintenanceService.expireOverdue(Instant.now());
    }
}
