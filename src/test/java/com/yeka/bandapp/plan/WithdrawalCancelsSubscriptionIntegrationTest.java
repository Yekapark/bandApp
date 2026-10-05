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
    void 보류_위에_쿠폰으로_PREMIUM_인_밴드도_결제자가_탈퇴하면_옛_구독을_해지한다() {
        // QA BILL-22: 보류로 FREE → 쿠폰으로 PREMIUM(옛 토큰·결제자 기록은 남음). 결제수단이 복구되면 옛 구독이 다시 청구되므로
        // 탈퇴할 때 해지해야 한다. 쿠폰 기간은 그대로 남는다.
        String payer = signup("wcs-payer7@band.app", "결제자");
        long bandId = createBand(payer, "보류쿠폰탈퇴");
        String purchase = "wcs-f-" + System.nanoTime();
        assertThat(verifyGoogle(payer, bandId, purchase).getStatusCode().value()).isEqualTo(200);
        assertThat(googlePlayWebhook(RTDN_ON_HOLD, purchase).getStatusCode().value()).isEqualTo(200);
        jdbc.update("insert into plan_coupons (code, grant_days, max_uses, expires_at, created_at) "
                + "values ('WCSHOLD1', 30, null, null, now())");
        assertThat(redeemCoupon(payer, bandId, "WCSHOLD1").getStatusCode().value()).isEqualTo(200);
        assertThat(planRow(bandId).get("tier")).isEqualTo("PREMIUM");

        withdraw(payer);

        assertThat(gateway.cancelledRenewals()).contains(purchase);
        assertThat(planRow(bandId).get("tier")).isEqualTo("PREMIUM");         // 쿠폰 기간은 그대로
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

    /** QA BILL-32 — 같은 Google 계정을 쓰는 다른 앱 계정 B 가 복원해도 처음 결제자 A 가 남는다. B 가 떠나면 해지 없음, A 가 떠나면 해지. */
    @Test
    void 다른_앱_계정이_복원해도_처음_결제자가_남고_그_사람이_떠날_때만_해지한다() {
        String a = signup("wcs-restore-a@band.app", "결제자A");
        String b = signup("wcs-restore-b@band.app", "복원자B");
        long aId = myUserId(a);
        String token = "wcs-g-" + System.nanoTime();
        long bandId = createBand(a, "복원밴드");
        String purchase = token + "@band-" + bandId;           // no-op 게이트웨이: 끝의 @band-N 이 구매에 적힌 밴드
        long[] band = {bandId};
        join(b, issueInvite(a, bandId, null));
        assertThat(verifyGoogle(a, bandId, purchase).getStatusCode().value()).isEqualTo(200);
        assertThat(post("/api/v1/bands/" + bandId + "/leader", "{\"newLeaderUserId\":" + myUserId(b) + "}", a)
                .getStatusCode().value()).isEqualTo(200);

        assertThat(post("/api/v1/plan/google/restore", "{\"purchaseToken\":\"" + purchase + "\"}", b)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(planRow(band[0]).get("purchased_by_user_id")).isEqualTo(aId);   // 덮어쓰지 않는다

        withdraw(b);   // 비결제자(지금 밴드장) 탈퇴 — 밴드장은 A 에게 자동 위임
        assertThat(gateway.cancelledRenewals()).doesNotContain(purchase);

        withdraw(a);   // 결제자 탈퇴
        assertThat(gateway.cancelledRenewals()).contains(purchase);
    }

    /** QA BILL-32 — 결제자 기록이 비어 있으면(옛 구매) 처음 복원한 밴드장이 결제자로 적힌다. */
    @Test
    void 결제자_기록이_비어_있으면_복원한_밴드장이_결제자가_된다() {
        String leader = signup("wcs-restore-c@band.app", "밴드장");
        long leaderId = myUserId(leader);
        long bandId = createBand(leader, "빈기록밴드");
        String purchase = "wcs-h-" + System.nanoTime() + "@band-" + bandId;
        assertThat(verifyGoogle(leader, bandId, purchase).getStatusCode().value()).isEqualTo(200);
        jdbc.update("update band_plans set purchased_by_user_id = null where band_id = ?", bandId);

        assertThat(post("/api/v1/plan/google/restore", "{\"purchaseToken\":\"" + purchase + "\"}", leader)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(planRow(bandId).get("purchased_by_user_id")).isEqualTo(leaderId);
    }

    /** QA BILL-34 — 스토어 구독 없이 쿠폰만 쓰는 밴드의 밴드장이 탈퇴해도 Play 해지 호출이 없다. */
    @Test
    void 쿠폰만_쓰는_밴드의_밴드장이_탈퇴해도_해지를_부르지_않는다() {
        String leader = signup("wcs-coupon-only@band.app", "밴드장");
        String member = signup("wcs-coupon-member@band.app", "멤버");
        long bandId = createBand(leader, "쿠폰만밴드");
        join(member, issueInvite(leader, bandId, null));
        jdbc.update("insert into plan_coupons (code, grant_days, max_uses, expires_at, created_at) "
                + "values ('WCSONLY1', 30, null, null, now())");
        assertThat(redeemCoupon(leader, bandId, "WCSONLY1").getStatusCode().value()).isEqualTo(200);
        int before = gateway.cancelledRenewals().size();

        withdraw(leader);

        assertThat(gateway.cancelledRenewals()).hasSize(before);
        assertThat(planRow(bandId).get("tier")).isEqualTo("PREMIUM");
    }

    private int purchaserLeftNotices(long leaderId, long bandId) {
        return jdbc.queryForObject("select count(*) from notification_dispatches "
                + "where type = 'PLAN_PURCHASER_LEFT' and user_id = ? and target_id = ?", Integer.class, leaderId, bandId);
    }

    /** 결제자가 밴드장을 넘긴 밴드 — 결제자는 이제 일반 멤버다. 돌려주는 값: [bandId, 새 밴드장 id]. */
    private long[] bandPaidByMemberWhoHandedOver(String payer, String next, String purchase) {
        long nextId = myUserId(next);
        long bandId = createBand(payer, "넘긴밴드");
        join(next, issueInvite(payer, bandId, null));
        assertThat(verifyGoogle(payer, bandId, purchase).getStatusCode().value()).isEqualTo(200);
        assertThat(post("/api/v1/bands/" + bandId + "/leader", "{\"newLeaderUserId\":" + nextId + "}", payer)
                .getStatusCode().value()).isEqualTo(200);
        return new long[]{bandId, nextId};
    }

    @Test
    void 결제자가_밴드를_나가면_자동_갱신을_해지하고_밴드장에게_알린다() {
        String payer = signup("wcs-leave-payer@band.app", "결제자");
        String next = signup("wcs-leave-next@band.app", "밴드장");
        String purchase = "wcs-f-" + System.nanoTime();
        long[] band = bandPaidByMemberWhoHandedOver(payer, next, purchase);

        assertThat(post("/api/v1/bands/" + band[0] + "/members/leave", null, payer).getStatusCode().is2xxSuccessful())
                .isTrue();

        assertThat(gateway.cancelledRenewals()).contains(purchase);
        Map<String, Object> row = planRow(band[0]);
        assertThat(row.get("tier")).isEqualTo("PREMIUM");             // 결제한 기간은 그대로
        assertThat(row.get("purchased_by_user_id")).isNull();
        assertThat(purchaserLeftNotices(band[1], band[0])).isEqualTo(1);
    }

    @Test
    void 결제자가_추방되면_자동_갱신을_해지한다() {
        String payer = signup("wcs-kick-payer@band.app", "결제자");
        String next = signup("wcs-kick-next@band.app", "밴드장");
        long payerId = myUserId(payer);
        String purchase = "wcs-g-" + System.nanoTime();
        long[] band = bandPaidByMemberWhoHandedOver(payer, next, purchase);

        assertThat(delete("/api/v1/bands/" + band[0] + "/members/" + payerId, next).getStatusCode().is2xxSuccessful())
                .isTrue();

        assertThat(gateway.cancelledRenewals()).contains(purchase);
        assertThat(planRow(band[0]).get("purchased_by_user_id")).isNull();
        assertThat(purchaserLeftNotices(band[1], band[0])).isEqualTo(1);
    }

    @Test
    void 결제자가_아닌_멤버가_나가면_해지하지_않는다() {
        String payer = signup("wcs-stay-payer@band.app", "결제자");
        String member = signup("wcs-stay-member@band.app", "멤버");
        long bandId = createBand(payer, "남는밴드");
        join(member, issueInvite(payer, bandId, null));
        String purchase = "wcs-h-" + System.nanoTime();
        assertThat(verifyGoogle(payer, bandId, purchase).getStatusCode().value()).isEqualTo(200);

        assertThat(post("/api/v1/bands/" + bandId + "/members/leave", null, member).getStatusCode().is2xxSuccessful())
                .isTrue();
        withdrawnPurchaserSubscriptions.retryPending();   // 결제자는 아직 멤버 — 재시도 대상도 아니다

        assertThat(gateway.cancelledRenewals()).doesNotContain(purchase);
        assertThat(planRow(bandId).get("purchased_by_user_id")).isEqualTo(myUserId(payer));
    }

    @Test
    void 밴드를_나간_결제자의_구독도_재시도_배치가_해지한다() {
        // 나갈 때 해지가 안 됐으면(Play 일시 장애·결제자 기록이 늦게 들어옴) 연결이 남고, 매시 배치가 다시 해지한다.
        String payer = signup("wcs-retry-payer@band.app", "결제자");
        String next = signup("wcs-retry-next@band.app", "밴드장");
        long payerId = myUserId(payer);
        String purchase = "wcs-i-" + System.nanoTime();
        long[] band = bandPaidByMemberWhoHandedOver(payer, next, purchase);
        jdbc.update("update band_plans set purchased_by_user_id = null where band_id = ?", band[0]);
        assertThat(post("/api/v1/bands/" + band[0] + "/members/leave", null, payer).getStatusCode().is2xxSuccessful())
                .isTrue();
        assertThat(gateway.cancelledRenewals()).doesNotContain(purchase);
        jdbc.update("update band_plans set purchased_by_user_id = ? where band_id = ?", payerId, band[0]);

        withdrawnPurchaserSubscriptions.retryPending();

        assertThat(gateway.cancelledRenewals()).contains(purchase);
        assertThat(planRow(band[0]).get("purchased_by_user_id")).isNull();
        assertThat(purchaserLeftNotices(band[1], band[0])).isEqualTo(1);
    }
}
