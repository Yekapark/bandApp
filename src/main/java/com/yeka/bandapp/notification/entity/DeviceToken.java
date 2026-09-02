package com.yeka.bandapp.notification.entity;

import com.yeka.bandapp.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * FCM 디바이스 토큰. {@code token}은 전역 유니크다 — 같은 기기가 계정 전환되면 재등록 시
 * {@link #reassign}로 소유자만 갱신한다(새 행을 만들지 않는다).
 *
 * <p>도메인 모델(BUILD_PLAN)의 {@code updatedAt}은 마지막 등록/갱신 시각이다. {@code createdAt}은 다른
 * 테이블과의 정합을 위해 {@link BaseTimeEntity}로 자동 관리한다.
 */
@Entity
@Table(name = "device_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DevicePlatform platform;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private DeviceToken(long userId, String token, DevicePlatform platform, Instant now) {
        this.userId = userId;
        this.token = token;
        this.platform = platform;
        this.updatedAt = now;
    }

    public static DeviceToken register(long userId, String token, DevicePlatform platform, Instant now) {
        return new DeviceToken(userId, token, platform, now);
    }

    /** 같은 토큰이 이미 있을 때 — 소유자·플랫폼·갱신 시각만 바꾼다. */
    public void reassign(long userId, DevicePlatform platform, Instant now) {
        this.userId = userId;
        this.platform = platform;
        this.updatedAt = now;
    }
}
