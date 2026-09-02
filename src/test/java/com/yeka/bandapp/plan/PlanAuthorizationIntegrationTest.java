package com.yeka.bandapp.plan;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 요금제 API 인가: 조회는 밴드 멤버, 전환은 밴드장만.
 */
class PlanAuthorizationIntegrationTest extends PlanApiSupport {

    @Test
    void non_leader_member_cannot_subscribe_or_cancel() {
        String leader = signup("authz-l@band.app", "리더");
        long bandId = createBand(leader, "인가밴드");
        String member = signup("authz-m@band.app", "멤버");
        join(member, issueInvite(leader, bandId, null));

        ResponseEntity<String> sub = subscribe(member, bandId);
        assertThat(sub.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(sub)).isEqualTo("NOT_BAND_LEADER");

        assertThat(cancel(member, bandId).getStatusCode().value()).isEqualTo(403);
        assertThat(renew(member, bandId).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void member_can_view_plan() {
        String leader = signup("authz-v-l@band.app", "리더");
        long bandId = createBand(leader, "조회밴드");
        String member = signup("authz-v-m@band.app", "멤버");
        join(member, issueInvite(leader, bandId, null));

        ResponseEntity<String> res = viewPlan(member, bandId);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(data(res).get("tier").asText()).isEqualTo("FREE");
    }

    @Test
    void non_member_is_forbidden() {
        String leader = signup("authz-x-l@band.app", "리더");
        long bandId = createBand(leader, "외부밴드");
        String outsider = signup("authz-x-o@band.app", "외부인");

        assertThat(errorCode(viewPlan(outsider, bandId))).isEqualTo("NOT_BAND_MEMBER");
        assertThat(errorCode(subscribe(outsider, bandId))).isEqualTo("NOT_BAND_MEMBER");
    }

    @Test
    void anonymous_request_is_unauthorized() {
        String leader = signup("authz-anon-l@band.app", "리더");
        long bandId = createBand(leader, "익명밴드");

        assertThat(get(planPath(bandId)).getStatusCode().value()).isEqualTo(401);
    }
}
