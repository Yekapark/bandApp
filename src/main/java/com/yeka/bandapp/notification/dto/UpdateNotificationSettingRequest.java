package com.yeka.bandapp.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 알림 설정 변경(PUT 전체 교체). {@code reminderOffsets}를 보내지 않거나 빈 배열이면 "리마인더 없음"이 된다.
 * 값의 범위·개수 상한 검증은 {@code ReminderOffsets.normalize}가 서비스에서 수행한다(400).
 */
public record UpdateNotificationSettingRequest(
        @Schema(description = "푸시 알림 전체 on/off", example = "true")
        boolean pushEnabled,

        @Schema(description = "일정 시작 N분 전 리마인더 시점(분). 중복·순서는 서버가 정리한다.", example = "[10, 60]")
        @Size(max = 20, message = "리마인더 시점이 너무 많습니다.")
        List<@NotNull Integer> reminderOffsets
) {
}
