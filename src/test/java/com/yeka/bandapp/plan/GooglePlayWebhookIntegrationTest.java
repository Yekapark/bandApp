package com.yeka.bandapp.plan;

import com.yeka.bandapp.support.FakeStorageClient;
import com.yeka.bandapp.support.StorageTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Google Play RTDN(Pub/Sub push) 웹훅 반영. no-op 게이트웨이가 토큰 접두사로 재조회 상태를 흉내낸다.
 */
@Import(StorageTestConfig.class)
class GooglePlayWebhookIntegrationTest extends PlanApiSupport {

    @Autowired
    private FakeStorageClient storage;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    @Test
    void renewed_extends_the_expiry() {
        String leader = signup("wh-renew@band.app", "리더");
        long bandId = createBand(leader, "갱신밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        Instant before = expiresAt(bandId);
        // 만료일을 과거 근처로 당겨서, 갱신이 확실히 미래로 밀어내는지 본다.
        jdbc.update("update band_plans set expires_at = ? where band_id = ?",
                Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)), bandId);

        assertThat(googlePlayWebhook(RTDN_RENEWED, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);

        assertThat(expiresAt(bandId)).isAfter(Instant.now().plus(300, ChronoUnit.DAYS));
        assertThat(before).isNotNull();
    }

    @Test
    void revoked_downgrades_immediately_and_expires_media_now() {
        String leader = signup("wh-revoke@band.app", "리더");
        long bandId = createBand(leader, "환불밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = uploadReadyMedia(storage, leader, bandId, postId);
        assertThat(mediaExpiresAt(mediaId)).isNull(); // PREMIUM 무제한

        assertThat(googlePlayWebhook(RTDN_REVOKED, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);

        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("FREE");
        // 유예 없이 지금 만료 — 다음 미디어 정리 배치가 바로 지운다.
        assertThat(mediaExpiresAt(mediaId)).isBeforeOrEqualTo(Instant.now().plusSeconds(5));
    }

    @Test
    void duplicate_message_id_is_applied_once() {
        String leader = signup("wh-dup@band.app", "리더");
        long bandId = createBand(leader, "중복밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        String msgId = "dup-msg-1";
        assertThat(googlePlayWebhook(RTDN_REVOKED, tokenFor(bandId), msgId, webhookSecret())
                .getStatusCode().value()).isEqualTo(200);
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("FREE");

        // 다시 PREMIUM 으로 올려놓고, 같은 messageId 의 REVOKED 를 재전송 — 이번엔 무시돼야 한다.
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        assertThat(googlePlayWebhook(RTDN_REVOKED, tokenFor(bandId), msgId, webhookSecret())
                .getStatusCode().value()).isEqualTo(200);
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("PREMIUM");
    }

    @Test
    void unknown_purchase_token_is_a_no_op_200() {
        assertThat(googlePlayWebhook(RTDN_RENEWED, "tok-nobody-has-this").getStatusCode().value())
                .isEqualTo(200);
    }

    @Test
    void wrong_secret_is_rejected_without_touching_state() {
        String leader = signup("wh-sec@band.app", "리더");
        long bandId = createBand(leader, "시크릿밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        assertThat(googlePlayWebhook(RTDN_REVOKED, tokenFor(bandId), "m-bad", "nope")
                .getStatusCode().value()).isEqualTo(403);
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("PREMIUM");
    }

    private Instant expiresAt(long bandId) {
        Timestamp ts = jdbc.queryForObject(
                "select expires_at from band_plans where band_id = ?", Timestamp.class, bandId);
        return ts == null ? null : ts.toInstant();
    }

    private Instant mediaExpiresAt(long mediaId) {
        Timestamp ts = jdbc.queryForObject(
                "select expires_at from media_attachments where id = ?", Timestamp.class, mediaId);
        return ts == null ? null : ts.toInstant();
    }
}
