package com.yeka.bandapp.notification.push;

import java.util.List;

/**
 * 푸시 발송의 유일한 접점. 나머지 코드는 FCM 을 모른다({@code StorageClient}·{@code GeocodingClient}와
 * 같은 역할). 바이트를 나르지 않으며, 실제 구현은 {@link FcmPushSender}다.
 *
 * <p>자격증명이 없으면 {@link #isConfigured()}가 {@code false}이고 {@link #send}는 아무것도 하지 않는다
 * — 알림은 부가 기능이라 미설정이 일정 등록·정산을 깨서는 안 된다(그래서 예외를 던지지 않는다).
 */
public interface PushSender {

    /** 여러 토큰으로 같은 메시지를 보낸다. 500개 초과는 구현이 청크로 나눈다. */
    PushResult send(PushMessage message, List<String> tokens);

    /** 자격증명이 갖춰져 실제 발송이 가능한지. */
    boolean isConfigured();
}
