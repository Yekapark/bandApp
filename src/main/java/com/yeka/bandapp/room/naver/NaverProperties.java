package com.yeka.bandapp.room.naver;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 네이버 지도(지오코딩) 연동 설정. {@code app.naver.*}.
 *
 * <p>{@code clientId}/{@code clientSecret}은 비어 있을 수 있다 — 그 경우 지오코딩만 건너뛰고
 * 합주실은 좌표 없이 주소만으로 정상 등록된다. 키를 나중에 환경변수로 넣으면 그때부터 좌표가 채워진다.
 *
 * <p>{@code apiBaseUrl}을 설정으로 뺀 이유: 네이버 클라우드 플랫폼이 게이트웨이 도메인을 옮긴 이력이
 * 있어(구 {@code naveropenapi.apigw.ntruss.com}), 콘솔에서 안내하는 주소로 코드 수정 없이 바꿀 수 있어야 한다.
 */
@ConfigurationProperties(prefix = "app.naver")
public record NaverProperties(
        String apiBaseUrl,
        String clientId,
        String clientSecret,
        Duration connectTimeout,
        Duration readTimeout
) {
    public NaverProperties {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            apiBaseUrl = "https://maps.apigw.ntruss.com";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(3);
        }
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
