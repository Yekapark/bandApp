package com.yeka.bandapp.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.support.FakeStorageClient;
import com.yeka.bandapp.support.StorageTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import static com.yeka.bandapp.support.RateLimitAssertions.assertRateLimited;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 신고 접수 — 게시글·미디어·사용자 대상, 타 밴드 격리, 자기 신고 거부, 중복 접수 거부, 레이트리밋.
 */
@Import(StorageTestConfig.class)
class ReportIntegrationTest extends BoardApiSupport {

    private static final String REPORTS = "/api/v1/reports";

    @Autowired
    private FakeStorageClient storage;

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    @Test
    void member_can_report_a_post_in_their_band() {
        String leader = signup("rp-post-l@band.app", "리더");
        String member = signup("rp-post-m@band.app", "멤버");
        long bandId = createBand(leader, "혁오");
        join(member, issueInvite(leader, bandId, null));
        long postId = createPost(leader, bandId, "문제 글", "본문");

        ResponseEntity<String> res = post(REPORTS,
                "{\"targetType\":\"POST\",\"targetId\":" + postId + ",\"reason\":\"부적절함\"}", member);
        assertThat(res.getStatusCode().value()).isEqualTo(201);
        JsonNode data = data(res);
        assertThat(data.get("targetType").asText()).isEqualTo("POST");
        assertThat(data.get("status").asText()).isEqualTo("OPEN");
    }

    @Test
    void member_can_report_a_media_attachment() {
        String leader = signup("rp-media-l@band.app", "리더");
        String member = signup("rp-media-m@band.app", "멤버");
        long bandId = createBand(leader, "잔나비");
        join(member, issueInvite(leader, bandId, null));
        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = data(issueUploadUrl(leader, bandId, postId, "image/jpeg", 1024))
                .get("mediaId").asLong();

        ResponseEntity<String> res = post(REPORTS,
                "{\"targetType\":\"MEDIA\",\"targetId\":" + mediaId + ",\"reason\":\"저작권\"}", member);
        assertThat(res.getStatusCode().value()).isEqualTo(201);
        assertThat(data(res).get("targetType").asText()).isEqualTo("MEDIA");
    }

    @Test
    void member_can_report_another_user() {
        String a = signup("rp-user-a@band.app", "에이");
        String b = signup("rp-user-b@band.app", "비");
        createBand(a, "국카스텐");

        ResponseEntity<String> res = post(REPORTS,
                "{\"targetType\":\"USER\",\"targetId\":" + myUserId(b) + ",\"reason\":\"괴롭힘\"}", a);
        assertThat(res.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void reporting_a_post_in_another_band_returns_404() {
        String alice = signup("rp-iso-a@band.app", "앨리스");
        String bob = signup("rp-iso-b@band.app", "밥");
        long aliceBand = createBand(alice, "앨리스밴드");
        createBand(bob, "밥밴드");
        long alicePost = createPost(alice, aliceBand, "앨리스 글", "본문");

        ResponseEntity<String> res = post(REPORTS,
                "{\"targetType\":\"POST\",\"targetId\":" + alicePost + ",\"reason\":\"신고\"}", bob);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(res)).isEqualTo("REPORT_TARGET_NOT_FOUND");
    }

    @Test
    void reporting_own_post_or_self_is_rejected() {
        String leader = signup("rp-self-l@band.app", "리더");
        long bandId = createBand(leader, "새소년");
        long postId = createPost(leader, bandId, "내 글", "본문");

        ResponseEntity<String> ownPost = post(REPORTS,
                "{\"targetType\":\"POST\",\"targetId\":" + postId + ",\"reason\":\"셀프\"}", leader);
        assertThat(ownPost.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(ownPost)).isEqualTo("CANNOT_REPORT_SELF");

        ResponseEntity<String> ownUser = post(REPORTS,
                "{\"targetType\":\"USER\",\"targetId\":" + myUserId(leader) + ",\"reason\":\"셀프\"}", leader);
        assertThat(ownUser.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(ownUser)).isEqualTo("CANNOT_REPORT_SELF");
    }

    @Test
    void duplicate_open_report_on_same_target_returns_409() {
        String leader = signup("rp-dup-l@band.app", "리더");
        String member = signup("rp-dup-m@band.app", "멤버");
        long bandId = createBand(leader, "실리카겔");
        join(member, issueInvite(leader, bandId, null));
        long postId = createPost(leader, bandId, "글", "본문");

        String body = "{\"targetType\":\"POST\",\"targetId\":" + postId + ",\"reason\":\"신고\"}";
        assertThat(post(REPORTS, body, member).getStatusCode().value()).isEqualTo(201);

        ResponseEntity<String> dup = post(REPORTS, body, member);
        assertThat(dup.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(dup)).isEqualTo("REPORT_ALREADY_SUBMITTED");
    }

    @Test
    void reports_are_rate_limited_per_user() {
        String a = signup("rp-rl-a@band.app", "에이");
        createBand(a, "쏜애플");
        long target = myUserId(signup("rp-rl-t@band.app", "대상"));
        String body = "{\"targetType\":\"USER\",\"targetId\":" + target + ",\"reason\":\"신고\"}";

        // 테스트 설정상 report 분당 10회.
        // (레이트리밋 검사가 중복 검사보다 먼저라 같은 대상을 반복 신고해도 카운터는 쌓인다.)
        assertRateLimited(10, () -> post(REPORTS, body, a).getStatusCode().value());
    }
}
