package com.yeka.bandapp.notification.service;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.common.ratelimit.RateLimitProperties;
import com.yeka.bandapp.common.ratelimit.RedisRateLimiter;
import com.yeka.bandapp.notification.entity.DevicePlatform;
import com.yeka.bandapp.notification.entity.DeviceToken;
import com.yeka.bandapp.notification.repository.DeviceTokenRepository;
import com.yeka.bandapp.notification.repository.NotificationSettingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * FCM 디바이스 토큰 등록/해제, 그리고 계정 탈퇴 시 알림 데이터 정리.
 *
 * <p>외부 I/O 가 없으므로(순수 DB) 각 명령은 일반 {@code @Transactional}로 처리한다.
 */
@Service
public class DeviceTokenService {

    private static final String RATE_LIMIT_BUCKET = "device-token:user";

    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationSettingRepository settingRepository;
    private final RedisRateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    public DeviceTokenService(DeviceTokenRepository deviceTokenRepository,
                              NotificationSettingRepository settingRepository,
                              RedisRateLimiter rateLimiter,
                              RateLimitProperties rateLimitProperties) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.settingRepository = settingRepository;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
    }

    /**
     * 토큰 등록/갱신(upsert). 같은 토큰이 이미 있으면(같은 사람이든 다른 사람이든) 소유자·플랫폼을 갱신한다
     * — 기기 하나당 토큰은 하나이며 계정 전환 시 재등록되기 때문이다. 레이트리밋 초과는 429.
     */
    @Transactional
    public void register(long userId, String token, DevicePlatform platform) {
        rateLimiter.check(RATE_LIMIT_BUCKET, Long.toString(userId),
                rateLimitProperties.deviceTokenPerUserPerMin());

        Instant now = Instant.now();
        deviceTokenRepository.findByToken(token).ifPresentOrElse(
                existing -> existing.reassign(userId, platform, now),
                () -> insertNew(userId, token, platform, now));
    }

    private void insertNew(long userId, String token, DevicePlatform platform, Instant now) {
        try {
            deviceTokenRepository.saveAndFlush(DeviceToken.register(userId, token, platform, now));
        } catch (DataIntegrityViolationException race) {
            // 동시 등록 경합 — 유니크 제약에 걸린 쪽은 갱신 경로로 되돌린다(CLAUDE.md 규칙).
            deviceTokenRepository.findByToken(token)
                    .ifPresent(existing -> existing.reassign(userId, platform, now));
        }
    }

    /** 토큰 해제. 본인이 등록한 토큰만 지운다 — 매칭되는 행이 없으면 404. */
    @Transactional
    public void unregister(long userId, String token) {
        if (deviceTokenRepository.deleteByUserIdAndToken(userId, token) == 0) {
            throw new BusinessException(ErrorCode.DEVICE_TOKEN_NOT_FOUND);
        }
    }

    /**
     * 로그아웃한 기기 — 이 토큰으로 가던 푸시를 끊는다. 누구 소유든 지운다: 토큰은 기기에 하나라 "이 기기에 더 보내지
     * 말라" 는 뜻이고, refresh 토큰이 만료된 채 강제 로그아웃된 기기도 정리해야 해서 소유자 확인을 요구하지 않는다
     * (토큰 값을 아는 것이 곧 그 기기다). 없으면 조용히 넘어간다(멱등).
     */
    @Transactional
    public void forgetDevice(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        deviceTokenRepository.deleteByTokenIn(List.of(token));
    }

    /** 계정 탈퇴·파기 시 — 그 사용자의 토큰과 알림 설정을 모두 제거한다. */
    @Transactional
    public void deleteAllOf(long userId) {
        deviceTokenRepository.deleteByUserId(userId);
        settingRepository.deleteByUserId(userId);
    }
}
