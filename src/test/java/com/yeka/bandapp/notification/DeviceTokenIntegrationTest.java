package com.yeka.bandapp.notification;

import com.yeka.bandapp.notification.repository.DeviceTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import java.util.concurrent.atomic.AtomicInteger;
import static com.yeka.bandapp.support.RateLimitAssertions.assertRateLimited;

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
        // 테스트 설정상 device-token 분당 10회.
        AtomicInteger seq = new AtomicInteger();
        assertRateLimited(10, () ->
                registerToken(user, "tok-" + seq.getAndIncrement(), "ANDROID").getStatusCode().value());
    }

    /**
     * 로그아웃 요청에 기기 토큰을 실으면 그 기기로 가던 푸시가 끊긴다(LAUNCH_REVIEW U1). 예전에는 앱이 토큰을 지운
     * 뒤에 인증이 필요한 DELETE 를 불러 401 이 났고, 로그아웃한 폰에 이전 계정 알림이 계속 갔다.
     */
    @Test
    void logout_with_device_token_stops_push_to_that_device() {
        ResponseEntity<String> signup = post("/api/v1/auth/signup",
                "{\"email\":\"dt-logout@band.app\",\"password\":\"pw12345678\",\"name\":\"유저\"}");
        String access = body(signup).at("/data/tokens/accessToken").asText();
        String refresh = body(signup).at("/data/tokens/refreshToken").asText();
        registerToken(access, "tok-logout", "ANDROID");

        ResponseEntity<String> res = post("/api/v1/auth/logout",
                "{\"refreshToken\":\"" + refresh + "\",\"deviceToken\":\"tok-logout\"}");

        assertThat(res.getStatusCode().value()).isEqualTo(204);
        assertThat(deviceTokenRepository.findByToken("tok-logout")).isEmpty();
    }

    /** 세션이 만료돼 강제로 로그아웃된 기기(refresh 무효)도 푸시를 끊을 수 있다. */
    @Test
    void logout_with_an_expired_session_still_forgets_the_device() {
        String user = signup("dt-expired@band.app", "유저");
        registerToken(user, "tok-expired", "ANDROID");

        ResponseEntity<String> res = post("/api/v1/auth/logout",
                "{\"refreshToken\":\"not-a-valid-refresh\",\"deviceToken\":\"tok-expired\"}");

        assertThat(res.getStatusCode().value()).isEqualTo(204);
        assertThat(deviceTokenRepository.findByToken("tok-expired")).isEmpty();
    }

    /** 기기 토큰 없이 로그아웃하는 옛 앱도 그대로 된다. */
    @Test
    void logout_without_device_token_keeps_working() {
        String user = signup("dt-notoken@band.app", "유저");
        registerToken(user, "tok-kept", "ANDROID");

        ResponseEntity<String> res = post("/api/v1/auth/logout", "{\"refreshToken\":\"whatever\"}");

        assertThat(res.getStatusCode().value()).isEqualTo(204);
        assertThat(deviceTokenRepository.findByToken("tok-kept")).isPresent();
    }

    @Test
    void device_token_endpoints_require_authentication() {
        assertThat(post("/api/v1/notifications/device-tokens",
                "{\"token\":\"x\",\"platform\":\"WEB\"}").getStatusCode().value()).isEqualTo(401);
    }
}
