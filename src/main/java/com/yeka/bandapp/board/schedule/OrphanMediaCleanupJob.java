package com.yeka.bandapp.board.schedule;

import com.yeka.bandapp.board.MediaMaintenanceProperties;
import com.yeka.bandapp.board.service.MediaMaintenanceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 배치 2(시간당) — 콜백이 오지 않아 {@code app.media.orphan-age}(기본 1시간) 이상 PENDING 인 고아 첨부를
 * 정리한다. cron/zone 은 {@code app.media.*}. 테스트에서는 {@code orphan-cron="-"}로 비활성화하고
 * {@link MediaMaintenanceService#cleanupOrphans}를 직접 호출한다.
 */
@Component
public class OrphanMediaCleanupJob {

    private final MediaMaintenanceService mediaMaintenanceService;
    private final MediaMaintenanceProperties properties;

    public OrphanMediaCleanupJob(MediaMaintenanceService mediaMaintenanceService,
                                 MediaMaintenanceProperties properties) {
        this.mediaMaintenanceService = mediaMaintenanceService;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.media.orphan-cron}", zone = "${app.media.zone}")
    public void run() {
        mediaMaintenanceService.cleanupOrphans(Instant.now().minus(properties.orphanAge()));
    }
}
