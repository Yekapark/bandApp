package com.yeka.bandapp.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.yeka.bandapp.support.FakeStorageClient;
import com.yeka.bandapp.support.StorageTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 8 완료 기준 ①② — 파일 스트림이 백엔드를 지나지 않고(presigned URL), 신고한 크기·형식과 실제
 * 업로드가 다르면 거부되며 R2 객체가 삭제된다. 여기에 성공 경로(READY 전환·보관기한), 객체 미도착,
 * 중복 완료, 상한 초과, 미지원 형식, 레이트리밋, 타 밴드 격리를 함께 본다.
 */
@Import(StorageTestConfig.class)
class MediaUploadIntegrationTest extends BoardApiSupport {

    private static final long ONE_MB = 1024 * 1024;

    @Autowired
    private FakeStorageClient storage;

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    /** 완료 기준 ① — 발급된 업로드 URL 은 서버가 아니라 객체 저장소를 가리키는 절대 URL 이고, PUT 이다. */
    @Test
    void upload_url_points_directly_at_object_storage() {
        String leader = signup("md-url-l@band.app", "리더");
        long bandId = createBand(leader, "혁오");
        long postId = createPost(leader, bandId, "글", "본문");

        JsonNode data = data(issueUploadUrl(leader, bandId, postId, "image/jpeg", 3 * ONE_MB));
        assertThat(data.get("method").asText()).isEqualTo("PUT");
        assertThat(data.get("uploadUrl").asText()).startsWith("https://").doesNotContain("/api/v1/");
        assertThat(data.get("requiredHeaders").get("Content-Type").asText()).isEqualTo("image/jpeg");
        assertThat(data.get("maxSizeBytes").asLong()).isEqualTo(10 * ONE_MB);
    }

    /** 완료 기준 ② — 신고 크기와 실제 업로드 크기가 다르면 409 + R2 객체·레코드 삭제. */
    @Test
    void complete_rejects_and_deletes_object_when_actual_size_differs() {
        String leader = signup("md-sz-l@band.app", "리더");
        long bandId = createBand(leader, "잔나비");
        long postId = createPost(leader, bandId, "글", "본문");

        long mediaId = data(issueUploadUrl(leader, bandId, postId, "image/jpeg", 1_000)).get("mediaId").asLong();
        String key = storage.lastPresignedPutKey();
        storage.putObject(key, 2_000, "image/jpeg"); // 신고는 1000, 실제는 2000

        ResponseEntity<String> res = completeUpload(leader, bandId, postId, mediaId);
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("MEDIA_SIZE_MISMATCH");
        assertThat(storage.deletedKeys()).contains(key);
        assertThat(storage.objectExists(key)).isFalse();

        // 레코드도 사라졌다 — 상세에 첨부가 없고, 재차 complete 는 404.
        assertThat(data(get(postPath(bandId, postId), leader)).get("mediaCount").asInt()).isZero();
        ResponseEntity<String> again = completeUpload(leader, bandId, postId, mediaId);
        assertThat(again.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(again)).isEqualTo("MEDIA_NOT_FOUND");
    }

    @Test
    void complete_rejects_and_deletes_object_when_actual_content_type_differs() {
        String leader = signup("md-ct-l@band.app", "리더");
        long bandId = createBand(leader, "국카스텐");
        long postId = createPost(leader, bandId, "글", "본문");

        long mediaId = data(issueUploadUrl(leader, bandId, postId, "image/jpeg", 1_000)).get("mediaId").asLong();
        String key = storage.lastPresignedPutKey();
        storage.putObject(key, 1_000, "image/png"); // 크기는 같지만 형식이 다름

        ResponseEntity<String> res = completeUpload(leader, bandId, postId, mediaId);
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("MEDIA_CONTENT_TYPE_MISMATCH");
        assertThat(storage.deletedKeys()).contains(key);
    }

    @Test
    void complete_keeps_row_pending_when_object_is_missing_then_succeeds_on_retry() {
        String leader = signup("md-miss-l@band.app", "리더");
        long bandId = createBand(leader, "새소년");
        long postId = createPost(leader, bandId, "글", "본문");

        long mediaId = data(issueUploadUrl(leader, bandId, postId, "video/mp4", 4 * ONE_MB)).get("mediaId").asLong();
        String key = storage.lastPresignedPutKey();

        ResponseEntity<String> tooEarly = completeUpload(leader, bandId, postId, mediaId);
        assertThat(tooEarly.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(tooEarly)).isEqualTo("MEDIA_NOT_UPLOADED");

        storage.putObject(key, 4 * ONE_MB, "video/mp4");
        ResponseEntity<String> ok = completeUpload(leader, bandId, postId, mediaId);
        assertThat(ok.getStatusCode().value()).isEqualTo(200);
        assertThat(data(ok).get("status").asText()).isEqualTo("READY");
    }

