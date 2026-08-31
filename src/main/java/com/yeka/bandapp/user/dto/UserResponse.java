package com.yeka.bandapp.user.dto;

import com.yeka.bandapp.user.entity.User;

import java.time.Instant;

/**
 * @param socialProvider 이메일 가입이면 {@code null}, 소셜 가입이면 {@code "KAKAO"}
 */
public record UserResponse(
        Long id,
        String email,
        String name,
        String socialProvider,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getSocialProvider() == null ? null : user.getSocialProvider().name(),
                user.getCreatedAt());
    }
}
