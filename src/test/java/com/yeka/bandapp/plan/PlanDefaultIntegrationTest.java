package com.yeka.bandapp.plan;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 밴드 생성 시 기본 FREE 요금제 행이 붙는지 확인한다({@code BandService.create} → {@code PlanProvisioningService}).
 */
class PlanDefaultIntegrationTest extends PlanApiSupport {

    @Test
    void new_band_starts_on_free_plan_with_thirty_day_retention() {
        String leader = signup("default-l@band.app", "리더");
        long bandId = createBand(leader, "기본밴드");

        ResponseEntity<String> res = viewPlan(leader, bandId);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(data(res).get("tier").asText()).isEqualTo("FREE");
        assertThat(data(res).get("mediaRetentionDays").asInt()).isEqualTo(30);
        assertThat(data(res).get("expiresAt").isNull()).isTrue();
        assertThat(data(res).get("startedAt").asText()).isNotBlank();
    }
}
