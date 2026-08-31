package com.yeka.bandapp.common.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 무상태 access 토큰을 만료 전에 무효화해야 할 때 쓰는 차단 목록.
 *
 * <pre>auth:blocked:{userId}   String "1", TTL = access 토큰 만료</pre>
 *
 * <p>탈퇴 시점에 등재하면 {@link JwtAuthenticationFilter}가 해당 사용자의 기존 access 토큰을
 * 즉시 거부한다. TTL이 access 만료와 같아 자료구조가 스스로 소멸한다.
 * 로그아웃에는 쓰지 않는다 — 한 기기 로그아웃이 다른 기기의 access까지 죽이기 때문이다.
 */
@Component
public class AccessTokenBlocklist {

    private static final String KEY_PREFIX = "auth:blocked:";

    private final StringRedisTemplate redis;

    public AccessTokenBlocklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void block(long userId, Duration ttl) {
        redis.opsForValue().set(KEY_PREFIX + userId, "1", ttl);
    }

    public boolean isBlocked(long userId) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + userId));
    }
}
