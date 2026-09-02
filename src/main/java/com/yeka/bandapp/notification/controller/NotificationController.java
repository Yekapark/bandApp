package com.yeka.bandapp.notification.controller;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.notification.dto.NotificationSettingResponse;
import com.yeka.bandapp.notification.dto.RegisterDeviceTokenRequest;
import com.yeka.bandapp.notification.dto.UpdateNotificationSettingRequest;
import com.yeka.bandapp.notification.service.DeviceTokenService;
import com.yeka.bandapp.notification.service.NotificationSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 푸시 알림 설정과 디바이스 토큰. Bearer 인증 필요(모든 엔드포인트가 토큰 주인 것만 다룬다).
 * FCM 키가 없어도 이 API 는 정상 동작한다 — 발송만 나중에 붙는다.
 */
@Tag(name = "13. 알림", description = "FCM 디바이스 토큰 등록/해제, 푸시 on/off 와 리마인더 시점 설정.")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final DeviceTokenService deviceTokenService;
    private final NotificationSettingService notificationSettingService;

    public NotificationController(DeviceTokenService deviceTokenService,
                                 NotificationSettingService notificationSettingService) {
        this.deviceTokenService = deviceTokenService;
        this.notificationSettingService = notificationSettingService;
    }

    @Operation(summary = "디바이스 토큰 등록/갱신",
            description = "같은 토큰이 이미 있으면 소유자·플랫폼을 갱신한다(upsert). 계정당 분당 호출 상한이 있다"
                    + "(초과 429 TOO_MANY_REQUESTS).")
    @PostMapping("/device-tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> registerDeviceToken(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody RegisterDeviceTokenRequest request) {
        deviceTokenService.register(principal.userId(), request.token(), request.platform());
        return ApiResponse.ok();
    }

    @Operation(summary = "디바이스 토큰 해제",
            description = "본인이 등록한 토큰만 해제할 수 있다. 매칭되는 토큰이 없으면 404 DEVICE_TOKEN_NOT_FOUND. "
                    + "DELETE 가 본문을 싣지 못해 토큰은 쿼리 파라미터로 받는다.")
    @DeleteMapping("/device-tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregisterDeviceToken(@AuthenticationPrincipal AuthPrincipal principal,
                                      @RequestParam String token) {
        deviceTokenService.unregister(principal.userId(), token);
    }

    @Operation(summary = "내 알림 설정 조회",
            description = "설정이 없으면 기본값(pushEnabled=true, reminderOffsets=[60])을 만들어 반환한다.")
    @GetMapping("/settings")
    public ApiResponse<NotificationSettingResponse> getSettings(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(notificationSettingService.getOrCreate(principal.userId()));
    }

    @Operation(summary = "내 알림 설정 변경",
            description = "PUT 전체 교체. reminderOffsets 는 일정 시작 N분 전(분) 값들이며 중복·순서는 서버가 정리한다. "
                    + "값이 1 미만이거나 상한(기본 1440분) 초과면 400 INVALID_REMINDER_OFFSET, 개수가 상한(기본 5개) "
                    + "초과면 400 TOO_MANY_REMINDER_OFFSETS. 빈 배열이면 리마인더를 받지 않는다.")
    @PutMapping("/settings")
    public ApiResponse<NotificationSettingResponse> updateSettings(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateNotificationSettingRequest request) {
        return ApiResponse.ok(notificationSettingService.update(
                principal.userId(), request.pushEnabled(), request.reminderOffsets()));
    }
}
