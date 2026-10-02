package com.yeka.bandapp.common.ratelimit;

import com.yeka.bandapp.common.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * {@code /api/v1/auth/**} 의 상태 변경 요청(POST)에 IP 기준 분당 상한을 건다.
 * 무차별 대입(/login), 이메일 열거(/signup·/login), 카카오 API 남용(/kakao), 토큰 회전 남용(/refresh) 대응.
 *
 * <p>엔드포인트별로 예산을 분리하려고 버킷 키에 <b>매칭된 매핑 패턴</b>(예: {@code /api/v1/auth/login})을 넣는다.
 * 날 URI({@code getRequestURI()})를 쓰면 {@code /api/v1/auth/%6Cogin} 처럼 글자만 퍼센트 인코딩한 주소가
 * 같은 컨트롤러로 가면서 다른 버킷으로 세어져, 인코딩 조합 수만큼 상한이 늘어났다.
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
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String bucket = "auth:" + (pattern != null ? pattern : request.getRequestURI());
        rateLimiter.check(bucket, ClientIp.of(request), properties.authPerIpPerMin());
        return true;
    }
}
