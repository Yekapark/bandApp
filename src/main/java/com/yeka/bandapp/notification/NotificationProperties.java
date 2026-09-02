package com.yeka.bandapp.notification;

import com.yeka.bandapp.notification.service.ReminderOffsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * 알림·리마인더 설정. {@code app.notification.*}.
 *
 * <p>{@code reminderCron}/{@code nudgeCron}은 {@code @Scheduled}가 프로퍼티 문자열로 직접 참조하므로
 * 여기서는 다루지 않는다({@code RecurringProperties}·{@code WithdrawalProperties}와 같은 방식).
 * {@code zone}은 {@code @Scheduled(zone=...)}와 이 클래스 양쪽에서 쓴다.
 */
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
        String zone,
        String defaultReminderOffsets,
        int maxReminderOffsetMinutes,
        int maxReminderOffsets,
        int nudgeLeadHours,
        int dispatchRetentionDays
) {

    public NotificationProperties {
        if (zone == null || zone.isBlank()) {
            zone = "Asia/Seoul";
        }
        if (defaultReminderOffsets == null || defaultReminderOffsets.isBlank()) {
            defaultReminderOffsets = "60";
        }
        if (maxReminderOffsetMinutes <= 0) {
            maxReminderOffsetMinutes = 1440;
        }
        if (maxReminderOffsets <= 0) {
            maxReminderOffsets = 5;
        }
        if (nudgeLeadHours <= 0) {
            nudgeLeadHours = 24;
        }
        if (dispatchRetentionDays <= 0) {
            dispatchRetentionDays = 30;
        }
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }

    /** 설정 행이 없는 사용자에게 적용할 기본 리마인더 시점(분). */
    public int[] defaultReminderOffsetsParsed() {
        return ReminderOffsets.parseCsv(defaultReminderOffsets, maxReminderOffsetMinutes, maxReminderOffsets);
    }
}
