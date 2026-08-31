package com.yeka.bandapp.common.security;

/**
 * 인증된 요청의 주체. {@link JwtAuthenticationFilter}가 SecurityContext에 넣고,
 * 컨트롤러는 {@code @AuthenticationPrincipal AuthPrincipal}로 받는다.
 */
public record AuthPrincipal(Long userId) {
}
