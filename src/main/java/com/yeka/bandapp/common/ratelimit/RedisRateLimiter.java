package com.yeka.bandapp.common.ratelimit;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis 고정 윈도우(1분) 카운터 기반 레이트리밋.
 *
 * <pre>ratelimit:{bucket}:{key}:{분 단위 epoch}   INCR, 첫 증가 시 TTL 120s</pre>
 *
 * <p>버킷은 대상 종류(예: {@code invite-join:ip}), 키는 식별자(userId 또는 IP)다.
 * 윈도우 경계에서 최대 2배까지 통과할 수 있으나(고정 윈도우의 한계), 무차별 대입·열거·외부 API
 * 남용을 늦추는 목적에는 충분하다. 슬라이딩 로그가 필요하면 이 클래스만 교체하면 된다.
 */
@Component
public class RedisRateLimiter {

    private static final String KEY_PREFIX = "ratelimit:";
    private static final Duration WINDOW_TTL = Duration.ofSeconds(120);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** @return 이번 요청까지 포함해 윈도우 내 카운트가 {@code limitPerMinute} 이하이면 true */
    public boolean tryAcquire(String bucket, String key, int limitPerMinute) {
        long window = Instant.now().getEpochSecond() / 60;
        String redisKey = KEY_PREFIX + bucket + ':' + key + ':' + window;
        Long count = redis.opsForValue().increment(redisKey);
        if (count != null && count == 1L) {
            redis.expire(redisKey, WINDOW_TTL);
        }
        return count != null && count <= limitPerMinute;
    }

    /** {@link #tryAcquire}가 실패하면 {@link ErrorCode#TOO_MANY_REQUESTS}(429)를 던진다. */
    public void check(String bucket, String key, int limitPerMinute) {
        if (!tryAcquire(bucket, key, limitPerMinute)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }
}
