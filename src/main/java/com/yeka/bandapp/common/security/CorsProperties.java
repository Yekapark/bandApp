package com.yeka.bandapp.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 브라우저 클라이언트(Flutter 웹) 대응 CORS 설정. {@code app.cors.*}.
 * 값은 {@code CorsConfiguration.setAllowedOriginPatterns}에 그대로 전달되므로 {@code *} 패턴을 쓸 수 있다.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = List.of("http://localhost:*");
        }
    }
}
