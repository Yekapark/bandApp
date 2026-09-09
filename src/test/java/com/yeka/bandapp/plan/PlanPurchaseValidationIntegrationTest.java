package com.yeka.bandapp.plan;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스토어가 "정상(ACTIVE)" 이라고 답해도 내용이 우리 조건에 안 맞으면 PREMIUM 을 주지 않는다.
 * 상태만 보고 통과시키던 시절에는 아래 두 경우가 그대로 PREMIUM 이 됐다.
 */
@Import(QuirkyStoreBillingGatewayConfig.class)
class PlanPurchaseValidationIntegrationTest extends PlanApiSupport {

    /**
     * {@code purchases.subscriptionsv2.get} 은 패키지 단위라 이 앱의 어떤 구독 토큰이든 조회된다.
     * 상품 id 를 안 보면, 나중에 더 싼 상품을 하나 추가하는 순간 그 토큰으로 PREMIUM 을 받을 수 있다.
     */
    @Test
    void a_purchase_of_another_product_does_not_grant_premium() {
        String leader = signup("pv-a@band.app", "리더");
        long bandId = createBand(leader, "다른상품밴드");

        ResponseEntity<String> res = verifyGoogle(leader, bandId, "wrongproduct-" + bandId);

        assertThat(res.getStatusCode().value()).isEqualTo(402);
        assertThat(errorCode(res)).isEqualTo("PURCHASE_NOT_VERIFIED");
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("FREE");
    }

    /**
     * 만료일이 없으면 {@code band_plans.expires_at} 이 NULL 로 저장되고, 만료 배치의
     * {@code expires_at < now} 에 <b>영원히 걸리지 않는다</b> = 공짜 무기한 PREMIUM.
     */
    @Test
    void a_purchase_without_an_expiry_does_not_grant_premium() {
        String leader = signup("pv-b@band.app", "리더");
        long bandId = createBand(leader, "만료없는밴드");

        ResponseEntity<String> res = verifyGoogle(leader, bandId, "noexpiry-" + bandId);

        assertThat(res.getStatusCode().value()).isEqualTo(402);
        assertThat(errorCode(res)).isEqualTo("PURCHASE_NOT_VERIFIED");
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("FREE");
    }

    /** 대조군 — 같은 게이트웨이가 정상 구매에는 그대로 PREMIUM 을 준다(위 둘이 과잉 차단이 아님). */
    @Test
    void a_normal_purchase_still_grants_premium() {
        String leader = signup("pv-c@band.app", "리더");
        long bandId = createBand(leader, "정상밴드");

        ResponseEntity<String> res = verifyGoogle(leader, bandId, "ok-" + bandId);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(data(res).get("tier").asText()).isEqualTo("PREMIUM");
    }
}
