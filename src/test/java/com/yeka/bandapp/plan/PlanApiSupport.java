package com.yeka.bandapp.plan;

import com.yeka.bandapp.board.BoardApiSupport;
import com.yeka.bandapp.support.FakeStorageClient;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 요금제 통합 테스트 공통 헬퍼. 밴드·게시판 픽스처는 {@link BoardApiSupport} 에서 온다.
 *
 * <p>구독은 이제 스토어에서 산다 — {@link #subscribe} 는 클라이언트가 결제 후 구매 토큰을 보내는
 * {@code POST /plan/google/verify} 를 흉내낸다. no-op 게이트웨이가 토큰 접두사로 상태를 정하므로
 * 기본 토큰({@link #tokenFor})은 ACTIVE 로 검증된다. 해지·갱신은 사용자 API 가 없고 RTDN 웹훅으로만
 * 들어오므로 {@link #cancel}/{@link #renew} 는 웹훅을 쏜다.
 */
public abstract class PlanApiSupport extends BoardApiSupport {

    // Google RTDN notificationType
    protected static final int RTDN_RENEWED = 2;
    protected static final int RTDN_CANCELED = 3;
    protected static final int RTDN_PURCHASED = 4;
    protected static final int RTDN_REVOKED = 12;
    protected static final int RTDN_EXPIRED = 13;

    protected String planPath(long bandId) {
        return "/api/v1/bands/" + bandId + "/plan";
    }

    /** 밴드마다 고정된 no-op 구매 토큰 — 접두사가 없으니 ACTIVE 로 검증된다. */
    protected String tokenFor(long bandId) {
        return "tok" + bandId;
    }

    protected ResponseEntity<String> viewPlan(String token, long bandId) {
        return get(planPath(bandId), token);
    }

    /** FREE → PREMIUM: 클라이언트가 결제 후 구매 토큰을 검증받는 흐름. */
    protected ResponseEntity<String> subscribe(String token, long bandId) {
        return verifyGoogle(token, bandId, tokenFor(bandId));
    }

    protected ResponseEntity<String> verifyGoogle(String token, long bandId, String purchaseToken) {
        return post(planPath(bandId) + "/google/verify",
                "{\"purchaseToken\":\"" + purchaseToken + "\"}", token);
    }

    /** 사용자가 Play 스토어에서 해지 → RTDN CANCELED 웹훅. 응답은 웹훅 200(본문 없음). */
    protected ResponseEntity<String> cancel(String ignoredToken, long bandId) {
        return googlePlayWebhook(RTDN_CANCELED, tokenFor(bandId));
    }

    protected ResponseEntity<String> renew(String ignoredToken, long bandId) {
        return googlePlayWebhook(RTDN_RENEWED, tokenFor(bandId));
    }

    /** RTDN(Pub/Sub push) 한 건을 웹훅 엔드포인트로 보낸다. */
    protected ResponseEntity<String> googlePlayWebhook(int notificationType, String purchaseToken) {
        return googlePlayWebhook(notificationType, purchaseToken, "msg-" + System.nanoTime(), webhookSecret());
    }

    protected ResponseEntity<String> googlePlayWebhook(int notificationType, String purchaseToken,
                                                       String messageId, String secret) {
        return googlePlayWebhook(notificationType, purchaseToken, messageId, secret, Instant.now());
    }

    /** {@code publishTime} 을 직접 정한다 — 오래 재전송된 메시지를 흉내낼 때. */
    protected ResponseEntity<String> googlePlayWebhook(int notificationType, String purchaseToken,
                                                       String messageId, String secret,
                                                       Instant publishTime) {
        String notification = "{\"version\":\"1.0\",\"packageName\":\"com.yeka.bandule\","
                + "\"eventTimeMillis\":\"1700000000000\",\"subscriptionNotification\":{"
                + "\"version\":\"1.0\",\"notificationType\":" + notificationType + ","
                + "\"purchaseToken\":\"" + purchaseToken + "\",\"subscriptionId\":\"premium_yearly\"}}";
        String data = Base64.getEncoder().encodeToString(notification.getBytes(StandardCharsets.UTF_8));
        String envelope = "{\"message\":{\"data\":\"" + data + "\",\"messageId\":\"" + messageId
                + "\",\"publishTime\":\"" + publishTime + "\"},"
                + "\"subscription\":\"projects/test/subscriptions/rtdn\"}";
        String url = "/api/v1/webhooks/google-play" + (secret == null ? "" : "?token=" + secret);
        return post(url, envelope, null);
    }

    protected String webhookSecret() {
        return TEST_WEBHOOK_SECRET;
    }

    /**
     * 게시글 하나에 이미지 첨부를 올려 READY 까지 만든 뒤 mediaId 를 돌려준다.
     * ({@code MediaExpirationJobTest.uploadReady} 와 같은 절차.)
     */
    protected long uploadReadyMedia(FakeStorageClient storage, String token, long bandId, long postId) {
        long imageBytes = 1024;
        long mediaId = data(issueUploadUrl(token, bandId, postId, "image/jpeg", imageBytes))
                .get("mediaId").asLong();
        storage.putObject(storage.lastPresignedPutKey(), imageBytes, "image/jpeg");
        assertThat(completeUpload(token, bandId, postId, mediaId).getStatusCode().value()).isEqualTo(200);
        return mediaId;
    }
}
