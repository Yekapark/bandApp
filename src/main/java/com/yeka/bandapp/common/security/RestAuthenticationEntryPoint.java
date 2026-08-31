package com.yeka.bandapp.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.response.ErrorPayload;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증 실패(401)를 공통 {@link ApiResponse} 포맷 JSON으로 직렬화한다.
 * {@link JwtAuthenticationFilter}가 남긴 구체적 사유가 있으면 그 코드를, 없으면 {@code UNAUTHORIZED}를 쓴다.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object attr = request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE);
        ErrorCode code = (attr instanceof ErrorCode ec) ? ec : ErrorCode.UNAUTHORIZED;
        writeError(response, code, objectMapper);
    }

    static void writeError(HttpServletResponse response, ErrorCode code, ObjectMapper objectMapper) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.fail(ErrorPayload.of(code.name(), code.defaultMessage())));
    }
}
