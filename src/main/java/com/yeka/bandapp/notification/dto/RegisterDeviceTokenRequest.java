package com.yeka.bandapp.notification.dto;

import com.yeka.bandapp.notification.entity.DevicePlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterDeviceTokenRequest(
        @Schema(description = "FCM 등록 토큰", example = "fcm-token-abc123")
        @NotBlank @Size(max = 255) String token,

        @Schema(description = "IOS | ANDROID | WEB", example = "ANDROID")
        @NotNull DevicePlatform platform
) {
}
