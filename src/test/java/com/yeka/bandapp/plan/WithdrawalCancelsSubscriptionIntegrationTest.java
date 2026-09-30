package com.yeka.bandapp.plan;

import com.yeka.bandapp.plan.gateway.NoOpStoreBillingGateway;
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
        assertThat(subscribe(payer, bandId).getStatusCode().value()).isEqualTo(200);
        assertThat(planRow(bandId).get("purchased_by_user_id")).isEqualTo(payerId);

        withdraw(payer);

        assertThat(gateway.cancelledRenewals()).contains(tokenFor(bandId));
        Map<String, Object> row = planRow(bandId);
        assertThat(row.get("tier")).isEqualTo("PREMIUM");            // 결제한 기간은 그대로
        assertThat(row.get("purchase_token")).isEqualTo(tokenFor(bandId)); // 웹훅이 밴드를 찾을 수 있게 남긴다
        assertThat(row.get("purchased_by_user_id")).isNull();       // 탈퇴자와의 연결은 끊는다
    }

    @Test
    void 결제자가_아닌_밴드장이_탈퇴하면_해지하지_않는다() {
        String payer = signup("wcs-payer2@band.app", "결제자");
        String next = signup("wcs-next@band.app", "다음밴드장");
        long nextId = myUserId(next);
        long bandId = createBand(payer, "위임밴드");
        join(next, issueInvite(payer, bandId, null));
        assertThat(subscribe(payer, bandId).getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> delegated = post("/api/v1/bands/" + bandId + "/leader",
                "{\"newLeaderUserId\":" + nextId + "}", payer);
        assertThat(delegated.getStatusCode().value()).isEqualTo(200);

        withdraw(next);   // 지금 밴드장이지만 결제자는 아니다

        assertThat(gateway.cancelledRenewals()).doesNotContain(tokenFor(bandId));
        assertThat(planRow(bandId).get("purchased_by_user_id")).isNotNull();

        withdraw(payer);  // 결제자가 탈퇴하면 그때 해지

        assertThat(gateway.cancelledRenewals()).contains(tokenFor(bandId));
    }

    @Test
    void 이미_해지_예약한_구독은_다시_해지하지_않는다() {
        String payer = signup("wcs-payer3@band.app", "결제자");
        long bandId = createBand(payer, "해지예약밴드");
        assertThat(subscribe(payer, bandId).getStatusCode().value()).isEqualTo(200);
        cancel(payer, bandId);   // Play 스토어에서 해지 → CANCELED 웹훅

        withdraw(payer);

        assertThat(gateway.cancelledRenewals()).doesNotContain(tokenFor(bandId));
        assertThat(planRow(bandId).get("purchased_by_user_id")).isNull();
    }

    @Test
    void 해지_호출이_실패해도_탈퇴는_끝난다() {
        String payer = signup("wcs-payer4@band.app", "결제자");
        long bandId = createBand(payer, "실패밴드");
        String purchaseToken = "nocancel-" + bandId;
        assertThat(verifyGoogle(payer, bandId, purchaseToken).getStatusCode().value()).isEqualTo(200);

        withdraw(payer);   // 204 가 아니면 withdraw() 가 예외를 던진다

        assertThat(gateway.cancelledRenewals()).doesNotContain(purchaseToken);
        assertThat(get("/api/v1/users/me", payer).getStatusCode().value()).isEqualTo(401);
    }
}
