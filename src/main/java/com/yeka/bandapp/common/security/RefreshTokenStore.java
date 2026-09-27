package com.yeka.bandapp.common.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

    // 확인·회전·재시도 응답 공개·재사용 차단을 한 명령으로 처리한다. 중간 상태를 다른 요청이
    // 보면 정상 재시도를 탈취로 오인하거나, 로그아웃으로 지운 세션을 다시 만들 수 있다.
    private static final DefaultRedisScript<String> ROTATE = new DefaultRedisScript<>("""
            if redis.call('HEXISTS', KEYS[1], ARGV[1]) == 1 then
                redis.call('HDEL', KEYS[1], ARGV[1])
                redis.call('HSET', KEYS[1], ARGV[2], ARGV[3])
                redis.call('PEXPIRE', KEYS[1], ARGV[4])
                redis.call('SET', KEYS[2], ARGV[5], 'PX', ARGV[6])
                return ARGV[5]
            end
            local replay = redis.call('GET', KEYS[2])
            if replay and redis.call('EXISTS', KEYS[1]) == 1 then
                return replay
            end
            redis.call('DEL', KEYS[1])
            return nil
            """, String.class);

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

    /**
     * 새 회전 또는 60초 내 재시도 응답을 반환한다. 무효한 토큰의 재사용은 전 세션을 지우고 empty.
     * 기존 세션·재시도 캐시 형식은 유지해 배포 전 발급된 토큰도 계속 갱신할 수 있다.
     */
    public Optional<String> rotate(long userId, String oldJti, String newJti, Duration ttl, String payload) {
        return Optional.ofNullable(redis.execute(ROTATE, List.of(key(userId), replayKey(userId, oldJti)),
                oldJti, newJti, Long.toString(Instant.now().toEpochMilli()), Long.toString(ttl.toMillis()),
                payload, Long.toString(ROTATION_REPLAY_GRACE.toMillis())));
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

    private String replayKey(long userId, String jti) {
        return REPLAY_PREFIX + userId + ':' + jti;
    }
}
