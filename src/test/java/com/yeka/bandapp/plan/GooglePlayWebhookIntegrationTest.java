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
    void recovered_after_account_hold_restores_premium() {
        // 카드 결제 실패 → 계정 보류(ON_HOLD) 로 FREE → 결제 수단을 고쳐 RECOVERED. 같은 토큰으로 온다.
        // 예전에는 보류 때 토큰을 지워 RECOVERED 가 밴드를 못 찾고 버려졌다(LAUNCH_REVIEW B1).
        String leader = signup("wh-hold@band.app", "리더");
        long bandId = createBand(leader, "보류밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        assertThat(googlePlayWebhook(RTDN_ON_HOLD, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("FREE");
        assertThat(storedToken(bandId)).isEqualTo(tokenFor(bandId));

        assertThat(googlePlayWebhook(RTDN_RECOVERED, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("PREMIUM");
        assertThat(expiresAt(bandId)).isAfter(Instant.now().plus(300, ChronoUnit.DAYS));
    }

    @Test
    void restarted_after_expiry_restores_premium() {
        String leader = signup("wh-restart@band.app", "리더");
        long bandId = createBand(leader, "재구독밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        assertThat(googlePlayWebhook(RTDN_EXPIRED, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("FREE");

        assertThat(googlePlayWebhook(RTDN_RESTARTED, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("PREMIUM");
    }

    @Test
    void revoked_forgets_the_token() {
        String leader = signup("wh-revoke-token@band.app", "리더");
        long bandId = createBand(leader, "환불토큰밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        assertThat(googlePlayWebhook(RTDN_REVOKED, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);

        assertThat(storedToken(bandId)).isNull();
    }

    private String storedToken(long bandId) {
        return jdbc.queryForObject("select purchase_token from band_plans where band_id = ?", String.class, bandId);
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
    void a_purchase_the_app_never_verified_is_granted_from_its_band_tag() {
        // LAUNCH_REVIEW B12 — 결제 직후 앱이 꺼지고 다시 안 열려도, 구매에 적힌 밴드로 웹훅이 올리고 확인 처리한다.
        // (예전에는 verify 를 기다리다 포기했고, 3일 뒤 Google 이 자동 환불했다.)
        String leader = signup("wh-b12@band.app", "리더");
        long bandId = createBand(leader, "앱안연밴드");
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("FREE");

        assertThat(googlePlayWebhook(RTDN_PURCHASED, "tok-b12@band-" + bandId).getStatusCode().value())
                .isEqualTo(200);

        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("PREMIUM");
        Integer marked = jdbc.queryForObject("select count(*) from processed_store_events", Integer.class);
        assertThat(marked).isEqualTo(1);
    }

    @Test
    void a_tagged_purchase_for_a_band_that_no_longer_exists_is_not_retried() {
        // 적힌 밴드가 없어졌으면 재전송해도 같다 — 200 으로 끝내고(재전송 폭풍 없음) 자동 환불에 맡긴다.
        assertThat(googlePlayWebhook(RTDN_PURCHASED, "tok-gone@band-987654321").getStatusCode().value())
                .isEqualTo(200);
    }

    @Test
    void a_tagged_purchase_waits_for_the_store_when_it_is_briefly_down() {
        assertThat(googlePlayWebhook(RTDN_PURCHASED, "unavailable-b12@band-1").getStatusCode().value())
                .isEqualTo(503);
    }

    @Test
    void recovery_that_arrives_while_the_store_is_briefly_down_is_retried_not_dropped() {
        // 예전에는 스토어 일시 장애 예외가 "처리 실패" 로 200 이 돼 RECOVERED 가 사라졌다 — 돈을 냈는데 FREE.
        String leader = signup("wh-recover-down@band.app", "리더");
        long bandId = createBand(leader, "복구장애밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        jdbc.update("update band_plans set purchase_token = 'unavailable-hold' where band_id = ?", bandId);
        assertThat(googlePlayWebhook(RTDN_ON_HOLD, "unavailable-hold").getStatusCode().value()).isEqualTo(200);
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("FREE");

        // 이제 스토어가 잠깐 답하지 않는다("unavailable-" 접두사) — 복구 알림은 재전송받아야 한다.
        assertThat(googlePlayWebhook(RTDN_RECOVERED, "unavailable-hold", "recover-down", webhookSecret())
                .getStatusCode().value()).isEqualTo(503);
        Integer marked = jdbc.queryForObject(
                "select count(*) from processed_store_events where message_id = 'recover-down'", Integer.class);
        assertThat(marked).isZero();
    }

    @Test
    void a_late_on_hold_after_recovery_does_not_downgrade_a_paying_band() {
        // Pub/Sub 는 순서를 보장하지 않는다 — 복구(RECOVERED)보다 늦게 온 보류 알림. 스토어는 이미 ACTIVE.
        String leader = signup("wh-late-hold@band.app", "리더");
        long bandId = createBand(leader, "늦은보류밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        assertThat(googlePlayWebhookKeepingStoreState(RTDN_ON_HOLD, tokenFor(bandId), "late-hold",
                webhookSecret(), Instant.now()).getStatusCode().value()).isEqualTo(200);

        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("PREMIUM");
    }

    @Test
    void a_late_cancel_after_resubscribing_keeps_the_band_auto_renewing() {
        // 해지 → Play 에서 복원(RESTARTED) 뒤에 늦게 온 CANCELED. 해지 예약으로 보이면 결제가 이어지는데도
        // 밴드를 지울 수 있고(B5), 결제자가 탈퇴해도 해지 요청 대상에서 빠진다(B13).
        String leader = signup("wh-late-cancel@band.app", "리더");
        long bandId = createBand(leader, "늦은해지밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        assertThat(googlePlayWebhookKeepingStoreState(RTDN_CANCELED, tokenFor(bandId), "late-cancel",
                webhookSecret(), Instant.now()).getStatusCode().value()).isEqualTo(200);

        assertThat(data(viewPlan(leader, bandId)).get("canceled").asBoolean()).isFalse();
        assertThat(data(viewPlan(leader, bandId)).get("autoRenewing").asBoolean()).isTrue();
    }

    @Test
    void verifying_a_canceled_subscription_again_keeps_it_canceled() {
        // 해지한 구독을 앱이 다시 검증·복구해도 "자동 갱신 중" 으로 되돌리지 않는다.
        String leader = signup("wh-reverify@band.app", "리더");
        long bandId = createBand(leader, "재검증밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        assertThat(cancel(leader, bandId).getStatusCode().value()).isEqualTo(200);
        assertThat(data(viewPlan(leader, bandId)).get("canceled").asBoolean()).isTrue();

        var verified = verifyGoogle(leader, bandId, tokenFor(bandId));   // 스토어는 여전히 CANCELED

        assertThat(verified.getStatusCode().value()).isEqualTo(200);
        assertThat(data(verified).get("canceled").asBoolean()).isTrue();
        assertThat(data(verified).get("autoRenewing").asBoolean()).isFalse();
        assertThat(data(viewPlan(leader, bandId)).get("canceled").asBoolean()).isTrue();
    }

    @Test
    void a_coupon_used_after_account_hold_survives_the_old_subscription_expiring() {
        // 보류로 FREE → 쿠폰으로 PREMIUM(옛 토큰은 남음, B1) → 옛 구독이 보류 끝에 EXPIRED. 쿠폰 기간이 사라지면 안 된다.
        String leader = signup("wh-coupon-hold@band.app", "리더");
        long bandId = createBand(leader, "보류쿠폰밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        assertThat(googlePlayWebhook(RTDN_ON_HOLD, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);
        jdbc.update("insert into plan_coupons (code, grant_days, max_uses, expires_at, created_at) "
                + "values ('HOLDCPN1', 30, null, null, now())");
        assertThat(redeemCoupon(leader, bandId, "HOLDCPN1").getStatusCode().value()).isEqualTo(200);

        assertThat(googlePlayWebhook(RTDN_EXPIRED, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);
        assertThat(googlePlayWebhook(RTDN_CANCELED, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);

        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("PREMIUM");
        assertThat(jdbc.queryForObject("select subscription_ref from band_plans where band_id = ?",
                String.class, bandId)).isEqualTo("coupon-HOLDCPN1");
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
