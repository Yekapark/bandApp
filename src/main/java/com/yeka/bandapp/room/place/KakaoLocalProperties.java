package com.yeka.bandapp.room.place;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 카카오 로컬(키워드 장소 검색) 연동 설정. {@code app.kakao.local.*}.
 *
 * <p>합주실 등록 폼의 "검색해서 자동 입력"에 쓴다. 카카오 개발자 콘솔 앱의 <b>REST API 키</b>를
 * {@code Authorization: KakaoAK {키}} 헤더로 보낸다 — 카카오 로그인용 네이티브/JS 키와 다른 값이며,
 * 별도 제휴·활성화 없이 앱만 있으면 호출된다.
 *
 * <p>{@code restApiKey}는 비어 있을 수 있다 — 그 경우 검색 API는 항상 빈 목록을 돌려주고(합주실
 * 등록 자체는 주소를 직접 입력해 정상 진행), 키를 환경변수로 넣으면 그때부터 동작한다.
 */
@ConfigurationProperties(prefix = "app.kakao.local")
public record KakaoLocalProperties(
        String apiBaseUrl,
        String restApiKey,
        Duration connectTimeout,
        Duration readTimeout
) {
    public KakaoLocalProperties {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            apiBaseUrl = "https://dapi.kakao.com";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(3);
        }
    }

    public boolean isConfigured() {
        return restApiKey != null && !restApiKey.isBlank();
    }
}
