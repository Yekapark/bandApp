package com.yeka.bandapp.plan;

import com.yeka.bandapp.plan.gateway.StoreBillingGateway.StoreSubscriptionState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 한 밴드에 구독이 둘 붙지 않게 한다 — 결제 보류 중인 밴드에서 다시 결제하면 보류가 풀릴 때 두 번 청구된다.
 * 살아 있는 옛 구독이 있으면 새 구매를 409 로 거절하고 확인 처리하지 않아 Google 이 자동 환불한다.
 * 함께: 결제 보류 표시({@code onHold}), 구매 토큰 유니크(V22).
 */
class BandDoubleSubscriptionIntegrationTest extends PlanApiSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private String storedToken(long bandId) {
        return jdbc.queryForObject("select purchase_token from band_plans where band_id = ?", String.class, bandId);
    }

    private boolean onHold(String leader, long bandId) {
        return data(viewPlan(leader, bandId)).get("onHold").asBoolean();
    }

    @Test
    void 결제_보류_중이면_onHold_이고_복구되면_풀린다() {
        String leader = signup("dbl-hold@band.app", "리더");
        long bandId = createBand(leader, "보류표시밴드");
        assertThat(onHold(leader, bandId)).isFalse();
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        assertThat(onHold(leader, bandId)).isFalse();

        assertThat(googlePlayWebhook(RTDN_ON_HOLD, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("FREE");
        assertThat(onHold(leader, bandId)).isTrue();

        assertThat(googlePlayWebhook(RTDN_RECOVERED, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("PREMIUM");
        assertThat(onHold(leader, bandId)).isFalse();
    }

    @Test
    void 보류가_만료로_끝나면_onHold_가_풀린다() {
        String leader = signup("dbl-hold-exp@band.app", "리더");
        long bandId = createBand(leader, "보류만료밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        assertThat(googlePlayWebhook(RTDN_ON_HOLD, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);
        assertThat(onHold(leader, bandId)).isTrue();

        assertThat(googlePlayWebhook(RTDN_EXPIRED, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);

        assertThat(onHold(leader, bandId)).isFalse();
        assertThat(storedToken(bandId)).isEqualTo(tokenFor(bandId));   // 토큰은 남는다(B1)
    }

    @Test
    void 보류_중에_해지되면_onHold_가_풀린다() {
        // 결제자가 나가 서버가 해지했거나 Play 에서 해지 — 보류가 풀려도 다시 청구되지 않는다. "결제 수단을 고치라" 가 틀린 안내가 된다.
        String leader = signup("dbl-hold-cancel@band.app", "리더");
        long bandId = createBand(leader, "보류해지밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        assertThat(googlePlayWebhook(RTDN_ON_HOLD, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);
        assertThat(onHold(leader, bandId)).isTrue();

        assertThat(googlePlayWebhook(RTDN_CANCELED, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);

        assertThat(onHold(leader, bandId)).isFalse();
    }

    @Test
    void 결제_보류_중인_밴드에_새_결제는_409_이고_옛_구독을_덮지_않는다() {
        String leader = signup("dbl-hold-buy@band.app", "리더");
        long bandId = createBand(leader, "이중결제밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        assertThat(googlePlayWebhook(RTDN_ON_HOLD, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> second = verifyGoogle(leader, bandId, "second-" + bandId);

        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(second)).isEqualTo("BAND_ALREADY_SUBSCRIBED");
        assertThat(storedToken(bandId)).isEqualTo(tokenFor(bandId));
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("FREE");
        assertThat(onHold(leader, bandId)).isTrue();
    }

    @Test
    void 자동_갱신_중이거나_해지_예약만_한_밴드에도_새_결제는_409() {
        String leader = signup("dbl-active@band.app", "리더");
        long bandId = createBand(leader, "활성밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        assertThat(errorCode(verifyGoogle(leader, bandId, "second-a-" + bandId))).isEqualTo("BAND_ALREADY_SUBSCRIBED");

        assertThat(cancel(leader, bandId).getStatusCode().value()).isEqualTo(200);   // 해지 예약, 기간은 남음
        assertThat(errorCode(verifyGoogle(leader, bandId, "second-b-" + bandId))).isEqualTo("BAND_ALREADY_SUBSCRIBED");
        assertThat(storedToken(bandId)).isEqualTo(tokenFor(bandId));
    }

    @Test
    void 옛_구독이_끝났으면_새_결제를_붙인다() {
        String leader = signup("dbl-ended@band.app", "리더");
        long bandId = createBand(leader, "재결제밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        assertThat(googlePlayWebhook(RTDN_EXPIRED, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> fresh = verifyGoogle(leader, bandId, "fresh-" + bandId);

        assertThat(fresh.getStatusCode().value()).isEqualTo(200);
        assertThat(data(fresh).get("tier").asText()).isEqualTo("PREMIUM");
        assertThat(storedToken(bandId)).isEqualTo("fresh-" + bandId);
    }

    @Test
    void 이전_구독_상태를_스토어가_답하지_않으면_붙이지_않는다() {
        String leader = signup("dbl-down@band.app", "리더");
        long bandId = createBand(leader, "장애밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        jdbc.update("update band_plans set purchase_token = 'unavailable-old' where band_id = ?", bandId);

        ResponseEntity<String> res = verifyGoogle(leader, bandId, "fresh2-" + bandId);

        assertThat(res.getStatusCode().value()).isEqualTo(402);   // 잠시 후 다시 — 앱이 다시 보낸다
        assertThat(storedToken(bandId)).isEqualTo("unavailable-old");
    }

    @Test
    void 웹훅으로_온_두번째_구매도_살아_있는_구독을_덮지_않는다() {
        String leader = signup("dbl-webhook@band.app", "리더");
        long bandId = createBand(leader, "웹훅이중밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        storeSays(tokenFor(bandId), StoreSubscriptionState.ON_HOLD);

        // 앱 확인 없이 구매에 적힌 밴드로 반영하는 경로(B12). 반영 불가 — 처리 완료(200)로 두고 확인 처리 안 함 → 자동 환불.
        assertThat(googlePlayWebhook(RTDN_PURCHASED, "tok-dbl@band-" + bandId).getStatusCode().value()).isEqualTo(200);

        assertThat(storedToken(bandId)).isEqualTo(tokenFor(bandId));
    }

    @Test
    void 만료_뒤_Play_스토어에서_재구독하면_이전_토큰의_밴드에_붙는다() {
        // 새 토큰엔 밴드 표시가 없고 linkedPurchaseToken 만 이전 토큰을 가리킨다(결정 #50).
        String leader = signup("dbl-resub@band.app", "리더");
        long bandId = createBand(leader, "재구독밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        assertThat(googlePlayWebhook(RTDN_EXPIRED, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("FREE");
        String resub = "resub-" + bandId;
        storeLinks(resub, tokenFor(bandId));

        assertThat(googlePlayWebhook(RTDN_PURCHASED, resub).getStatusCode().value()).isEqualTo(200);

        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("PREMIUM");
        assertThat(storedToken(bandId)).isEqualTo(resub);
    }

    @Test
    void 이전_구독이_살아_있으면_이어받은_새_구독도_붙이지_않는다() {
        String leader = signup("dbl-resub-alive@band.app", "리더");
        long bandId = createBand(leader, "재구독활성밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        String resub = "resub-alive-" + bandId;
        storeLinks(resub, tokenFor(bandId));

        assertThat(googlePlayWebhook(RTDN_PURCHASED, resub).getStatusCode().value()).isEqualTo(200);

        assertThat(storedToken(bandId)).isEqualTo(tokenFor(bandId));   // B18 — 살아 있는 구독을 덮지 않는다
    }

    @Test
    void 쿠폰_PREMIUM_은_onHold_가_아니다() {
        String leader = signup("dbl-coupon@band.app", "리더");
        long bandId = createBand(leader, "쿠폰보류밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        assertThat(googlePlayWebhook(RTDN_ON_HOLD, tokenFor(bandId)).getStatusCode().value()).isEqualTo(200);
        jdbc.update("insert into plan_coupons (code, grant_days, max_uses, expires_at, created_at) "
                + "values ('DBLHOLD1', 30, null, null, now())");

        assertThat(redeemCoupon(leader, bandId, "DBLHOLD1").getStatusCode().value()).isEqualTo(200);

        assertThat(onHold(leader, bandId)).isFalse();
    }

    @Test
    void 같은_구매_토큰은_DB_가_두_밴드에_못_붙게_막는다() {
        String leader = signup("dbl-unique@band.app", "리더");
        long bandA = createBand(leader, "유니크A");
        long bandB = createBand(leader, "유니크B");
        assertThat(subscribe(leader, bandA).getStatusCode().value()).isEqualTo(200);

        assertThatThrownBy(() -> jdbc.update(
                "update band_plans set store = 'GOOGLE_PLAY', purchase_token = ? where band_id = ?", tokenFor(bandA), bandB))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
