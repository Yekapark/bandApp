package com.yeka.bandapp.band;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /api/v1/bands} — 내가 속한 밴드 목록 (BACKLOG §1.9).
 * 클라이언트 밴드 스위처의 데이터 출처다.
 */
class MyBandListIntegrationTest extends BandApiSupport {

    @Test
    void lists_only_bands_i_am_an_active_member_of_with_my_role() {
        String me = signup("mybands-me@band.app", "나");
        String other = signup("mybands-other@band.app", "남");

        long ledByMe = createBand(me, "내가리더");
        long joinedByMe = createBand(other, "남의밴드");
        join(me, issueInvite(other, joinedByMe, null));
        createBand(other, "내가안낀밴드");

        JsonNode data = data(get("/api/v1/bands", me));
        assertThat(data.get("bandCount").asInt()).isEqualTo(2);

        JsonNode first = data.get("bands").get(0);
        assertThat(first.get("id").asLong()).isEqualTo(ledByMe);
        assertThat(first.get("myRole").asText()).isEqualTo("LEADER");
        assertThat(first.get("name").asText()).isEqualTo("내가리더");

        JsonNode second = data.get("bands").get(1);
        assertThat(second.get("id").asLong()).isEqualTo(joinedByMe);
        assertThat(second.get("myRole").asText()).isEqualTo("MEMBER");
        assertThat(second.get("memberCount").asInt()).isEqualTo(2);
    }

    @Test
    void band_i_left_drops_out_of_the_list() {
        String me = signup("mybands-leaver@band.app", "탈퇴자");
        String leader = signup("mybands-leader@band.app", "리더");
        long bandId = createBand(leader, "떠날밴드");
        join(me, issueInvite(leader, bandId, null));

        assertThat(data(get("/api/v1/bands", me)).get("bandCount").asInt()).isEqualTo(1);

        assertThat(post("/api/v1/bands/" + bandId + "/members/leave", null, me)
                .getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> after = get("/api/v1/bands", me);
        assertThat(data(after).get("bandCount").asInt()).isZero();
    }
}
