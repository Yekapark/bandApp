package com.yeka.bandapp.plan.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeka.bandapp.plan.service.StoreSubscriptionService;
import com.yeka.bandapp.plan.service.StoreWebhookRetryException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * Google Play RTDN(Real-time Developer Notifications) 수신. Play Console 에 등록한 Pub/Sub 토픽의
 * <b>push 구독</b>이 이 엔드포인트로 POST 한다. 본문은 Pub/Sub push 봉투이고, 안의
 * {@code message.data} 를 base64 디코드하면 {@code DeveloperNotification} JSON 이다.
 *
 * <p><b>인증</b>({@link WebhookAuthenticator}): Pub/Sub 구독에 인증 서비스 계정을 지정하면 Google 이
 * 붙이는 {@code Authorization: Bearer <OIDC JWT>} 를 Google 공개키로 검증하거나, push 구독 URL 의
 * {@code ?token=<시크릿>} 이 {@code app.plan.billing.webhook-secret} 과 같아야 한다. 둘 다 설정이
 * 없으면 전부 거부(fail-closed).
 *
 * <p>항상 {@code 200} 을 준다(인증 실패·일시적 처리 실패 제외) — Pub/Sub 는 2xx 가 아니면 재전송하는데,
 * 우리 쪽 멱등 처리({@code processed_store_events})가 중복을 흡수하므로 재전송보다 "받았다"가 낫다.
 */
@Tag(name = "16. 요금제", description = "Google Play RTDN 웹훅 (내부용 — Pub/Sub push 전용).")
@RestController
@RequestMapping("/api/v1/webhooks/google-play")
public class GooglePlayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GooglePlayWebhookController.class);

    private final StoreSubscriptionService storeSubscriptionService;
    private final WebhookAuthenticator authenticator;
    private final ObjectMapper objectMapper;

    public GooglePlayWebhookController(StoreSubscriptionService storeSubscriptionService,
                                      WebhookAuthenticator authenticator, ObjectMapper objectMapper) {
        this.storeSubscriptionService = storeSubscriptionService;
        this.authenticator = authenticator;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "RTDN 수신", description = "Pub/Sub push 전용. OIDC Bearer 또는 ?token= 시크릿 필요.")
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestParam(name = "token", required = false) String token,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody PubsubPushEnvelope envelope) {
        if (!authenticator.isAuthorized(authorization, token)) {
            log.warn("Google Play 웹훅: 인증 실패 — 거부");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            handle(envelope);
        } catch (StoreWebhookRetryException retryable) {
            // 일시적 실패 — Pub/Sub 가 재전송하도록 5xx 를 준다(멱등 기록은 아직 안 남았다).
            log.warn("Google Play 웹훅: 재시도 요청 — {}", retryable.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } catch (RuntimeException e) {
            // 파싱·처리 실패를 재전송으로 되돌리지 않는다 — 잘못된 메시지는 재전송해도 똑같이 실패한다.
            log.error("Google Play 웹훅 처리 실패", e);
        }
        return ResponseEntity.ok().build();
    }

    private void handle(PubsubPushEnvelope envelope) {
        PubsubMessage message = envelope == null ? null : envelope.message();
        if (message == null || message.data() == null || message.data().isBlank()) {
            log.info("Google Play 웹훅: 빈 메시지 — 무시");
            return;
        }
        byte[] json = Base64.getDecoder().decode(message.data());
        DeveloperNotification notification = readNotification(json);
        if (notification == null || notification.subscriptionNotification() == null) {
            // testNotification / oneTimeProductNotification / voidedPurchaseNotification 은 이 릴리스에서 안 쓴다.
            log.info("Google Play 웹훅: 구독 알림 아님 — 무시");
            return;
        }
        SubscriptionNotification sub = notification.subscriptionNotification();
        storeSubscriptionService.handleGoogleNotification(
                message.messageId(), sub.notificationType(), sub.purchaseToken(), message.publishTime());
    }

    private DeveloperNotification readNotification(byte[] json) {
        try {
            return objectMapper.readValue(json, DeveloperNotification.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("RTDN 페이로드 파싱 실패", e);
        }
    }

    // --- Pub/Sub push 봉투 -----------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PubsubPushEnvelope(PubsubMessage message, String subscription) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PubsubMessage(String data, String messageId, String publishTime) {
    }

    // --- Google Play DeveloperNotification ----------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeveloperNotification(String version, String packageName, String eventTimeMillis,
                                        SubscriptionNotification subscriptionNotification,
                                        TestNotification testNotification) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubscriptionNotification(String version, int notificationType, String purchaseToken,
                                           String subscriptionId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TestNotification(String version) {
    }
}
