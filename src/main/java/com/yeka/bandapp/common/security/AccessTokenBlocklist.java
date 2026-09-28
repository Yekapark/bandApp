package com.yeka.bandapp.common.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 무상태 access 토큰을 만료 전에 무효화해야 할 때 쓰는 차단 목록.
 *
 * <pre>auth:blocked:{userId}   String "1"(탈퇴) | "S"(이용 정지), TTL = access 토큰 만료</pre>
 *
 * <p>탈퇴 시점에 등재하면 {@link JwtAuthenticationFilter}가 해당 사용자의 기존 access 토큰을
 * 즉시 거부한다. TTL이 access 만료와 같아 자료구조가 스스로 소멸한다.
 * 이용 정지는 운영 스크립트({@code tools/moderate.py})가 redis-cli 로 {@code "S"} 를 넣는다 — 값으로 둘을 구분해
 * 정지된 사람에게 "탈퇴한 계정" 이라고 답하지 않는다.
 * 로그아웃에는 쓰지 않는다 — 한 기기 로그아웃이 다른 기기의 access까지 죽이기 때문이다.
 */
@Component
public class AccessTokenBlocklist {

    private static final String KEY_PREFIX = "auth:blocked:";
    /** 이용 정지 표시. 운영 스크립트와 약속한 값이라 바꾸면 스크립트도 함께 바꾼다. */
    public static final String SUSPENDED = "S";

    private final StringRedisTemplate redis;

    public AccessTokenBlocklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void block(long userId, Duration ttl) {
        redis.opsForValue().set(KEY_PREFIX + userId, "1", ttl);
    }

    /** 막혔다면 그 이유(탈퇴·정지). 막히지 않았으면 empty. */
    public Optional<Reason> reason(long userId) {
        String value = redis.opsForValue().get(KEY_PREFIX + userId);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(SUSPENDED.equals(value) ? Reason.SUSPENDED : Reason.WITHDRAWN);
    }

    public enum Reason { WITHDRAWN, SUSPENDED }
}
