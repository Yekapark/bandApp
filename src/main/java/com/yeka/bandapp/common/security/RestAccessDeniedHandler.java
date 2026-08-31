package com.yeka.bandapp.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeka.bandapp.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * 인가 실패(403)를 공통 {@link com.yeka.bandapp.common.response.ApiResponse} 포맷 JSON으로 직렬화한다.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        RestAuthenticationEntryPoint.writeError(response, ErrorCode.FORBIDDEN, objectMapper);
    }
}
