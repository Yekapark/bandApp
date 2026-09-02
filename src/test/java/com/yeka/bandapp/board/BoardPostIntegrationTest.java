package com.yeka.bandapp.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.support.StorageTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게시글 CRUD·커서 페이징·권한·타 밴드 격리. 미디어·차단·신고는 별도 테스트에서 본다.
 */
@Import(StorageTestConfig.class)
class BoardPostIntegrationTest extends BoardApiSupport {

    @Test
    void member_can_create_and_read_a_post() {
        String leader = signup("bp-c-l@band.app", "리더");
        long bandId = createBand(leader, "혁오");
        long postId = createPost(leader, bandId, "첫 글", "합주 사진입니다");

        JsonNode detail = data(get(postPath(bandId, postId), leader));
        assertThat(detail.get("title").asText()).isEqualTo("첫 글");
        assertThat(detail.get("content").asText()).isEqualTo("합주 사진입니다");
        assertThat(detail.get("authorName").asText()).isEqualTo("리더");
        assertThat(detail.get("editable").asBoolean()).isTrue();
        assertThat(detail.get("mediaCount").asInt()).isZero();
    }

    @Test
    void non_member_cannot_read_posts() {
        String leader = signup("bp-nm-l@band.app", "리더");
        String outsider = signup("bp-nm-o@band.app", "외부인");
        long bandId = createBand(leader, "잔나비");
        long postId = createPost(leader, bandId, "글", "본문");

        ResponseEntity<String> list = get(postsPath(bandId), outsider);
        assertThat(list.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(list)).isEqualTo("NOT_BAND_MEMBER");

        ResponseEntity<String> detail = get(postPath(bandId, postId), outsider);
        assertThat(detail.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(detail)).isEqualTo("NOT_BAND_MEMBER");
    }

    @Test
    void other_bands_post_returns_404() {
        String alice = signup("bp-iso-a@band.app", "앨리스");
        String bob = signup("bp-iso-b@band.app", "밥");
        long aliceBand = createBand(alice, "앨리스밴드");
        long bobBand = createBand(bob, "밥밴드");
        long alicePost = createPost(alice, aliceBand, "앨리스 글", "본문");

        ResponseEntity<String> crossBand = get(postPath(bobBand, alicePost), bob);
        assertThat(crossBand.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(crossBand)).isEqualTo("POST_NOT_FOUND");
    }

    @Test
    void author_can_update_and_delete_own_post() {
        String leader = signup("bp-upd-l@band.app", "리더");
        String member = signup("bp-upd-m@band.app", "멤버");
        long bandId = createBand(leader, "국카스텐");
        join(member, issueInvite(leader, bandId, null));
        long postId = createPost(member, bandId, "원래 제목", "원래 본문");

        JsonNode updated = data(put(postPath(bandId, postId),
                "{\"title\":\"고친 제목\",\"content\":\"고친 본문\"}", member));
        assertThat(updated.get("title").asText()).isEqualTo("고친 제목");
        assertThat(updated.get("content").asText()).isEqualTo("고친 본문");

        ResponseEntity<String> deleted = delete(postPath(bandId, postId), member);
        assertThat(deleted.getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> afterDelete = get(postPath(bandId, postId), member);
        assertThat(afterDelete.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(afterDelete)).isEqualTo("POST_NOT_FOUND");
    }

    @Test
    void leader_can_delete_another_members_post_but_a_plain_member_cannot() {
        String leader = signup("bp-perm-l@band.app", "리더");
        String author = signup("bp-perm-a@band.app", "작성자");
        String other = signup("bp-perm-o@band.app", "제3자");
        long bandId = createBand(leader, "새소년");
        join(author, issueInvite(leader, bandId, null));
        join(other, issueInvite(leader, bandId, null));
        long postId = createPost(author, bandId, "작성자 글", "본문");

        ResponseEntity<String> byOther = put(postPath(bandId, postId),
                "{\"title\":\"침범\",\"content\":\"침범\"}", other);
        assertThat(byOther.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(byOther)).isEqualTo("NOT_POST_OWNER");

        ResponseEntity<String> byLeader = delete(postPath(bandId, postId), leader);
        assertThat(byLeader.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void deleted_post_disappears_from_the_list() {
        String leader = signup("bp-del-l@band.app", "리더");
        long bandId = createBand(leader, "실리카겔");
        long keep = createPost(leader, bandId, "남길 글", "본문");
        long remove = createPost(leader, bandId, "지울 글", "본문");

        delete(postPath(bandId, remove), leader);

        JsonNode list = data(get(postsPath(bandId), leader));
        assertThat(list.get("count").asInt()).isEqualTo(1);
        assertThat(list.get("posts").get(0).get("id").asLong()).isEqualTo(keep);
    }

    @Test
    void cursor_paging_returns_pages_in_created_desc_order_without_duplicates() {
        String leader = signup("bp-pg-l@band.app", "리더");
        long bandId = createBand(leader, "쏜애플");
        long[] ids = new long[5];
        for (int i = 0; i < 5; i++) {
            ids[i] = createPost(leader, bandId, "글 " + i, "본문 " + i);
        }

        JsonNode page1 = data(get(postsPath(bandId) + "?limit=2", leader));
        assertThat(page1.get("posts")).hasSize(2);
        assertThat(page1.get("hasNext").asBoolean()).isTrue();
        assertThat(page1.get("posts").get(0).get("id").asLong()).isEqualTo(ids[4]);
        assertThat(page1.get("posts").get(1).get("id").asLong()).isEqualTo(ids[3]);

        String cursor = page1.get("nextCursor").asText();
        JsonNode page2 = data(get(postsPath(bandId) + "?limit=2&cursor=" + cursor, leader));
        assertThat(page2.get("posts").get(0).get("id").asLong()).isEqualTo(ids[2]);
        assertThat(page2.get("posts").get(1).get("id").asLong()).isEqualTo(ids[1]);

        String cursor2 = page2.get("nextCursor").asText();
        JsonNode page3 = data(get(postsPath(bandId) + "?limit=2&cursor=" + cursor2, leader));
        assertThat(page3.get("posts")).hasSize(1);
        assertThat(page3.get("posts").get(0).get("id").asLong()).isEqualTo(ids[0]);
        assertThat(page3.get("hasNext").asBoolean()).isFalse();
        assertThat(page3.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void malformed_cursor_is_rejected() {
        String leader = signup("bp-badcur-l@band.app", "리더");
        long bandId = createBand(leader, "혁오둘");
        createPost(leader, bandId, "글", "본문");

        ResponseEntity<String> res = get(postsPath(bandId) + "?cursor=not-a-cursor!!!", leader);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("POST_CURSOR_INVALID");
    }

    @Test
    void post_survives_author_withdrawal_and_still_lists() {
        String leader = signup("bp-wd-l@band.app", "리더");
        String quitter = signup("bp-wd-q@band.app", "떠날사람");
        long bandId = createBand(leader, "국카스텐셋");
        join(quitter, issueInvite(leader, bandId, null));
        long quitterId = myUserId(quitter);
        createPost(quitter, bandId, "떠나기 전 글", "본문");
        withdraw(quitter);

        JsonNode list = data(get(postsPath(bandId), leader));
        assertThat(list.get("count").asInt()).isEqualTo(1);
        assertThat(list.get("posts").get(0).get("authorId").asLong()).isEqualTo(quitterId);
        assertThat(list.get("posts").get(0).get("authorName").asText()).isNotBlank();
    }
}
