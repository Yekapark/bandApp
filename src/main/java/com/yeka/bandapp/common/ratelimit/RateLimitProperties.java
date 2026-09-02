package com.yeka.bandapp.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 분당 요청 상한. {@code app.ratelimit.*}. 값이 0 이하이면 안전한 기본값으로 되돌린다.
 */
@ConfigurationProperties(prefix = "app.ratelimit")
public record RateLimitProperties(
        int inviteJoinPerUserPerMin,
        int inviteJoinPerIpPerMin,
        int authPerIpPerMin,
        int geocodePerUserPerMin,
        int mediaUploadPerUserPerMin,
        int reportPerUserPerMin
) {
    public RateLimitProperties {
        if (inviteJoinPerUserPerMin <= 0) {
            inviteJoinPerUserPerMin = 10;
        }
        if (inviteJoinPerIpPerMin <= 0) {
            inviteJoinPerIpPerMin = 20;
        }
        if (authPerIpPerMin <= 0) {
            authPerIpPerMin = 20;
        }
        if (geocodePerUserPerMin <= 0) {
            geocodePerUserPerMin = 20;
        }
        if (mediaUploadPerUserPerMin <= 0) {
            mediaUploadPerUserPerMin = 30;
        }
        if (reportPerUserPerMin <= 0) {
            reportPerUserPerMin = 10;
        }
    }
}
