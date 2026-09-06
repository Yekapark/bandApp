package com.yeka.bandapp.user.dto;

import com.yeka.bandapp.user.entity.User;

import java.time.Instant;

/**
 * @param socialProvider 이메일 가입이면 {@code null}, 소셜 가입이면 {@code "KAKAO"}
 * @param emailVerified 소셜 가입자는 항상 {@code true}. 이메일 가입자가 {@code false}면
 *                      클라이언트가 인증 안내 배너를 띄울 수 있다 — 기능은 막지 않는다.
 */
public record UserResponse(
        Long id,
        String email,
        String name,
        String socialProvider,
        boolean emailVerified,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getSocialProvider() == null ? null : user.getSocialProvider().name(),
                user.isEmailVerified(),
                user.getCreatedAt());
    }
}
