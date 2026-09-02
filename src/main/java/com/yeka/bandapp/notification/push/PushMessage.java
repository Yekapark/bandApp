package com.yeka.bandapp.notification.push;

import java.util.Map;

/**
 * 한 건의 푸시 알림 내용. {@code data}는 클라이언트가 알림을 눌렀을 때 이동할 화면을 정하는 데 쓰는
 * 문자열 맵이다(예: {@code {"type":"RESERVATION_REMINDER","bandId":"3","reservationId":"12"}}).
 * FCM data 페이로드는 값이 모두 문자열이어야 한다.
 */
public record PushMessage(String title, String body, Map<String, String> data) {

    public PushMessage {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
