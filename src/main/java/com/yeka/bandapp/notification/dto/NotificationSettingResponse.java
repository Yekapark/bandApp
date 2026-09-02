package com.yeka.bandapp.notification.dto;

import com.yeka.bandapp.notification.entity.NotificationSetting;

import java.util.Arrays;
import java.util.List;

public record NotificationSettingResponse(boolean pushEnabled, List<Integer> reminderOffsets) {

    public static NotificationSettingResponse from(NotificationSetting setting) {
        return new NotificationSettingResponse(
                setting.isPushEnabled(),
                Arrays.stream(setting.getReminderOffsets()).boxed().toList());
    }
}
