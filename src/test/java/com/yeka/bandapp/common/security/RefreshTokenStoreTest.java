package com.yeka.bandapp.common.security;

import com.yeka.bandapp.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenStoreTest extends IntegrationTestSupport {

    private static final Duration TTL = Duration.ofDays(1);

    @Autowired
    RefreshTokenStore store;

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

        store.rotate(1L, "old", "new", TTL);

        assertThat(store.exists(1L, "old")).isFalse();
        assertThat(store.exists(1L, "new")).isTrue();
    }
}
