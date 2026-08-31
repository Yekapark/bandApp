package com.yeka.bandapp.user.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 카카오 연동 설정. {@code app.kakao.*}.
 *
 * <p>{@code appId}/{@code adminKey}는 비어 있을 수 있다 — 그 경우 카카오 로그인만
 * {@code KAKAO_NOT_CONFIGURED(503)}로 응답하고 이메일 로그인 등 나머지는 정상 동작한다.
 * {@code adminKey}는 카카오 API 무제한 호출이 가능한 마스터 키이므로 환경변수로만 다룬다.
 */
@ConfigurationProperties(prefix = "app.kakao")
public record KakaoProperties(
        String apiBaseUrl,
        String appId,
        String adminKey,
        Duration connectTimeout,
        Duration readTimeout
) {
    public KakaoProperties {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            apiBaseUrl = "https://kapi.kakao.com";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(3);
        }
    }

    public boolean isConfigured() {
        return appId != null && !appId.isBlank() && adminKey != null && !adminKey.isBlank();
    }
}
