package com.yeka.bandapp.common.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메일 발신 설정. {@code app.mail.*}.
 *
 * <p>{@code from}이 비어 있으면 {@link EmailSender}가 발송을 건너뛰고 로그만 남긴다 —
 * 카카오·FCM·R2 와 같은 "미설정이면 그 기능만 조용히 꺼진다" 방식이다.
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String from) {

    public boolean isConfigured() {
        return from != null && !from.isBlank();
    }
}
