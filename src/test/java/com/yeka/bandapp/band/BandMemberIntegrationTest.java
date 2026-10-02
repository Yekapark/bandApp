package com.yeka.bandapp.band;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.band.repository.BandMemberRepository;
import com.yeka.bandapp.band.entity.BandMemberRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 완료 기준 중: 권한 없는 사용자의 밴드 설정 변경 403, 위임 후 LEADER 정확히 한 명.
 * 여기에 목록 조회 · 자발적 탈퇴 · 추방 · 타 밴드 접근 차단을 함께 검증한다.
 */
class BandMemberIntegrationTest extends BandApiSupport {

    @Autowired
    BandMemberRepository bandMemberRepository;

    @Test
    void non_leader_cannot_change_band_settings() {
        String leader = signup("leader1@band.app", "리더");
        String member = signup("member1@band.app", "멤버");
        long bandId = createBand(leader, "장미여관");
        join(member, issueInvite(leader, bandId, null));

        ResponseEntity<String> res = put("/api/v1/bands/" + bandId + "/settings",
                "{\"reservationPermission\":\"ANYONE\"}", member);

        assertThat(res.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("NOT_BAND_LEADER");
    }

    @Test
    void leader_changes_band_settings() {
        String leader = signup("leader2@band.app", "리더");
        long bandId = createBand(leader, "국카스텐");

        ResponseEntity<String> res = put("/api/v1/bands/" + bandId + "/settings",
                "{\"reservationPermission\":\"APPROVAL_REQUIRED\"}", leader);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(data(res).get("reservationPermission").asText()).isEqualTo("APPROVAL_REQUIRED");
    }

    @Test
    void invalid_permission_value_is_400() {
        String leader = signup("leader3@band.app", "리더");
        long bandId = createBand(leader, "혁오");

        ResponseEntity<String> res = put("/api/v1/bands/" + bandId + "/settings",
                "{\"reservationPermission\":\"WHENEVER\"}", leader);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void delegation_leaves_exactly_one_leader_and_swaps_roles() {
        String leader = signup("leader4@band.app", "원리더");
        String member = signup("member4@band.app", "새리더");
        long bandId = createBand(leader, "새소년");
        long memberId = myUserId(member);
        long oldLeaderId = myUserId(leader);
        join(member, issueInvite(leader, bandId, null));

        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/leader",
                "{\"newLeaderUserId\":" + memberId + "}", leader);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(data(res).get("leaderId").asLong()).isEqualTo(memberId);

        // DB 불변식: 활성 LEADER 정확히 한 명.
        assertThat(bandMemberRepository.countByBandIdAndRoleAndLeftAtIsNull(bandId, BandMemberRole.LEADER))
                .isEqualTo(1);

        // 역할이 실제로 뒤바뀌었다.
        assertThat(bandMemberRepository.findByBandIdAndUserIdAndLeftAtIsNull(bandId, memberId).orElseThrow().isLeader())
                .isTrue();
        assertThat(bandMemberRepository.findByBandIdAndUserIdAndLeftAtIsNull(bandId, oldLeaderId).orElseThrow().isLeader())
                .isFalse();

        // 새 밴드장은 설정을 바꿀 수 있고, 강등된 예전 밴드장은 403.
        assertThat(put("/api/v1/bands/" + bandId + "/settings",
                "{\"reservationPermission\":\"ANYONE\"}", member).getStatusCode().value()).isEqualTo(200);
        assertThat(put("/api/v1/bands/" + bandId + "/settings",
                "{\"reservationPermission\":\"LEADER_ONLY\"}", leader).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void leader_must_delegate_before_leaving() {
        String leader = signup("leader5@band.app", "리더");
        long bandId = createBand(leader, "잔나비");

        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/members/leave", null, leader);

        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("LEADER_MUST_DELEGATE_BEFORE_LEAVING");
    }

    @Test
    void member_can_leave_and_then_no_longer_sees_the_band() {
        String leader = signup("leader6@band.app", "리더");
        String member = signup("member6@band.app", "멤버");
        long bandId = createBand(leader, "브로콜리너마저");
        join(member, issueInvite(leader, bandId, null));

        assertThat(post("/api/v1/bands/" + bandId + "/members/leave", null, member)
                .getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> afterLeave = get("/api/v1/bands/" + bandId, member);
        assertThat(afterLeave.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(afterLeave)).isEqualTo("NOT_BAND_MEMBER");
    }

    @Test
    void leader_kicks_member() {
        String leader = signup("leader7@band.app", "리더");
        String member = signup("member7@band.app", "쫓겨날사람");
        long bandId = createBand(leader, "실리카겔");
        long memberId = myUserId(member);
        join(member, issueInvite(leader, bandId, null));

        assertThat(delete("/api/v1/bands/" + bandId + "/members/" + memberId, leader)
                .getStatusCode().value()).isEqualTo(204);

        JsonNode list = data(get("/api/v1/bands/" + bandId + "/members", leader));
        assertThat(list.get("memberCount").asInt()).isEqualTo(1);
        assertThat(get("/api/v1/bands/" + bandId, member).getStatusCode().value()).isEqualTo(403);
    }

    /** 결정 #7 — 추방된 사람이 이미 가진 초대코드로 바로 다시 들어오지 못한다. */
    @Test
    void kicking_revokes_the_current_invite_code() {
        String leader = signup("leader7r@band.app", "리더");
        String member = signup("member7r@band.app", "쫓겨날사람");
        long bandId = createBand(leader, "추방코드밴드");
        long memberId = myUserId(member);
        String code = issueInvite(leader, bandId, null);
        join(member, code);

        assertThat(delete("/api/v1/bands/" + bandId + "/members/" + memberId, leader)
                .getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> rejoin = join(member, code);
        assertThat(rejoin.getStatusCode().value()).isEqualTo(410);
        assertThat(errorCode(rejoin)).isEqualTo("INVITE_REVOKED");
        assertThat(join(member, issueInvite(leader, bandId, null)).getStatusCode().is2xxSuccessful()).isTrue();
    }

    /** 결정 #11 — 보이지 않는 문자만으로 된 밴드 이름은 400. */
    @Test
    void band_name_of_only_invisible_characters_is_rejected() {
        String leader = signup("leader-blank@band.app", "리더");
        for (String name : new String[]{"\\u200B\\u200B", "\\u3164", "\\u2800 \\uFEFF\\u0007"}) {
            ResponseEntity<String> res = post("/api/v1/bands", "{\"name\":\"" + name + "\"}", leader);
            assertThat(res.getStatusCode().value()).as(name).isEqualTo(400);
            assertThat(body(res).at("/error/message").asText()).isEqualTo("밴드 이름을 입력해 주세요");
        }
        // 공백·줄바꿈만이면 그 전에 @NotBlank 가 400 으로 막는다
        assertThat(post("/api/v1/bands", "{\"name\":\"\\n\\t\"}", leader).getStatusCode().value()).isEqualTo(400);
        assertThat(createBand(leader, "\\u200B실리카겔")).isPositive();
    }

    @Test
    void member_cannot_kick() {
        String leader = signup("leader8@band.app", "리더");
        String a = signup("membera8@band.app", "에이");
        String b = signup("memberb8@band.app", "비");
        long bandId = createBand(leader, "데이먼스이어");
        String code = issueInvite(leader, bandId, null);
        join(a, code);
        join(b, code);

        ResponseEntity<String> res = delete("/api/v1/bands/" + bandId + "/members/" + myUserId(b), a);
        assertThat(res.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("NOT_BAND_LEADER");
    }

    @Test
    void non_member_cannot_read_band() {
        String leader = signup("leader9@band.app", "리더");
        String stranger = signup("stranger9@band.app", "낯선이");
        long bandId = createBand(leader, "아도이");

        ResponseEntity<String> res = get("/api/v1/bands/" + bandId, stranger);
        assertThat(res.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("NOT_BAND_MEMBER");
    }

    @Test
    void another_bands_leader_cannot_use_membership_or_admin_endpoints() {
        String owner = signup("owner@band.app", "밴드장");
        String outsider = signup("other-owner@band.app", "다른밴드장");
        long bandId = createBand(owner, "private band");
        createBand(outsider, "other band");
        long ownerId = myUserId(owner);
        String base = "/api/v1/bands/" + bandId;
        assertThat(get(base, outsider).getStatusCode().value()).isEqualTo(403);
        assertThat(get(base + "/members", outsider).getStatusCode().value()).isEqualTo(403);
        assertThat(get(base + "/invites/current", outsider).getStatusCode().value()).isEqualTo(403);
        assertThat(post(base + "/invites", null, outsider).getStatusCode().value()).isEqualTo(403);
        assertThat(delete(base + "/invites/current", outsider).getStatusCode().value()).isEqualTo(403);
        assertThat(put(base + "/settings", "{\"reservationPermission\":\"ANYONE\"}", outsider)
                .getStatusCode().value()).isEqualTo(403);
        assertThat(post(base + "/leader", "{\"newLeaderUserId\":" + ownerId + "}", outsider)
                .getStatusCode().value()).isEqualTo(403);
        assertThat(post(base + "/members/leave", null, outsider).getStatusCode().value()).isEqualTo(403);
        assertThat(delete(base + "/members/" + ownerId, outsider).getStatusCode().value()).isEqualTo(403);
        assertThat(post(base + "/delete", "{\"confirmName\":\"private band\"}", outsider)
                .getStatusCode().value()).isEqualTo(403);
        // 잠금 추가로 존재하지 않는 밴드의 응답이 404 등으로 달라지지 않는다.
        assertThat(post("/api/v1/bands/999999/invites", null, outsider).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void member_list_shows_roles_and_join_order() {
        String leader = signup("leader10@band.app", "리더");
        String member = signup("member10@band.app", "둘째");
        long bandId = createBand(leader, "쏜애플");
        join(member, issueInvite(leader, bandId, null));

        JsonNode list = data(get("/api/v1/bands/" + bandId + "/members", leader));
        assertThat(list.get("memberCount").asInt()).isEqualTo(2);
        assertThat(list.get("members").get(0).get("role").asText()).isEqualTo("LEADER");
        assertThat(list.get("members").get(0).get("name").asText()).isEqualTo("리더");
        assertThat(list.get("members").get(1).get("role").asText()).isEqualTo("MEMBER");
    }
}
