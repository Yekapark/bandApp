package com.yeka.bandapp.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeka.bandapp.plan.config.StoreBillingProperties;
import com.yeka.bandapp.plan.controller.GooglePlayWebhookController;
import com.yeka.bandapp.plan.controller.GooglePlayWebhookController.PubsubMessage;
import com.yeka.bandapp.plan.controller.GooglePlayWebhookController.PubsubPushEnvelope;
import com.yeka.bandapp.plan.controller.WebhookAuthenticator;
import com.yeka.bandapp.plan.service.StoreSubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * DB 일시 장애로 웹훅 처리가 실패하면 200 이 아니라 503 — Pub/Sub 가 다시 보내야 환불·보류 알림이 사라지지 않는다.
 */
class GooglePlayWebhookControllerTest {

    @Test
    void a_transient_database_failure_asks_pubsub_to_retry() {
        StoreSubscriptionService service = mock(StoreSubscriptionService.class);
        doThrow(new CannotAcquireLockException("lock timeout"))
                .when(service).handleGoogleNotification(anyString(), anyInt(), anyString(), anyString());
        WebhookAuthenticator auth = new WebhookAuthenticator(
                new StoreBillingProperties("noop", "s", null, null, null, null, null, null),
                token -> Optional.empty());
        GooglePlayWebhookController controller = new GooglePlayWebhookController(service, auth, new ObjectMapper());

        String json = "{\"subscriptionNotification\":{\"notificationType\":12,\"purchaseToken\":\"t\"}}";
        PubsubPushEnvelope envelope = new PubsubPushEnvelope(new PubsubMessage(
                Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)),
                "m1", "2026-10-02T00:00:00Z"), "sub");

        assertThat(controller.receive("s", null, envelope).getStatusCode().value()).isEqualTo(503);
    }
}
