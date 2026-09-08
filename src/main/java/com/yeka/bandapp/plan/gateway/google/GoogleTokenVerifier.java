package com.yeka.bandapp.plan.gateway.google;

import com.google.auth.oauth2.TokenVerifier;
import com.yeka.bandapp.plan.config.StoreBillingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link PubSubOidcVerifier} 를 Google {@code TokenVerifier}(공개키 캐시 내장)로 구현한다.
 *
 * <p>{@code app.plan.billing.google-pubsub-audience} 가 비어 있으면 검증기를 만들지 않고 항상
 * {@code empty} 를 돌려준다 — 그 경우 웹훅 인증은 {@code ?token=} 공유 시크릿으로만 한다
 * ({@code WebhookAuthenticator}).
 */
@Component
public class GoogleTokenVerifier implements PubSubOidcVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";

    private final TokenVerifier verifier; // null 이면 OIDC 검증 비활성

    public GoogleTokenVerifier(StoreBillingProperties properties) {
        String audience = properties.googlePubsubAudience();
        if (audience == null) {
            this.verifier = null;
            log.info("Pub/Sub OIDC 검증 비활성 (google-pubsub-audience 미설정) — 웹훅은 공유 시크릿으로만 인증");
        } else {
            this.verifier = TokenVerifier.newBuilder()
                    .setAudience(audience)
                    .setIssuer(GOOGLE_ISSUER)
                    .build();
            log.info("Pub/Sub OIDC 검증 활성 audience={}", audience);
        }
    }

    @Override
    public Optional<String> verifiedEmail(String bearerToken) {
        if (verifier == null || bearerToken == null || bearerToken.isBlank()) {
            return Optional.empty();
        }
        try {
            Object email = verifier.verify(bearerToken).getPayload().get("email");
            return email == null ? Optional.empty() : Optional.of(email.toString());
        } catch (TokenVerifier.VerificationException e) {
            log.warn("Pub/Sub OIDC 토큰 검증 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
