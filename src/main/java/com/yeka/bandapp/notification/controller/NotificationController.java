package com.yeka.bandapp.notification.controller;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.notification.dto.NotificationFeedResponse;
import com.yeka.bandapp.notification.dto.NotificationSettingResponse;
import com.yeka.bandapp.notification.dto.RegisterDeviceTokenRequest;
import com.yeka.bandapp.notification.dto.UpdateNotificationSettingRequest;
import com.yeka.bandapp.notification.service.DeviceTokenService;
import com.yeka.bandapp.notification.service.NotificationFeedService;
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
@Tag(name = "13. 알림",
        description = "받은 알림 목록, FCM 디바이스 토큰 등록/해제, 푸시 on/off 와 리마인더 시점 설정.")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final DeviceTokenService deviceTokenService;
    private final NotificationSettingService notificationSettingService;
    private final NotificationFeedService notificationFeedService;

    public NotificationController(DeviceTokenService deviceTokenService,
                                 NotificationSettingService notificationSettingService,
                                 NotificationFeedService notificationFeedService) {
        this.deviceTokenService = deviceTokenService;
        this.notificationSettingService = notificationSettingService;
        this.notificationFeedService = notificationFeedService;
    }

    @Operation(summary = "받은 알림 목록",
            description = "그 밴드에서 나에게 발송된 알림을 최신순으로 반환한다. 다음 페이지는 응답의 "
                    + "nextCursor 를 cursor 로 다시 보낸다(null 이면 마지막). size 는 최대 50, 기본 20. "
                    + "읽음 여부는 서버가 갖지 않는다 — 클라이언트가 마지막 확인 시각을 기기에 저장한다. "
                    + "이 기능(V11) 이전에 발송된 알림은 문구가 남아 있지 않아 목록에서 빠진다. "
                    + "그 밴드 멤버만(비멤버 403 NOT_BAND_MEMBER).")
    @GetMapping
    public ApiResponse<NotificationFeedResponse> feed(@AuthenticationPrincipal AuthPrincipal principal,
                                                      @RequestParam long bandId,
                                                      @RequestParam(required = false) Long cursor,
                                                      @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(
                notificationFeedService.feed(bandId, principal.userId(), cursor, size));
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
