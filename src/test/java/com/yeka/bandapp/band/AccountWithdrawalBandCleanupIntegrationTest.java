package com.yeka.bandapp.band;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.band.entity.BandMemberRole;
import com.yeka.bandapp.band.repository.BandMemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BACKLOG §1.9 — 계정 탈퇴 시 밴드 멤버십 정리.
 *
 * <p>채택한 동작: 탈퇴 시 소속 전 밴드에서 자동으로 나간다. 탈퇴자가 밴드장이면 가장 먼저 가입한
 * 다른 활성 멤버가 밴드장으로 승격되고, 다른 멤버가 없으면 그 밴드는 활성 멤버 0인 상태로 남는다.
 */
class AccountWithdrawalBandCleanupIntegrationTest extends BandApiSupport {

    @Autowired
    BandMemberRepository bandMemberRepository;

    @Test
    void member_withdrawal_removes_them_from_the_band() {
        String leader = signup("wd-mem-l@band.app", "리더");
        String member = signup("wd-mem-m@band.app", "멤버");
        long bandId = createBand(leader, "혁오");
        join(member, issueInvite(leader, bandId, null));

        withdraw(member);

        JsonNode members = data(get("/api/v1/bands/" + bandId + "/members", leader));
        assertThat(members.get("memberCount").asInt()).isEqualTo(1);
        assertThat(members.get("members").get(0).get("role").asText()).isEqualTo("LEADER");
        assertThat(bandMemberRepository.countByBandIdAndLeftAtIsNull(bandId)).isEqualTo(1);
    }

    @Test
    void leader_withdrawal_auto_delegates_to_earliest_joined_member() {
        String leader = signup("wd-lead-l@band.app", "리더");
        String m1 = signup("wd-lead-m1@band.app", "먼저");
        String m2 = signup("wd-lead-m2@band.app", "나중");
        long bandId = createBand(leader, "국카스텐");
        String code = issueInvite(leader, bandId, null);
        join(m1, code);
        join(m2, code);
        long m1Id = myUserId(m1);
        long m2Id = myUserId(m2);

        withdraw(leader);

        // 활성 LEADER 정확히 한 명, 그리고 그건 최고참 m1.
        assertThat(bandMemberRepository.countByBandIdAndRoleAndLeftAtIsNull(bandId, BandMemberRole.LEADER))
                .isEqualTo(1);
        assertThat(bandMemberRepository.findByBandIdAndUserIdAndLeftAtIsNull(bandId, m1Id).orElseThrow().isLeader())
                .isTrue();
        assertThat(bandMemberRepository.findByBandIdAndUserIdAndLeftAtIsNull(bandId, m2Id).orElseThrow().isLeader())
                .isFalse();
        assertThat(bandMemberRepository.countByBandIdAndLeftAtIsNull(bandId)).isEqualTo(2);

        // 승격된 m1 이 실제로 밴드장 권한을 쓸 수 있다.
        assertThat(post("/api/v1/bands/" + bandId + "/invites", null, m1).getStatusCode().value())
                .isEqualTo(201);
        // 강등도 위임도 안 된 m2 는 여전히 밴드장이 아니다.
        assertThat(post("/api/v1/bands/" + bandId + "/invites", null, m2).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    void sole_leader_withdrawal_leaves_the_band_memberless() {
        String leader = signup("wd-solo@band.app", "혼자");
        long bandId = createBand(leader, "새소년");

        withdraw(leader);

        assertThat(bandMemberRepository.countByBandIdAndLeftAtIsNull(bandId)).isZero();
    }

    @Test
    void user_in_multiple_bands_is_detached_from_all() {
        String host = signup("wd-multi-host@band.app", "호스트");
        String u = signup("wd-multi-u@band.app", "여러밴드");
        String uMate = signup("wd-multi-mate@band.app", "u의밴드메이트");

        // A: host 가 밴드장, u 는 멤버
        long bandA = createBand(host, "밴드A");
        join(u, issueInvite(host, bandA, null));
        // B: u 가 밴드장, uMate 가 멤버
        long bandB = createBand(u, "밴드B");
        join(uMate, issueInvite(u, bandB, null));
        long hostId = myUserId(host);
        long uMateId = myUserId(uMate);

        withdraw(u);

        // A: u 만 빠지고 host 는 그대로 밴드장.
        assertThat(bandMemberRepository.countByBandIdAndLeftAtIsNull(bandA)).isEqualTo(1);
        assertThat(bandMemberRepository.findByBandIdAndUserIdAndLeftAtIsNull(bandA, hostId).orElseThrow().isLeader())
                .isTrue();
        // B: uMate 가 밴드장으로 승격, 활성 멤버 1명.
        assertThat(bandMemberRepository.countByBandIdAndLeftAtIsNull(bandB)).isEqualTo(1);
        assertThat(bandMemberRepository.findByBandIdAndUserIdAndLeftAtIsNull(bandB, uMateId).orElseThrow().isLeader())
                .isTrue();
    }

    @Test
    void withdrawal_of_user_with_no_bands_still_succeeds() {
        String loner = signup("wd-noband@band.app", "밴드없음");

        withdraw(loner); // 204 아니면 헬퍼가 예외

        // 재가입 가능(계정이 온전히 탈퇴됨).
        assertThat(post("/api/v1/auth/signup",
                "{\"email\":\"wd-noband@band.app\",\"password\":\"pw12345678\",\"name\":\"다시\"}")
                .getStatusCode().value()).isEqualTo(201);
    }
}
