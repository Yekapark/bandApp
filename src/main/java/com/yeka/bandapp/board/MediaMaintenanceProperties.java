package com.yeka.bandapp.board;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 미디어 정리 배치 설정. {@code app.media.*}.
 *
 * <p>{@code expireCron}/{@code orphanCron}은 {@code @Scheduled}가 프로퍼티 문자열로 직접 참조하므로
 * 여기서는 다루지 않는다({@code RecurringProperties}와 같은 방식). {@code zone}은 {@code @Scheduled(zone=...)}용,
 * {@code orphanAge}는 "이 시간 이상 PENDING 이면 고아"의 기준이다.
 */
@ConfigurationProperties(prefix = "app.media")
public record MediaMaintenanceProperties(String zone, Duration orphanAge) {

    public MediaMaintenanceProperties {
        if (zone == null || zone.isBlank()) {
            zone = "Asia/Seoul";
        }
        if (orphanAge == null || orphanAge.compareTo(Duration.ofMinutes(1)) < 0) {
            orphanAge = Duration.ofHours(1);
        }
    }
}
