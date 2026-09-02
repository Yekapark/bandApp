package com.yeka.bandapp.notification;

import com.yeka.bandapp.notification.repository.DeviceTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 디바이스 토큰 등록/갱신/해제. 같은 토큰이 계정 전환되면 소유자만 갱신되는지, 남의 토큰은 못 지우는지,
 * 레이트리밋이 걸리는지 본다.
 */
class DeviceTokenIntegrationTest extends NotificationApiSupport {

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Test
    void register_then_unregister() {
        String user = signup("dt-basic@band.app", "유저");

        assertThat(registerToken(user, "tok-basic", "ANDROID").getStatusCode().value()).isEqualTo(201);
        assertThat(deviceTokenRepository.findByToken("tok-basic")).isPresent();

        assertThat(delete("/api/v1/notifications/device-tokens?token=tok-basic", user)
                .getStatusCode().value()).isEqualTo(204);
        assertThat(deviceTokenRepository.findByToken("tok-basic")).isEmpty();
    }

    @Test
    void re_registering_an_existing_token_reassigns_the_owner() {
        String alice = signup("dt-alice@band.app", "앨리스");
        String bob = signup("dt-bob@band.app", "밥");
        long aliceId = myUserId(alice);
        long bobId = myUserId(bob);

        registerToken(alice, "shared-device", "IOS");
        assertThat(deviceTokenRepository.findByToken("shared-device").orElseThrow().getUserId())
                .isEqualTo(aliceId);

        // 같은 기기를 밥이 등록 — 새 행이 생기지 않고 소유자만 바뀐다.
        assertThat(registerToken(bob, "shared-device", "ANDROID").getStatusCode().value()).isEqualTo(201);
        assertThat(deviceTokenRepository.findAll()).hasSize(1);
        assertThat(deviceTokenRepository.findByToken("shared-device").orElseThrow().getUserId())
                .isEqualTo(bobId);
    }

    @Test
    void cannot_unregister_someone_elses_token() {
        String alice = signup("dt-iso-a@band.app", "앨리스");
        String bob = signup("dt-iso-b@band.app", "밥");
        registerToken(alice, "alice-only", "WEB");

        ResponseEntity<String> res = delete("/api/v1/notifications/device-tokens?token=alice-only", bob);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(res)).isEqualTo("DEVICE_TOKEN_NOT_FOUND");
        assertThat(deviceTokenRepository.findByToken("alice-only")).isPresent();
    }

    @Test
    void registration_is_rate_limited_per_user() {
        String user = signup("dt-rl@band.app", "유저");
        int limited = 0;
        for (int i = 0; i < 13; i++) {
            ResponseEntity<String> res = registerToken(user, "tok-" + i, "ANDROID");
            if (res.getStatusCode().value() == 429) {
                limited++;
                assertThat(errorCode(res)).isEqualTo("TOO_MANY_REQUESTS");
            }
        }
        assertThat(limited).isPositive();
    }

    @Test
    void device_token_endpoints_require_authentication() {
        assertThat(post("/api/v1/notifications/device-tokens",
                "{\"token\":\"x\",\"platform\":\"WEB\"}").getStatusCode().value()).isEqualTo(401);
    }
}
