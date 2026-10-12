package com.yeka.bandapp.plan;

import com.yeka.bandapp.plan.gateway.StoreBillingGateway.StoreSubscriptionState;
import com.yeka.bandapp.plan.service.StoreSubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 돈은 들어왔는데 밴드를 못 찾은 구독(V24, QA BILL-31) — 기록되고, 해결될 때까지 남고, 서버가 스스로 닫거나 운영자가 정한
 * 밴드에 반영한다. 만료 뒤 Play 스토어 「다시 구독」 은 밴드 표시 없이 오고, 이어받은 토큰의 밴드가 없으면 여기로 온다.
 */
class UnmatchedStorePurchaseIntegrationTest extends PlanApiSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StoreSubscriptionService storeSubscriptionService;

    private void staleGrant(int type, String token) {
        assertThat(googlePlayWebhook(type, token, "msg-" + System.nanoTime(), webhookSecret(),
                Instant.now().minus(2, ChronoUnit.HOURS)).getStatusCode().value()).isEqualTo(200);
    }

    private int openCount() {
        return jdbc.queryForObject("select count(*) from unmatched_store_purchases where resolved_at is null",
                Integer.class);
    }

    private Map<String, Object> row(String token) {
        return jdbc.queryForMap("select * from unmatched_store_purchases where purchase_token = ?", token);
    }

    @Test
    void a_paid_resubscription_whose_old_band_is_gone_is_recorded_once() {
        storeLinks("tok-resub-gone", "tok-old-of-a-deleted-band");

        staleGrant(RTDN_RESTARTED, "tok-resub-gone");
        staleGrant(RTDN_RENEWED, "tok-resub-gone"); // 같은 토큰이 또 와도 한 행

        assertThat(openCount()).isEqualTo(1);
        Map<String, Object> r = row("tok-resub-gone");
        assertThat(r.get("notification_type")).isEqualTo(RTDN_RESTARTED);
        assertThat(r.get("linked_purchase_token")).isEqualTo("tok-old-of-a-deleted-band");
        assertThat(r.get("order_id")).isEqualTo("noop-order-tok-resub-gone");
    }

    @Test
    void nothing_is_recorded_while_still_waiting_for_the_app_or_when_no_money_is_left() {
        // 한 시간 안 = 앱 검증과의 경합 — 재전송을 기다린다.
        assertThat(googlePlayWebhook(RTDN_PURCHASED, "tok-race").getStatusCode().value()).isEqualTo(503);
        // 해지·만료 같은 알림, 스토어에서 이미 끝난 구독은 돈이 남지 않는다.
        staleGrant(RTDN_EXPIRED, "tok-expired-event");
        staleGrant(RTDN_RENEWED, "expired-already-over");

        assertThat(jdbc.queryForObject("select count(*) from unmatched_store_purchases", Integer.class)).isZero();
    }

    @Test
    void it_closes_itself_once_the_purchase_reaches_a_band_some_other_way() {
        staleGrant(RTDN_PURCHASED, "tok-late-verify");
        String leader = signup("um-late@band.app", "리더");
        long bandId = createBand(leader, "늦은검증밴드");
        assertThat(verifyGoogle(leader, bandId, "tok-late-verify").getStatusCode().value()).isEqualTo(200);

        assertThat(storeSubscriptionService.resolveUnmatchedPurchases()).isEqualTo(1);

        assertThat(openCount()).isZero();
        assertThat((String) row("tok-late-verify").get("resolution")).contains("밴드 " + bandId, "자동");
    }

    @Test
    void it_closes_itself_when_the_store_refunds_it() {
        // 확인 처리를 못 받은 구매는 3일 뒤 Google 이 자동 환불한다 — 운영자가 아무것도 안 해도 경보가 꺼진다.
        staleGrant(RTDN_PURCHASED, "tok-auto-refund");
        storeSays("tok-auto-refund", StoreSubscriptionState.REVOKED);

        assertThat(storeSubscriptionService.resolveUnmatchedPurchases()).isEqualTo(1);

        assertThat((String) row("tok-auto-refund").get("resolution")).contains("스토어에서 끝남", "REVOKED");
    }

    @Test
    void the_band_an_operator_picks_gets_premium() {
        String leader = signup("um-pick@band.app", "리더");
        long bandId = createBand(leader, "운영자연결밴드");
        staleGrant(RTDN_RESTARTED, "tok-operator-link");
        assertThat(storeSubscriptionService.resolveUnmatchedPurchases()).isZero(); // 정할 때까지 그대로

        // tools/purchases.py link 가 하는 일
        jdbc.update("update unmatched_store_purchases set target_band_id = ? where purchase_token = ?",
                bandId, "tok-operator-link");
        assertThat(storeSubscriptionService.resolveUnmatchedPurchases()).isEqualTo(1);

        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("PREMIUM");
        assertThat((String) row("tok-operator-link").get("resolution")).contains("밴드 " + bandId, "운영자");
        assertThat(openCount()).isZero();
    }

    @Test
    void a_pick_the_band_cannot_take_stays_open() {
        staleGrant(RTDN_RESTARTED, "tok-bad-pick");
        jdbc.update("update unmatched_store_purchases set target_band_id = 987654321 where purchase_token = ?",
                "tok-bad-pick");

        assertThat(storeSubscriptionService.resolveUnmatchedPurchases()).isZero();

        assertThat(openCount()).isEqualTo(1);
    }
}
