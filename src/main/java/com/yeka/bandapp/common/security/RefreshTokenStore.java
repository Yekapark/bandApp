package com.yeka.bandapp.common.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

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
    private static final String REPLAY_PREFIX = "auth:refresh:replay:";

    /**
     * refresh 회전 직후, 방금 소비된 토큰(jti)에 대해 그 회전이 돌려준 응답을 이만큼 보관한다.
     * 네트워크 재시도·더블탭·탭 중복이 같은 토큰을 다시 보내도 전 세션 로그아웃 대신 같은 응답을 받게 한다.
     * 이 창을 넘겨 오는 옛 토큰은 여전히 "재사용"으로 간주된다.
     */
    static final Duration ROTATION_REPLAY_GRACE = Duration.ofSeconds(60);

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

    /**
     * 방금 회전에 성공한 토큰(jti)에 대해 그 회전 결과({@code payload})를 {@link #ROTATION_REPLAY_GRACE} 동안 보관.
     * 같은 옛 토큰이 그 창 안에 다시 오면 {@link #recallRotation}로 같은 응답을 돌려줄 수 있다.
     */
    public void rememberRotation(long userId, String consumedJti, String payload) {
        redis.opsForValue().set(replayKey(userId, consumedJti), payload, ROTATION_REPLAY_GRACE);
    }

    /** {@link #rememberRotation}로 저장해 둔 회전 결과. 창이 지났거나 없으면 empty. */
    public Optional<String> recallRotation(long userId, String consumedJti) {
        return Optional.ofNullable(redis.opsForValue().get(replayKey(userId, consumedJti)));
    }

    private String key(long userId) {
        return KEY_PREFIX + userId;
    }

    private String replayKey(long userId, String jti) {
        return REPLAY_PREFIX + userId + ':' + jti;
    }
}
