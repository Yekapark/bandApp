package com.yeka.bandapp.plan;

import com.yeka.bandapp.plan.gateway.NoOpStoreBillingGateway;
import com.yeka.bandapp.plan.service.WithdrawnPurchaserSubscriptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제한 회원이 탈퇴하면 그 사람이 결제한 스토어 구독의 자동 갱신을 해지한다(LAUNCH_REVIEW B13).
 * 결제자는 결제 뒤 검증을 보낸 사람이다. 밴드는 결제한 기간이 끝날 때까지 PREMIUM 으로 남는다.
 */
class WithdrawalCancelsSubscriptionIntegrationTest extends PlanApiSupport {

    @Autowired
    private NoOpStoreBillingGateway gateway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WithdrawnPurchaserSubscriptions withdrawnPurchaserSubscriptions;

    private Map<String, Object> planRow(long bandId) {
        return jdbc.queryForMap(
                "select tier, purchase_token, purchased_by_user_id from band_plans where band_id = ?", bandId);
    }

    @Test
    void 결제한_사람이_탈퇴하면_자동_갱신을_해지하고_밴드는_기간_끝까지_PREMIUM() {
        String payer = signup("wcs-payer@band.app", "결제자");
        String member = signup("wcs-member@band.app", "멤버");
        long payerId = myUserId(payer);
        long bandId = createBand(payer, "해지밴드");
        join(member, issueInvite(payer, bandId, null));
        String purchase = "wcs-a-" + System.nanoTime();
        assertThat(verifyGoogle(payer, bandId, purchase).getStatusCode().value()).isEqualTo(200);
        assertThat(planRow(bandId).get("purchased_by_user_id")).isEqualTo(payerId);

        withdraw(payer);

        assertThat(gateway.cancelledRenewals()).contains(purchase);
        Map<String, Object> row = planRow(bandId);
        assertThat(row.get("tier")).isEqualTo("PREMIUM");             // 결제한 기간은 그대로
        assertThat(row.get("purchase_token")).isEqualTo(purchase);    // 웹훅이 밴드를 찾을 수 있게 남긴다
        assertThat(row.get("purchased_by_user_id")).isNull();       // 탈퇴자와의 연결은 끊는다
    }

    @Test
    void 결제자가_아닌_밴드장이_탈퇴하면_해지하지_않는다() {
        String payer = signup("wcs-payer2@band.app", "결제자");
        String next = signup("wcs-next@band.app", "다음밴드장");
        long nextId = myUserId(next);
        long bandId = createBand(payer, "위임밴드");
        join(next, issueInvite(payer, bandId, null));
        String purchase = "wcs-b-" + System.nanoTime();
        assertThat(verifyGoogle(payer, bandId, purchase).getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> delegated = post("/api/v1/bands/" + bandId + "/leader",
                "{\"newLeaderUserId\":" + nextId + "}", payer);
        assertThat(delegated.getStatusCode().value()).isEqualTo(200);

        withdraw(next);   // 지금 밴드장이지만 결제자는 아니다

        assertThat(gateway.cancelledRenewals()).doesNotContain(purchase);
        assertThat(planRow(bandId).get("purchased_by_user_id")).isNotNull();

        withdraw(payer);  // 결제자가 탈퇴하면 그때 해지

        assertThat(gateway.cancelledRenewals()).contains(purchase);
    }

    @Test
    void 이미_해지_예약한_구독도_해지를_보내_확인되면_연결을_끊는다() {
        // 요금제의 "해지 예약" 표시가 아니라 스토어가 판단한다 — 이미 끝났으면 게이트웨이가 성공으로 돌려준다(B14·B15).
        String payer = signup("wcs-payer3@band.app", "결제자");
        long bandId = createBand(payer, "해지예약밴드");
        String purchase = "wcs-c-" + System.nanoTime();
        assertThat(verifyGoogle(payer, bandId, purchase).getStatusCode().value()).isEqualTo(200);
        googlePlayWebhook(RTDN_CANCELED, purchase);   // Play 스토어에서 해지 → CANCELED 웹훅

        withdraw(payer);

        assertThat(gateway.cancelledRenewals()).contains(purchase);
        assertThat(planRow(bandId).get("purchased_by_user_id")).isNull();
    }

    @Test
    void 결제_보류로_FREE_가_된_구독도_탈퇴하면_해지한다() {
        // B15: 보류(ON_HOLD) 중엔 요금제가 FREE 지만 토큰은 남고, 결제가 복구되면 청구가 이어진다.
        String payer = signup("wcs-payer5@band.app", "결제자");
        long bandId = createBand(payer, "보류밴드");
        String purchase = "wcs-d-" + System.nanoTime();
        assertThat(verifyGoogle(payer, bandId, purchase).getStatusCode().value()).isEqualTo(200);
        jdbc.update("update band_plans set tier = 'FREE', media_retention_days = 30, subscription_ref = null, "
                + "expires_at = null where band_id = ?", bandId);   // downgradeToFree 와 같은 모양

        withdraw(payer);

        assertThat(gateway.cancelledRenewals()).contains(purchase);
        assertThat(planRow(bandId).get("purchase_token")).isEqualTo(purchase);
        assertThat(planRow(bandId).get("purchased_by_user_id")).isNull();
    }

    @Test
    void 해지_호출이_실패해도_탈퇴는_끝나고_재시도를_위해_결제자_연결은_남긴다() {
        String payer = signup("wcs-payer4@band.app", "결제자");
        long payerId = myUserId(payer);
        long bandId = createBand(payer, "실패밴드");
        String purchaseToken = "nocancel-" + System.nanoTime();
        assertThat(verifyGoogle(payer, bandId, purchaseToken).getStatusCode().value()).isEqualTo(200);

        withdraw(payer);   // 204 가 아니면 withdraw() 가 예외를 던진다

        assertThat(gateway.cancelledRenewals()).doesNotContain(purchaseToken);
        assertThat(get("/api/v1/users/me", payer).getStatusCode().value()).isEqualTo(401);
        assertThat(planRow(bandId).get("purchased_by_user_id")).isEqualTo(payerId);   // B17: 재시도 대상

        withdrawnPurchaserSubscriptions.retryPending();   // 여전히 실패 — 연결이 남아 다음에 또 시도

        assertThat(planRow(bandId).get("purchased_by_user_id")).isEqualTo(payerId);
    }

    @Test
    void 탈퇴_뒤에_결제자로_적힌_구독은_재시도_배치가_해지한다() {
        // B16: 검증과 탈퇴가 겹쳐 탈퇴가 먼저 커밋되고 결제자 기록이 뒤에 들어온 경우.
        String payer = signup("wcs-payer6@band.app", "결제자");
        long payerId = myUserId(payer);
        long bandId = createBand(payer, "경합밴드");
        String purchase = "wcs-e-" + System.nanoTime();
        assertThat(verifyGoogle(payer, bandId, purchase).getStatusCode().value()).isEqualTo(200);
        jdbc.update("update band_plans set purchased_by_user_id = null where band_id = ?", bandId);

        withdraw(payer);   // 이때는 결제자가 아직 안 적혀 해지할 게 없다
        assertThat(gateway.cancelledRenewals()).doesNotContain(purchase);
        jdbc.update("update band_plans set purchased_by_user_id = ? where band_id = ?", payerId, bandId);   // 늦게 적힘

        withdrawnPurchaserSubscriptions.retryPending();

        assertThat(gateway.cancelledRenewals()).contains(purchase);
        assertThat(planRow(bandId).get("purchased_by_user_id")).isNull();
    }
}
