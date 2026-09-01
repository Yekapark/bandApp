package com.yeka.bandapp.recurring.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * 정기 일정 설정. {@code app.recurring.*}.
 *
 * <p>{@code extendCron}은 {@code @Scheduled}가 프로퍼티 문자열로 직접 참조하므로 여기서는 다루지 않는다
 * ({@code WithdrawalProperties}와 같은 방식). {@code zone}은 {@code @Scheduled(zone=...)}와 회차 시각
 * 계산 양쪽에서 쓰므로 여기서도 노출한다.
 */
@ConfigurationProperties(prefix = "app.recurring")
public record RecurringProperties(String zone, int horizonWeeks) {

    public RecurringProperties {
        if (zone == null || zone.isBlank()) {
            zone = "Asia/Seoul";
        }
        if (horizonWeeks <= 0) {
            horizonWeeks = 8;
        }
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
