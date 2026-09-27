package com.yeka.bandapp.plan;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 구매를 밴드에 묶기(LAUNCH_REVIEW B2·B3). 앱은 구매할 때 결제한 밴드를 {@code band-{id}} 로 적고,
 * 서버는 검증·복구 때 "지금 선택된 밴드" 가 아니라 그 값을 따른다.
 *
 * <p>no-op 게이트웨이는 토큰 끝의 {@code @band-7} 을 구매에 적힌 밴드 표시로 돌려준다.
 */
class PlanPurchaseRestoreIntegrationTest extends PlanApiSupport {

    private static String tagged(String token, long bandId) {
        return token + "@band-" + bandId;
    }

    private ResponseEntity<String> restoreGoogle(String bearer, String purchaseToken) {
        return post("/api/v1/plan/google/restore", "{\"purchaseToken\":\"" + purchaseToken + "\"}", bearer);
    }

    @Test
    void restore_applies_to_the_band_written_on_the_purchase_not_another() {
        // 밴드장이 밴드 두 개를 가졌다. A 를 결제하고 검증 전에 앱이 꺼졌다 → 다음 실행 때 복구.
        String leader = signup("restore-a@band.app", "리더");
        long bandA = createBand(leader, "복구A");
        long bandB = createBand(leader, "복구B");

        ResponseEntity<String> res = restoreGoogle(leader, tagged("rtok1", bandA));

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(data(res).get("bandId").asLong()).isEqualTo(bandA);
        assertThat(data(res).get("plan").get("tier").asText()).isEqualTo("PREMIUM");
        assertThat(data(viewPlan(leader, bandA)).get("tier").asText()).isEqualTo("PREMIUM");
        assertThat(data(viewPlan(leader, bandB)).get("tier").asText()).isEqualTo("FREE");
    }

    @Test
    void restore_twice_is_safe() {
        String leader = signup("restore-twice@band.app", "리더");
        long band = createBand(leader, "두번");

        assertThat(restoreGoogle(leader, tagged("rtok2", band)).getStatusCode().value()).isEqualTo(200);
        assertThat(restoreGoogle(leader, tagged("rtok2", band)).getStatusCode().value()).isEqualTo(200);

        assertThat(data(viewPlan(leader, band)).get("tier").asText()).isEqualTo("PREMIUM");
    }

    @Test
    void restore_without_band_tag_is_unknown_and_changes_nothing() {
        String leader = signup("restore-untagged@band.app", "리더");
        long band = createBand(leader, "표시없음");

        ResponseEntity<String> res = restoreGoogle(leader, "rtok3");

        assertThat(res.getStatusCode().value()).isEqualTo(422);
        assertThat(errorCode(res)).isEqualTo("PURCHASE_BAND_UNKNOWN");
        assertThat(data(viewPlan(leader, band)).get("tier").asText()).isEqualTo("FREE");
    }

    @Test
    void restore_by_someone_who_is_not_that_bands_leader_is_forbidden() {
        String leader = signup("restore-owner@band.app", "리더");
        long band = createBand(leader, "남의밴드");
        String stranger = signup("restore-stranger@band.app", "남");

        ResponseEntity<String> res = restoreGoogle(stranger, tagged("rtok4", band));

        assertThat(res.getStatusCode().value()).isEqualTo(403);
        assertThat(data(viewPlan(leader, band)).get("tier").asText()).isEqualTo("FREE");
    }

    @Test
    void verify_rejects_a_purchase_written_for_another_band() {
        String leader = signup("verify-mismatch@band.app", "리더");
        long bandA = createBand(leader, "결제한밴드");
        long bandB = createBand(leader, "선택된밴드");

        ResponseEntity<String> res = verifyGoogle(leader, bandB, tagged("vtok1", bandA));

        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("PURCHASE_BAND_MISMATCH");
        assertThat(data(viewPlan(leader, bandB)).get("tier").asText()).isEqualTo("FREE");
        assertThat(data(viewPlan(leader, bandA)).get("tier").asText()).isEqualTo("FREE");
    }

    @Test
    void verify_accepts_a_purchase_written_for_the_same_band() {
        String leader = signup("verify-match@band.app", "리더");
        long band = createBand(leader, "같은밴드");

        ResponseEntity<String> res = verifyGoogle(leader, band, tagged("vtok2", band));

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(data(viewPlan(leader, band)).get("tier").asText()).isEqualTo("PREMIUM");
    }
}
