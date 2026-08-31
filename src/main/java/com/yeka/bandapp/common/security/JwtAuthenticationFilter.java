package com.yeka.bandapp.common.security;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * {@code Authorization: Bearer <access>}를 파싱해 {@link AuthPrincipal}을 SecurityContext에 넣는다.
 *
 * <p>관대하게 동작한다 — 헤더가 없으면 아무것도 하지 않고 통과시켜 permitAll 경로가 정상 동작한다.
 * 토큰이 있으나 유효하지 않으면 사유 {@link ErrorCode}를 request attribute에 남기고 통과시킨다.
 * 이후 인가 단계에서 거부되면 {@link RestAuthenticationEntryPoint}가 그 사유로 401을 만든다
 * (필터에서 던진 예외는 {@code @RestControllerAdvice}에 도달하지 않기 때문).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 토큰 검증 실패 사유를 담는 request attribute 키. */
    public static final String ERROR_CODE_ATTRIBUTE = "auth.errorCode";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final AccessTokenBlocklist blocklist;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, AccessTokenBlocklist blocklist) {
        this.tokenProvider = tokenProvider;
        this.blocklist = blocklist;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                JwtTokenProvider.ParsedToken parsed = tokenProvider.parseAccess(token);
                if (blocklist.isBlocked(parsed.userId())) {
                    throw new BusinessException(ErrorCode.ACCOUNT_WITHDRAWN);
                }
                var authentication = new UsernamePasswordAuthenticationToken(
                        new AuthPrincipal(parsed.userId()), null, List.of());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException e) {
                SecurityContextHolder.clearContext();
                request.setAttribute(ERROR_CODE_ATTRIBUTE, e.errorCode());
            }
        }
        filterChain.doFilter(request, response);
    }
}