    @Test
    void complete_marks_ready_with_free_plan_expiry_and_download_url() {
        String leader = signup("md-ok-l@band.app", "리더");
        long bandId = createBand(leader, "실리카겔");
        long postId = createPost(leader, bandId, "글", "본문");

        long mediaId = data(issueUploadUrl(leader, bandId, postId, "image/webp", 2 * ONE_MB)).get("mediaId").asLong();
        String key = storage.lastPresignedPutKey();
        storage.putObject(key, 2 * ONE_MB, "image/webp");

        JsonNode media = data(completeUpload(leader, bandId, postId, mediaId));
        assertThat(media.get("status").asText()).isEqualTo("READY");
        assertThat(media.get("type").asText()).isEqualTo("IMAGE");
        assertThat(media.get("downloadUrl").asText()).startsWith("https://").contains("op=get");

        Instant uploadedAt = Instant.parse(media.get("uploadedAt").asText());
        Instant expiresAt = Instant.parse(media.get("expiresAt").asText());
        assertThat(Duration.between(uploadedAt, expiresAt).toDays()).isEqualTo(30);

        // 상세 조회에도 READY 첨부와 다운로드 URL 이 실린다.
        JsonNode detail = data(get(postPath(bandId, postId), leader));
        assertThat(detail.get("mediaCount").asInt()).isEqualTo(1);
        assertThat(detail.get("media").get(0).get("downloadUrl").asText()).startsWith("https://");
    }

    @Test
    void second_complete_after_success_is_rejected() {
        String leader = signup("md-2nd-l@band.app", "리더");
        long bandId = createBand(leader, "쏜애플");
        long postId = createPost(leader, bandId, "글", "본문");

        long mediaId = data(issueUploadUrl(leader, bandId, postId, "image/jpeg", ONE_MB)).get("mediaId").asLong();
        storage.putObject(storage.lastPresignedPutKey(), ONE_MB, "image/jpeg");
        assertThat(completeUpload(leader, bandId, postId, mediaId).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> again = completeUpload(leader, bandId, postId, mediaId);
        assertThat(again.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(again)).isEqualTo("MEDIA_NOT_PENDING");
    }

    @Test
    void upload_url_rejects_oversized_image_without_creating_a_row() {
        String leader = signup("md-big-l@band.app", "리더");
        long bandId = createBand(leader, "혁오둘");
        long postId = createPost(leader, bandId, "글", "본문");

        ResponseEntity<String> res = issueUploadUrl(leader, bandId, postId, "image/jpeg", 10 * ONE_MB + 1);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("MEDIA_SIZE_EXCEEDED");
        assertThat(data(get(postPath(bandId, postId), leader)).get("mediaCount").asInt()).isZero();
    }

    @Test
    void upload_url_rejects_unsupported_content_type() {
        String leader = signup("md-gif-l@band.app", "리더");
        long bandId = createBand(leader, "국카스텐둘");
        long postId = createPost(leader, bandId, "글", "본문");

        ResponseEntity<String> res = issueUploadUrl(leader, bandId, postId, "image/gif", ONE_MB);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(res)).isEqualTo("MEDIA_TYPE_NOT_SUPPORTED");
    }

    @Test
    void upload_url_is_rate_limited_per_user() {
        String leader = signup("md-rl-l@band.app", "리더");
        long bandId = createBand(leader, "잔나비둘");

        int limitHits = 0;
        for (int i = 0; i < 12; i++) {
            long postId = createPost(leader, bandId, "글 " + i, "본문");
            ResponseEntity<String> res = issueUploadUrl(leader, bandId, postId, "image/jpeg", ONE_MB);
            if (res.getStatusCode().value() == 429) {
                limitHits++;
                assertThat(errorCode(res)).isEqualTo("TOO_MANY_REQUESTS");
            }
        }
        assertThat(limitHits).isPositive();
    }

    @Test
    void non_author_member_cannot_attach_media() {
        String leader = signup("md-perm-l@band.app", "리더");
        String member = signup("md-perm-m@band.app", "멤버");
        long bandId = createBand(leader, "새소년둘");
        join(member, issueInvite(leader, bandId, null));
        long postId = createPost(leader, bandId, "리더 글", "본문");

        ResponseEntity<String> res = issueUploadUrl(member, bandId, postId, "image/jpeg", ONE_MB);
        assertThat(res.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("NOT_POST_OWNER");
    }

    @Test
    void upload_url_for_other_bands_post_returns_404() {
        String alice = signup("md-iso-a@band.app", "앨리스");
        String bob = signup("md-iso-b@band.app", "밥");
        long aliceBand = createBand(alice, "앨리스밴드");
        long bobBand = createBand(bob, "밥밴드");
        long alicePost = createPost(alice, aliceBand, "앨리스 글", "본문");

        ResponseEntity<String> res = issueUploadUrl(bob, bobBand, alicePost, "image/jpeg", ONE_MB);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(res)).isEqualTo("POST_NOT_FOUND");
    }
}
