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
    void renewed_with_a_failed_store_lookup_asks_pubsub_to_retry() {
        String leader = signup("wh-retry@band.app", "리더");
        long bandId = createBand(leader, "재시도밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        // 조회가 실패하는(=noop 이 empty 를 주는) 토큰으로 바꿔 둔다.
        jdbc.update("update band_plans set purchase_token = 'invalid-x' where band_id = ?", bandId);

        // 갱신 알림인데 스토어 조회가 안 되면 삼키지 말고 5xx → Pub/Sub 재전송.
        assertThat(googlePlayWebhook(RTDN_RENEWED, "invalid-x").getStatusCode().value()).isEqualTo(503);
        // 기록도 남기지 않아 재전송이 다시 처리된다.
        Integer marked = jdbc.queryForObject("select count(*) from processed_store_events", Integer.class);
        assertThat(marked).isZero();
    }

    @Test
    void unknown_token_on_a_terminal_event_is_a_no_op_200() {
        // 종료성 이벤트(REVOKED/EXPIRED/CANCELED)는 이미 밴드에서 토큰이 지워졌을 수 있다 — 무시.
        assertThat(googlePlayWebhook(RTDN_REVOKED, "tok-nobody-has-this").getStatusCode().value())
                .isEqualTo(200);
    }

    @Test
    void unknown_token_on_a_grant_event_asks_pubsub_to_retry() {
        // 갱신·구매인데 아직 밴드에 토큰이 안 붙었다 = verify 가 곧 온다 — 재전송받는다.
        assertThat(googlePlayWebhook(RTDN_RENEWED, "tok-not-linked-yet").getStatusCode().value())
                .isEqualTo(503);
    }

    @Test
    void a_stale_grant_event_is_given_up_on_instead_of_retried_forever() {
        // 한 시간이 지나도록 밴드에 안 붙은 구매 토큰 = 클라이언트 verify 가 영영 안 온다.
        // 계속 503 을 주면 Pub/Sub 가 보존기간(7일) 내내 재전송해 서버를 때린다 — 2026-09-09 에
        // 실제로 10분에 600건이 들어왔다. 포기하고 200 으로 받아 끝낸다.
        assertThat(googlePlayWebhook(RTDN_RENEWED, "tok-stale-forever", "msg-stale",
                webhookSecret(), Instant.now().minus(2, ChronoUnit.HOURS))
                .getStatusCode().value()).isEqualTo(200);
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
