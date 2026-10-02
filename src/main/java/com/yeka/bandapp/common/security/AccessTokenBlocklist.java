package com.yeka.bandapp.common.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 무상태 access 토큰을 만료 전에 무효화해야 할 때 쓰는 차단 목록.
 *
 * <pre>
 * auth:blocked:{userId}         String "1"(탈퇴) | "S"(이용 정지), TTL = access 토큰 만료
 * auth:revoked-before:{userId}  String epoch 밀리초 — 이 시각 전에 발급된 access 거부(비밀번호 재설정), TTL 동일
 * </pre>
 *
 * <p>탈퇴 시점에 등재하면 {@link JwtAuthenticationFilter}가 해당 사용자의 기존 access 토큰을
 * 즉시 거부한다. TTL이 access 만료와 같아 자료구조가 스스로 소멸한다.
 * 이용 정지는 운영 스크립트({@code tools/moderate.py})가 redis-cli 로 {@code "S"} 를 넣는다 — 값으로 둘을 구분해
 * 정지된 사람에게 "탈퇴한 계정" 이라고 답하지 않는다.
 * 비밀번호 재설정은 "그 사람 전체" 가 아니라 <b>재설정 전에 발급된 토큰</b>만 막아야 한다(새 비밀번호로
 * 다시 로그인한 토큰은 통해야 한다) — 그래서 키를 따로 두고 발급 시각({@code iat})과 비교한다.
 * 로그아웃에는 쓰지 않는다 — 한 기기 로그아웃이 다른 기기의 access까지 죽이기 때문이다.
 */
@Component
public class AccessTokenBlocklist {

    private static final String KEY_PREFIX = "auth:blocked:";
    private static final String REVOKED_BEFORE_PREFIX = "auth:revoked-before:";
    /** 이용 정지 표시. 운영 스크립트와 약속한 값이라 바꾸면 스크립트도 함께 바꾼다. */
    public static final String SUSPENDED = "S";

    private final StringRedisTemplate redis;

    public AccessTokenBlocklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void block(long userId, Duration ttl) {
        redis.opsForValue().set(KEY_PREFIX + userId, "1", ttl);
    }

    /**
     * {@code when} 이전에 발급된 이 사용자의 access 토큰을 모두 거부한다(비밀번호 재설정, LAUNCH_REVIEW U24).
     * 밀리초로 비교한다({@code JwtTokenProvider} 의 {@code iat_ms}) — 초 단위면 재설정과 같은 초에 발급된
     * 토큰(예: 재설정과 경합한 공격자의 refresh)이 살아남는다. 재설정 뒤 새로 로그인한 토큰은 통과한다.
     */
    public void revokeIssuedBefore(long userId, Instant when, Duration ttl) {
        redis.opsForValue().set(REVOKED_BEFORE_PREFIX + userId, Long.toString(when.toEpochMilli()), ttl);
    }

    /** {@code issuedAt} 에 발급된 토큰이 막혔다면 그 이유. 막히지 않았으면 empty. Redis 왕복은 한 번(MGET). */
    public Optional<Reason> reason(long userId, Instant issuedAt) {
        List<String> values = redis.opsForValue()
                .multiGet(List.of(KEY_PREFIX + userId, REVOKED_BEFORE_PREFIX + userId));
        String blocked = values == null ? null : values.get(0);
        if (blocked != null) {
            return Optional.of(SUSPENDED.equals(blocked) ? Reason.SUSPENDED : Reason.WITHDRAWN);
        }
        String revokedBefore = values == null ? null : values.get(1);
        if (revokedBefore != null && issuedAt.toEpochMilli() < Long.parseLong(revokedBefore)) {
            return Optional.of(Reason.PASSWORD_CHANGED);
        }
        return Optional.empty();
    }

    public enum Reason { WITHDRAWN, SUSPENDED, PASSWORD_CHANGED }
}
