package com.yeka.bandapp.plan.controller;

import com.yeka.bandapp.plan.config.StoreBillingProperties;
import com.yeka.bandapp.plan.gateway.google.PubSubOidcVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * RTDN 웹훅 요청이 정말 우리 Pub/Sub 에서 온 것인지 확인한다. 두 가지를 받아들인다:
 *
 * <ol>
 *   <li><b>OIDC Bearer 토큰</b> — Pub/Sub 구독에 인증 서비스 계정을 지정하면 Google 이 넣어 준다.
 *       {@code google-pubsub-audience} 가 설정돼 있을 때 검사하고, {@code google-pubsub-service-account}
 *       까지 설정돼 있으면 토큰의 {@code email} 이 그 값과 같아야 한다.
 *   <li><b>{@code ?token=} 공유 시크릿</b> — {@code webhook-secret} 과 상수시간 비교.
 * </ol>
 *
 * <p>둘 중 하나라도 통과하면 허용. 둘 다 설정이 없으면 전부 거부(fail-closed).
 */
@Component
public class WebhookAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(WebhookAuthenticator.class);

    private final StoreBillingProperties properties;
    private final PubSubOidcVerifier oidcVerifier;

    public WebhookAuthenticator(StoreBillingProperties properties, PubSubOidcVerifier oidcVerifier) {
        this.properties = properties;
        this.oidcVerifier = oidcVerifier;
    }

    /**
     * @param authorizationHeader 요청의 {@code Authorization} 헤더 값(예: {@code "Bearer eyJ..."}), 없으면 null
     * @param queryToken          {@code ?token=} 쿼리 파라미터 값, 없으면 null
     */
    public boolean isAuthorized(String authorizationHeader, String queryToken) {
        if (oidcAccepted(bearer(authorizationHeader))) {
            return true;
        }
        if (sharedSecretAccepted(queryToken)) {
            return true;
        }
        if (properties.googlePubsubAudience() == null && properties.webhookSecret() == null) {
            log.warn("웹훅 인증 수단이 하나도 설정되지 않았다 — 전부 거부");
        }
        return false;
    }

    private boolean oidcAccepted(String bearerToken) {
        if (properties.googlePubsubAudience() == null || bearerToken == null) {
            return false;
        }
        Optional<String> email = oidcVerifier.verifiedEmail(bearerToken);
        if (email.isEmpty()) {
            return false;
        }
        String expectedSa = properties.googlePubsubServiceAccount();
        if (expectedSa != null && !expectedSa.equalsIgnoreCase(email.get())) {
            log.warn("웹훅 OIDC: 서비스 계정 불일치 (기대={}, 실제={})", expectedSa, email.get());
            return false;
        }
        return true;
    }

    private boolean sharedSecretAccepted(String queryToken) {
        String expected = properties.webhookSecret();
        if (expected == null || queryToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), queryToken.getBytes(StandardCharsets.UTF_8));
    }

    private static String bearer(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        String prefix = "Bearer ";
        return authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())
                ? authorizationHeader.substring(prefix.length()).trim()
                : authorizationHeader.trim();
    }
}
