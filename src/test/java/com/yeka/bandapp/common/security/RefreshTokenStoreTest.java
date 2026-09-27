package com.yeka.bandapp.common.security;

import com.yeka.bandapp.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenStoreTest extends IntegrationTestSupport {

    private static final Duration TTL = Duration.ofDays(1);

    @Autowired
    RefreshTokenStore store;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void save_then_exists() {
        store.save(1L, "jti-a", TTL);

        assertThat(store.exists(1L, "jti-a")).isTrue();
        assertThat(store.exists(1L, "jti-unknown")).isFalse();
    }

    @Test
    void remove_drops_only_that_session() {
        store.save(1L, "a", TTL);
        store.save(1L, "b", TTL);

        store.remove(1L, "a");

        assertThat(store.exists(1L, "a")).isFalse();
        assertThat(store.exists(1L, "b")).isTrue();
    }

    @Test
    void removeAll_drops_every_session() {
        store.save(1L, "a", TTL);
        store.save(1L, "b", TTL);

        store.removeAll(1L);

        assertThat(store.exists(1L, "a")).isFalse();
        assertThat(store.exists(1L, "b")).isFalse();
    }

    @Test
    void rotate_swaps_jti() {
        store.save(1L, "old", TTL);

        assertThat(store.rotate(1L, "old", "new", TTL, "response")).contains("response");

        assertThat(store.exists(1L, "old")).isFalse();
        assertThat(store.exists(1L, "new")).isTrue();
    }

    @Test
    void revoked_token_cannot_recreate_a_session() {
        store.save(1L, "old", TTL);
        store.remove(1L, "old");

        assertThat(store.rotate(1L, "old", "new", TTL, "response")).isEmpty();
        assertThat(store.exists(1L, "new")).isFalse();
    }

    @Test
    void removing_all_sessions_also_prevents_replaying_a_cached_rotation() {
        store.save(1L, "old", TTL);
        store.rotate(1L, "old", "new", TTL, "response");
        store.removeAll(1L);

        assertThat(store.rotate(1L, "old", "retry", TTL, "retry-response")).isEmpty();
        assertThat(store.exists(1L, "new")).isFalse();
        assertThat(store.exists(1L, "retry")).isFalse();
    }

    @Test
    void replay_after_grace_expires_still_revokes_all_sessions() {
        store.save(1L, "old", TTL);
        store.save(1L, "other-device", TTL);
        store.rotate(1L, "old", "new", TTL, "response");
        // Redis에서 유예 캐시를 즉시 만료시킨다. 테스트를 위해 60초 기다릴 필요는 없다.
        redis.expire("auth:refresh:replay:1:old", Duration.ZERO);

        assertThat(store.rotate(1L, "old", "retry", TTL, "retry-response")).isEmpty();
        assertThat(store.exists(1L, "new")).isFalse();
        assertThat(store.exists(1L, "other-device")).isFalse();
    }
}
