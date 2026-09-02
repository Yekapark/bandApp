package com.yeka.bandapp.notification.push;

import java.util.List;

/**
 * 푸시 발송 결과. {@code invalidTokens}는 FCM 이 "더 이상 유효하지 않다"고 알려준 토큰들이며
 * ({@code UNREGISTERED} 등) 호출 측이 저장소에서 지운다 — 푸시 구현이 DB 를 모르게 유지한다.
 */
public record PushResult(int successCount, List<String> invalidTokens) {

    public PushResult {
        invalidTokens = invalidTokens == null ? List.of() : List.copyOf(invalidTokens);
    }

    public static PushResult empty() {
        return new PushResult(0, List.of());
    }
}
