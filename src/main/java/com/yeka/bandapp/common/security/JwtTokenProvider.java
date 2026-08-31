package com.yeka.bandapp.common.security;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 발급·파싱 전담. 도메인 타입을 모르며 입력은 {@code long userId}뿐이다.
 *
 * <p>두 토큰 모두 HS256 서명이고 {@code typ} 클레임으로 용도를 구분한다.
 * {@code typ} 검증이 없으면 refresh 토큰을 Authorization 헤더에 넣어 장수명 access처럼 쓸 수 있다.
 * refresh 토큰에는 Redis 세션 식별자로 쓰는 {@code jti}가 들어간다.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public TokenPair issue(long userId) {
        Instant now = Instant.now();
        String access = build(userId, TYPE_ACCESS, null, now, properties.accessTokenTtl());
        String jti = UUID.randomUUID().toString();
        String refresh = build(userId, TYPE_REFRESH, jti, now, properties.refreshTokenTtl());
        return new TokenPair(access, refresh, jti, properties.accessTokenTtl());
    }

    /** access 토큰 파싱. 실패 시 {@link ErrorCode#ACCESS_TOKEN_EXPIRED} 또는 {@link ErrorCode#INVALID_TOKEN}. */
    public ParsedToken parseAccess(String token) {
        return parse(token, TYPE_ACCESS);
    }

    /** refresh 토큰 파싱. 실패 시 {@link ErrorCode#REFRESH_TOKEN_INVALID}. */
    public ParsedToken parseRefresh(String token) {
        return parse(token, TYPE_REFRESH);
    }

    private String build(long userId, String type, String jti, Instant now, Duration ttl) {
        var builder = Jwts.builder()
                .issuer(properties.issuer())
                .subject(Long.toString(userId))
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key);
        if (jti != null) {
            builder.id(jti);
        }
        return builder.compact();
    }

    private ParsedToken parse(String token, String expectedType) {
        boolean access = TYPE_ACCESS.equals(expectedType);
        Claims claims;
        try {
            claims = Jwts.parser()
                    .requireIssuer(properties.issuer())
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(access ? ErrorCode.ACCESS_TOKEN_EXPIRED : ErrorCode.REFRESH_TOKEN_INVALID);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(access ? ErrorCode.INVALID_TOKEN : ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new BusinessException(access ? ErrorCode.INVALID_TOKEN : ErrorCode.REFRESH_TOKEN_INVALID);
        }
        return new ParsedToken(Long.parseLong(claims.getSubject()), claims.getId());
    }

    public record ParsedToken(Long userId, String jti) {
    }
}
