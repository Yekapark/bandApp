package com.yeka.bandapp.band;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.band.entity.BandInvite;
import com.yeka.bandapp.band.entity.BandMemberRole;
import com.yeka.bandapp.band.repository.BandInviteRepository;
import com.yeka.bandapp.band.repository.BandMemberRepository;
import com.yeka.bandapp.band.repository.BandRepository;
import com.yeka.bandapp.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BACKLOG §1.9 — 계정 탈퇴 시 밴드 멤버십 정리.
 *
 * <p>채택한 동작: 탈퇴 시 소속 전 밴드에서 자동으로 나간다. 탈퇴자가 밴드장이면 가장 먼저 가입한
 * 다른 활성 멤버가 밴드장으로 승격되고, 다른 멤버가 없으면 그 밴드는 활성 멤버 0인 채 닫혔다가 정리 배치가 지운다
 * (LAUNCH_REVIEW L7 — {@code BandDeletionIntegrationTest}).
 */
class AccountWithdrawalBandCleanupIntegrationTest extends BandApiSupport {

    @Autowired
    BandMemberRepository bandMemberRepository;
    @Autowired BandInviteRepository invites;
    @Autowired BandRepository bands;
    @Autowired UserRepository users;

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
        String outsider = signup("wd-outsider@band.app", "외부인");
        long bandId = createBand(leader, "새소년");
        String code = issueInvite(leader, bandId, null);

        withdraw(leader);

        assertThat(bandMemberRepository.countByBandIdAndLeftAtIsNull(bandId)).isZero();
        assertThat(invites.findByCode(code).orElseThrow().isRevoked()).isTrue();
        ResponseEntity<String> rejected = join(outsider, code);
        assertThat(rejected.getStatusCode().value()).isEqualTo(410);
        assertThat(errorCode(rejected)).isEqualTo("INVITE_REVOKED");
        assertThat(get("/api/v1/bands/" + bandId, outsider).getStatusCode().value()).isEqualTo(403);
        assertThat(bandMemberRepository.countByBandIdAndLeftAtIsNull(bandId)).isZero();
    }

    @Test
    void legacy_active_invite_cannot_reopen_a_band_without_a_leader() {
        String leader = signup("wd-legacy@band.app", "혼자");
        String outsider = signup("wd-outsider@band.app", "외부인");
        long leaderId = myUserId(leader);
        long bandId = createBand(leader, "기존 빈 밴드");
        withdraw(leader);
        // 수정 전에 남아 있을 수 있는 유효 코드를 재현한다. 운영 데이터를 변경하는 마이그레이션은 없다.
        invites.save(BandInvite.issue(bandId, "LEGACY01", leaderId, Instant.now(), Duration.ofDays(7), null));

        ResponseEntity<String> rejected = join(outsider, "LEGACY01");

        assertThat(rejected.getStatusCode().value()).isEqualTo(410);
        assertThat(errorCode(rejected)).isEqualTo("INVITE_REVOKED");
        assertThat(bandMemberRepository.countByBandIdAndLeftAtIsNull(bandId)).isZero();
        assertThat(invites.findByCode("LEGACY01").orElseThrow().getUsedCount()).isZero();
    }

    @Test
    void revoking_empty_bands_does_not_discard_other_withdrawal_changes() {
        String leader = signup("wd-many@band.app", "여러밴드");
        String mate = signup("wd-mate@band.app", "후임");
        long leaderId = myUserId(leader);
        long mateId = myUserId(mate);
        long emptyFirst = createBand(leader, "먼저 비는 밴드");
        long shared = createBand(leader, "승계 밴드");
        long emptyLast = createBand(leader, "나중에 비는 밴드");
        String firstCode = issueInvite(leader, emptyFirst, null);
        String lastCode = issueInvite(leader, emptyLast, null);
        String sharedCode = issueInvite(leader, shared, null);
        assertThat(join(mate, sharedCode).getStatusCode().value()).isEqualTo(200);

        withdraw(leader);

        assertThat(users.findByIdAndDeletedAtIsNull(leaderId)).isEmpty();
        assertThat(bandMemberRepository.findActiveBandIdsForWithdrawal(leaderId)).isEmpty();
        assertThat(invites.findByCode(firstCode).orElseThrow().isRevoked()).isTrue();
        assertThat(invites.findByCode(lastCode).orElseThrow().isRevoked()).isTrue();
        assertThat(invites.findByCode(sharedCode).orElseThrow().isRevoked()).isFalse();
        assertThat(bands.findById(shared).orElseThrow().getLeaderId()).isEqualTo(mateId);
        assertThat(bandMemberRepository.findByBandIdAndUserIdAndLeftAtIsNull(shared, mateId)
                .orElseThrow().isLeader()).isTrue();
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
