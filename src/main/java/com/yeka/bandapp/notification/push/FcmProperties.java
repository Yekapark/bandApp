package com.yeka.bandapp.notification.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * FCM 연동 설정. {@code app.fcm.*}.
 *
 * <p>{@code projectId}와 자격증명({@code credentialsJson} 또는 {@code credentialsPath})이 모두 있어야
 * 발송이 활성화된다. 하나라도 비면 {@link FcmPushSender}가 조용히 no-op 으로 뜨고, 디바이스 토큰
 * 등록·알림 설정 API 는 정상 동작한다({@code R2Properties}와 같은 방식).
 */
@ConfigurationProperties(prefix = "app.fcm")
public record FcmProperties(
        String projectId,
        String credentialsPath,
        String credentialsJson,
        Duration connectTimeout,
        Duration readTimeout
) {

    public FcmProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }

    public boolean isConfigured() {
        return isNotBlank(projectId) && (isNotBlank(credentialsJson) || isNotBlank(credentialsPath));
    }

    /** JSON 문자열이 있으면 그것을, 없으면 파일 경로를 쓴다. */
    public boolean hasInlineCredentials() {
        return isNotBlank(credentialsJson);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
