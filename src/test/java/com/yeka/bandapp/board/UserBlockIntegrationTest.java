package com.yeka.bandapp.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.support.StorageTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 8 완료 기준 ③ — 차단한 사용자의 글이 목록 응답에서 빠진다.
 * 여기에 양방향(나를 차단한 사람의 글도 안 보임), 상세 조회 차단, 차단 해제 복원,
 * 자기 차단·중복 차단·미차단 해제 처리를 함께 본다.
 */
@Import(StorageTestConfig.class)
class UserBlockIntegrationTest extends BoardApiSupport {

    private static final String BLOCKS = "/api/v1/users/me/blocks";

    /** 완료 기준 ③ — A 가 B 를 차단하면 B 의 글이 A 의 목록에서 빠진다. */
    @Test
    void blocked_users_posts_are_excluded_from_the_list() {
        String a = signup("blk-a@band.app", "에이");
        String b = signup("blk-b@band.app", "비");
        long bandId = createBand(a, "혁오");
        join(b, issueInvite(a, bandId, null));
        createPost(a, bandId, "A 글", "본문");
        long bPost = createPost(b, bandId, "B 글", "본문");

        assertThat(data(get(postsPath(bandId), a)).get("count").asInt()).isEqualTo(2);

        ResponseEntity<String> block = post(BLOCKS, "{\"blockedUserId\":" + myUserId(b) + "}", a);
        assertThat(block.getStatusCode().value()).isEqualTo(201);

        JsonNode list = data(get(postsPath(bandId), a));
        assertThat(list.get("count").asInt()).isEqualTo(1);
        list.get("posts").forEach(p -> assertThat(p.get("id").asLong()).isNotEqualTo(bPost));

        ResponseEntity<String> detail = get(postPath(bandId, bPost), a);
        assertThat(detail.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(detail)).isEqualTo("POST_NOT_FOUND");
    }

    /** 차단은 양방향 — B 가 A 를 차단해도 A 는 B 의 글을 볼 수 없다(차단 사실이 역으로 새지 않게). */
    @Test
    void blocking_is_mutual() {
        String a = signup("blk-mut-a@band.app", "에이");
        String b = signup("blk-mut-b@band.app", "비");
        long bandId = createBand(a, "잔나비");
        join(b, issueInvite(a, bandId, null));
        long aPost = createPost(a, bandId, "A 글", "본문");
        long bPost = createPost(b, bandId, "B 글", "본문");

        post(BLOCKS, "{\"blockedUserId\":" + myUserId(a) + "}", b); // B 가 A 를 차단

        // A 목록에서 B 글이 빠지고
        JsonNode aList = data(get(postsPath(bandId), a));
        assertThat(aList.get("count").asInt()).isEqualTo(1);
        assertThat(aList.get("posts").get(0).get("id").asLong()).isEqualTo(aPost);

        // B 목록에서도 A 글이 빠진다
        JsonNode bList = data(get(postsPath(bandId), b));
        assertThat(bList.get("count").asInt()).isEqualTo(1);
        assertThat(bList.get("posts").get(0).get("id").asLong()).isEqualTo(bPost);
    }

    @Test
    void unblocking_restores_the_posts() {
        String a = signup("blk-un-a@band.app", "에이");
        String b = signup("blk-un-b@band.app", "비");
        long bandId = createBand(a, "국카스텐");
        join(b, issueInvite(a, bandId, null));
        createPost(b, bandId, "B 글", "본문");
        long bId = myUserId(b);

        post(BLOCKS, "{\"blockedUserId\":" + bId + "}", a);
        assertThat(data(get(postsPath(bandId), a)).get("count").asInt()).isZero();

        ResponseEntity<String> unblock = delete(BLOCKS + "/" + bId, a);
        assertThat(unblock.getStatusCode().value()).isEqualTo(204);
        assertThat(data(get(postsPath(bandId), a)).get("count").asInt()).isEqualTo(1);
    }

    @Test
    void block_is_global_across_bands() {
        String a = signup("blk-gl-a@band.app", "에이");
        String b = signup("blk-gl-b@band.app", "비");
        long band1 = createBand(a, "밴드하나");
        long band2 = createBand(a, "밴드둘");
        join(b, issueInvite(a, band1, null));
        join(b, issueInvite(a, band2, null));
        createPost(b, band1, "밴드1 B글", "본문");
        createPost(b, band2, "밴드2 B글", "본문");

        post(BLOCKS, "{\"blockedUserId\":" + myUserId(b) + "}", a);

        assertThat(data(get(postsPath(band1), a)).get("count").asInt()).isZero();
        assertThat(data(get(postsPath(band2), a)).get("count").asInt()).isZero();
    }

    @Test
    void self_block_is_rejected() {
        String a = signup("blk-self@band.app", "에이");
        createBand(a, "쏜애플");
        ResponseEntity<String> res = post(BLOCKS, "{\"blockedUserId\":" + myUserId(a) + "}", a);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("CANNOT_BLOCK_SELF");
    }

    @Test
    void duplicate_block_returns_409_and_unknown_unblock_returns_404() {
        String a = signup("blk-dup-a@band.app", "에이");
        String b = signup("blk-dup-b@band.app", "비");
        createBand(a, "실리카겔");
        long bId = myUserId(b);

        assertThat(post(BLOCKS, "{\"blockedUserId\":" + bId + "}", a).getStatusCode().value()).isEqualTo(201);

        ResponseEntity<String> dup = post(BLOCKS, "{\"blockedUserId\":" + bId + "}", a);
        assertThat(dup.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(dup)).isEqualTo("ALREADY_BLOCKED");

        delete(BLOCKS + "/" + bId, a);
        ResponseEntity<String> again = delete(BLOCKS + "/" + bId, a);
        assertThat(again.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(again)).isEqualTo("BLOCK_NOT_FOUND");
    }

    @Test
    void block_list_shows_blocked_users_newest_first() {
        String a = signup("blk-list-a@band.app", "에이");
        String b = signup("blk-list-b@band.app", "비");
        String c = signup("blk-list-c@band.app", "씨");
        createBand(a, "새소년");

        post(BLOCKS, "{\"blockedUserId\":" + myUserId(b) + "}", a);
        post(BLOCKS, "{\"blockedUserId\":" + myUserId(c) + "}", a);

        JsonNode list = data(get(BLOCKS, a));
        assertThat(list.get("count").asInt()).isEqualTo(2);
        assertThat(list.get("blocks").get(0).get("blockedUserId").asLong()).isEqualTo(myUserId(c));
        assertThat(list.get("blocks").get(0).get("blockedUserName").asText()).isEqualTo("씨");
    }
}
