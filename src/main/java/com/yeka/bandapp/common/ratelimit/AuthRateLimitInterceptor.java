package com.yeka.bandapp.common.ratelimit;

import com.yeka.bandapp.common.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@code /api/v1/auth/**} 의 상태 변경 요청(POST)에 IP 기준 분당 상한을 건다.
 * 무차별 대입(/login), 이메일 열거(/signup·/login), 카카오 API 남용(/kakao), 토큰 회전 남용(/refresh) 대응.
 *
 * <p>엔드포인트별로 예산을 분리하려고 버킷 키에 요청 경로를 포함한다.
 * 초과 시 {@link com.yeka.bandapp.common.exception.ErrorCode#TOO_MANY_REQUESTS}가
 * {@code GlobalExceptionHandler}로 흘러 공통 포맷의 429 응답이 된다.
 */
@Component
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    private final RedisRateLimiter rateLimiter;
    private final RateLimitProperties properties;

    public AuthRateLimitInterceptor(RedisRateLimiter rateLimiter, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String bucket = "auth:" + request.getRequestURI();
        rateLimiter.check(bucket, ClientIp.of(request), properties.authPerIpPerMin());
        return true;
    }
}
