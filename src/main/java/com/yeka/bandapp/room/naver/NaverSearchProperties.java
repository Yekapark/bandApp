package com.yeka.bandapp.room.naver;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 네이버 지역검색(장소 검색) 연동 설정. {@code app.naver.search.*}.
 *
 * <p>지오코딩({@link NaverProperties}, NCP Maps)과는 <b>다른 API·다른 자격증명</b>이다. 지역검색은
 * 네이버 개발자센터 애플리케이션의 {@code X-Naver-Client-Id}/{@code X-Naver-Client-Secret}을 쓴다.
 *
 * <p>{@code clientId}/{@code clientSecret}은 비어 있을 수 있다 — 그 경우 검색 API는 항상 빈 목록을
 * 돌려주고(합주실 등록 자체는 주소를 직접 입력해 정상 진행), 키를 환경변수로 넣으면 그때부터 동작한다.
 */
@ConfigurationProperties(prefix = "app.naver.search")
public record NaverSearchProperties(
        String apiBaseUrl,
        String clientId,
        String clientSecret,
        Duration connectTimeout,
        Duration readTimeout
) {
    public NaverSearchProperties {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            apiBaseUrl = "https://openapi.naver.com";
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
