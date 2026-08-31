package com.yeka.bandapp.common.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * refresh 토큰 세션 저장소 (Redis Hash).
 *
 * <pre>
 * auth:refresh:{userId}   Hash   field = jti, value = 발급 epochMillis
 * </pre>
 *
 * <p>필드 하나가 기기(세션) 하나에 대응한다. 단일 로그아웃은 {@code HDEL}, 탈퇴·재사용 탐지 시
 * 전 기기 무효화는 {@code DEL}로 모두 O(1)이다. 키 TTL은 쓰기마다 refresh 만료로 다시 채운다
 * (슬라이딩). 잔여 필드가 자기 만료보다 오래 남을 수 있으나, refresh 검증은 JWT 서명·만료를
 * 먼저 통과해야 하므로 권한이 아니라 데이터일 뿐이다.
 */
@Component
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redis;

    public RefreshTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void save(long userId, String jti, Duration ttl) {
        String key = key(userId);
        redis.opsForHash().put(key, jti, Long.toString(Instant.now().toEpochMilli()));
        redis.expire(key, ttl);
    }

    public boolean exists(long userId, String jti) {
        return redis.opsForHash().hasKey(key(userId), jti);
    }

    /** 이전 jti를 지우고 새 jti를 저장한다. 키 TTL도 갱신(슬라이딩). */
    public void rotate(long userId, String oldJti, String newJti, Duration ttl) {
        String key = key(userId);
        redis.opsForHash().delete(key, oldJti);
        redis.opsForHash().put(key, newJti, Long.toString(Instant.now().toEpochMilli()));
        redis.expire(key, ttl);
    }

    /** 단일 세션(기기) 로그아웃. 이미 없으면 무시된다. */
    public void remove(long userId, String jti) {
        redis.opsForHash().delete(key(userId), jti);
    }

    /** 전 기기 무효화 (탈퇴 / refresh 재사용 탐지). */
    public void removeAll(long userId) {
        redis.delete(key(userId));
    }

    private String key(long userId) {
        return KEY_PREFIX + userId;
    }
}
