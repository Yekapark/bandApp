package com.yeka.bandapp.band;

import com.yeka.bandapp.band.entity.BandInvite;
import com.yeka.bandapp.band.repository.BandInviteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2 완료 기준 중: 만료 · 사용완료 · revoked 코드가 각각 다른 사유로 거부된다.
 * 정상 참여, 중복 참여, 초대코드 레이트리밋도 함께 검증한다.
 */
class BandInviteIntegrationTest extends BandApiSupport {

    @Autowired
    BandInviteRepository bandInviteRepository;

    @Test
    void happy_path_join_puts_user_in_the_band() {
        String leader = signup("inv-leader1@band.app", "리더");
        String joiner = signup("inv-joiner1@band.app", "참여자");
        long bandId = createBand(leader, "혁오");
        String code = issueInvite(leader, bandId, null);

        ResponseEntity<String> res = join(joiner, code);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(data(res).get("id").asLong()).isEqualTo(bandId);

        assertThat(data(get("/api/v1/bands/" + bandId + "/members", leader)).get("memberCount").asInt())
                .isEqualTo(2);
        assertThat(data(get("/api/v1/bands/" + bandId + "/invites/current", leader)).get("usedCount").asInt())
                .isEqualTo(1);
    }

    @Test
    void invite_link_points_at_the_landing_page() {
        String leader = signup("inv-leader2@band.app", "리더");
        long bandId = createBand(leader, "국카스텐");

        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/invites", null, leader);
        String code = data(res).get("code").asText();
        assertThat(data(res).get("link").asText()).isEqualTo("https://band.test/invite/" + code);
    }

    @Test
    void expired_code_is_rejected_as_expired() {
        String leader = signup("inv-leader3@band.app", "리더");
        String joiner = signup("inv-joiner3@band.app", "참여자");
        long bandId = createBand(leader, "새소년");
        long leaderId = myUserId(leader);
        // 이미 만료된 코드를 직접 심는다(API 로는 과거 만료를 만들 수 없다).
        bandInviteRepository.save(BandInvite.issue(bandId, "EXPIRED0", leaderId,
                Instant.now().minus(Duration.ofDays(10)), Duration.ofDays(7), null));

        ResponseEntity<String> res = join(joiner, "EXPIRED0");
        assertThat(res.getStatusCode().value()).isEqualTo(410);
        assertThat(errorCode(res)).isEqualTo("INVITE_EXPIRED");
    }

    @Test
    void exhausted_code_is_rejected_as_exhausted() {
        String leader = signup("inv-leader4@band.app", "리더");
        String first = signup("inv-first4@band.app", "먼저");
        String second = signup("inv-second4@band.app", "나중");
        long bandId = createBand(leader, "잔나비");
        String code = issueInvite(leader, bandId, "{\"maxUses\":1}");

        assertThat(join(first, code).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> res = join(second, code);
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("INVITE_EXHAUSTED");
    }

    @Test
    void reissue_revokes_the_previous_code() {
        String leader = signup("inv-leader5@band.app", "리더");
        String joiner = signup("inv-joiner5@band.app", "참여자");
        long bandId = createBand(leader, "쏜애플");
        String oldCode = issueInvite(leader, bandId, null);
        String newCode = issueInvite(leader, bandId, null);
        assertThat(newCode).isNotEqualTo(oldCode);

        ResponseEntity<String> res = join(joiner, oldCode);
        assertThat(res.getStatusCode().value()).isEqualTo(410);
        assertThat(errorCode(res)).isEqualTo("INVITE_REVOKED");

        // 새 코드는 정상 동작한다.
        assertThat(join(joiner, newCode).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void explicitly_revoked_code_is_rejected_as_revoked() {
        String leader = signup("inv-leader6@band.app", "리더");
        String joiner = signup("inv-joiner6@band.app", "참여자");
        long bandId = createBand(leader, "실리카겔");
        String code = issueInvite(leader, bandId, null);

        assertThat(delete("/api/v1/bands/" + bandId + "/invites/current", leader)
                .getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> res = join(joiner, code);
        assertThat(res.getStatusCode().value()).isEqualTo(410);
        assertThat(errorCode(res)).isEqualTo("INVITE_REVOKED");
    }

    @Test
    void unknown_code_is_404() {
        String joiner = signup("inv-joiner7@band.app", "참여자");
        ResponseEntity<String> res = join(joiner, "NOTREAL9");
        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(res)).isEqualTo("INVITE_NOT_FOUND");
    }

    @Test
    void joining_a_band_you_are_already_in_is_409() {
        String leader = signup("inv-leader8@band.app", "리더");
        String joiner = signup("inv-joiner8@band.app", "참여자");
        long bandId = createBand(leader, "아도이");
        String code = issueInvite(leader, bandId, null);
        join(joiner, code);

        ResponseEntity<String> res = join(joiner, code);
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("ALREADY_BAND_MEMBER");
    }

    @Test
    void non_leader_cannot_issue_invite() {
        String leader = signup("inv-leader9@band.app", "리더");
        String member = signup("inv-member9@band.app", "멤버");
        long bandId = createBand(leader, "데이먼스이어");
        join(member, issueInvite(leader, bandId, null));

        ResponseEntity<String> res = post("/api/v1/bands/" + bandId + "/invites", null, member);
        assertThat(res.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("NOT_BAND_LEADER");
    }

    @Test
    void repeated_join_attempts_are_rate_limited() {
        String joiner = signup("inv-flood@band.app", "폭주");

        int lastStatus = 0;
        for (int i = 0; i < 11; i++) {
            lastStatus = join(joiner, "NOTREAL1").getStatusCode().value();
        }
        // 테스트 설정상 분당 10회 → 11번째는 429.
        assertThat(lastStatus).isEqualTo(429);
    }
}
