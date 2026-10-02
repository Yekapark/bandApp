package com.yeka.bandapp.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.reservation.ReservationApiSupport;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 알림 통합 테스트 공통 헬퍼(디바이스 토큰 등록, 알림 설정, "곧 시작" 일정 시각).
 * 밴드·합주실·일정 픽스처는 {@link ReservationApiSupport} 계열에서 온다.
 */
public abstract class NotificationApiSupport extends ReservationApiSupport {

    protected static final String NOTIFICATIONS = "/api/v1/notifications";

    /** 아직 끝나지 않은 일정 시각. 끝난 일정의 취소는 알리지 않으므로(결정 #20) 취소 알림을 보는 테스트가 쓴다. */
    protected static final String F10 = Instant.now().plus(Duration.ofDays(30))
            .truncatedTo(java.time.temporal.ChronoUnit.HOURS).toString();
    protected static final String F13 = Instant.parse(F10).plus(Duration.ofHours(3)).toString();
    protected static final String F16 = Instant.parse(F10).plus(Duration.ofHours(6)).toString();

    protected ResponseEntity<String> registerToken(String accessToken, String token, String platform) {
        return post(NOTIFICATIONS + "/device-tokens",
                "{\"token\":\"" + token + "\",\"platform\":\"" + platform + "\"}", accessToken);
    }

    protected ResponseEntity<String> putSettings(String accessToken, boolean pushEnabled, int... offsets) {
        String arr = Arrays.stream(offsets).mapToObj(Integer::toString).collect(Collectors.joining(","));
        return put(NOTIFICATIONS + "/settings",
                "{\"pushEnabled\":" + pushEnabled + ",\"reminderOffsets\":[" + arr + "]}", accessToken);
    }

    protected JsonNode getSettings(String accessToken) {
        return data(get(NOTIFICATIONS + "/settings", accessToken));
    }

    /** 지금부터 {@code fromNow} 뒤 시각의 ISO 문자열(일정 startAt 용). */
    protected String isoFromNow(Duration fromNow) {
        return Instant.now().plus(fromNow).toString();
    }
}
