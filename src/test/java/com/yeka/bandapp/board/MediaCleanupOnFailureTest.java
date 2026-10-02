package com.yeka.bandapp.board;

import com.yeka.bandapp.board.entity.MediaAttachment;
import com.yeka.bandapp.board.entity.MediaStatus;
import com.yeka.bandapp.board.repository.MediaAttachmentRepository;
import com.yeka.bandapp.board.service.MediaDirectoryService;
import com.yeka.bandapp.board.service.MediaMaintenanceService;
import com.yeka.bandapp.support.FakeStorageClient;
import com.yeka.bandapp.support.StorageTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R2 삭제가 실패해도 파일이 저장소에 영구히 남지 않아야 한다(저장 비용·숨긴 콘텐츠). 예전에는 DB 행을 먼저
 * 지우거나 EXPIRED 로 바꾼 뒤 R2 를 best-effort 로 지워서, 실패하면 어떤 배치도 그 객체를 다시 찾지 못했다.
 */
@Import(StorageTestConfig.class)
class MediaCleanupOnFailureTest extends BoardApiSupport {

    private static final long ONE_KB = 1024;

    @Autowired
    private MediaMaintenanceService mediaMaintenanceService;

    @Autowired
    private MediaDirectoryService mediaDirectoryService;

    @Autowired
    private MediaAttachmentRepository mediaRepository;

    @Autowired
    private FakeStorageClient storage;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    @Test
    void post_delete_removes_ready_media_from_r2_and_marks_expired() {
        String leader = signup("mc-del-l@band.app", "리더");
        long bandId = createBand(leader, "글삭제밴드");
        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = uploadReady(leader, bandId, postId);
        String key = storage.lastPresignedPutKey();

        assertThat(delete(postPath(bandId, postId), leader).getStatusCode().value()).isEqualTo(204);

        assertThat(storage.objectExists(key)).isFalse();
        assertThat(mediaRepository.findById(mediaId).orElseThrow().getStatus()).isEqualTo(MediaStatus.EXPIRED);
    }

    @Test
    void post_delete_with_r2_failure_is_retried_by_the_expiration_batch() {
        String leader = signup("mc-delf-l@band.app", "리더");
        long bandId = createBand(leader, "글삭제실패밴드");
        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = uploadReady(leader, bandId, postId);
        String key = storage.lastPresignedPutKey();

        storage.failNextDelete();
        assertThat(delete(postPath(bandId, postId), leader).getStatusCode().value()).isEqualTo(204);

        // R2 삭제가 실패한 첨부는 READY 로 남되 보관기한이 지금으로 당겨져 있다.
        MediaAttachment left = mediaRepository.findById(mediaId).orElseThrow();
        assertThat(left.getStatus()).isEqualTo(MediaStatus.READY);
        assertThat(left.getExpiresAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
        assertThat(storage.objectExists(key)).isTrue();

        // 밴드가 그 사이 PREMIUM 으로 올라가도 삭제된 글의 첨부는 무제한 보관으로 되살아나지 않는다.
        mediaDirectoryService.extendRetentionForBand(bandId);
        assertThat(mediaRepository.findById(mediaId).orElseThrow().getExpiresAt()).isNotNull();

        assertThat(mediaMaintenanceService.expireOverdue(Instant.now().plusSeconds(1))).isEqualTo(1);
        assertThat(storage.objectExists(key)).isFalse();
        assertThat(mediaRepository.findById(mediaId).orElseThrow().getStatus()).isEqualTo(MediaStatus.EXPIRED);
    }

    @Test
    void media_delete_with_r2_failure_returns_error_and_keeps_the_row() {
        String leader = signup("mc-mdel-l@band.app", "리더");
        long bandId = createBand(leader, "첨부삭제밴드");
        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = uploadReady(leader, bandId, postId);
        String key = storage.lastPresignedPutKey();

        storage.failNextDelete();
        ResponseEntity<String> failed = delete(mediaPath(bandId, postId) + "/" + mediaId, leader);
        assertThat(failed.getStatusCode().value()).isEqualTo(502);
        assertThat(errorCode(failed)).isEqualTo("MEDIA_STORAGE_ERROR");
        assertThat(mediaRepository.findById(mediaId)).isPresent();

        // 다시 시도하면 지워진다.
        assertThat(delete(mediaPath(bandId, postId) + "/" + mediaId, leader).getStatusCode().value())
                .isEqualTo(204);
        assertThat(mediaRepository.findById(mediaId)).isEmpty();
        assertThat(storage.objectExists(key)).isFalse();
    }

    @Test
    void size_mismatch_with_r2_failure_keeps_pending_row_for_the_orphan_batch() {
        String leader = signup("mc-mis-l@band.app", "리더");
        long bandId = createBand(leader, "위조밴드");
        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = data(issueUploadUrl(leader, bandId, postId, "image/jpeg", ONE_KB)).get("mediaId").asLong();
        String key = storage.lastPresignedPutKey();
        storage.putObject(key, 2 * ONE_KB, "image/jpeg");   // 신고와 다른 크기

        storage.failNextDelete();
        ResponseEntity<String> res = completeUpload(leader, bandId, postId, mediaId);
        assertThat(errorCode(res)).isEqualTo("MEDIA_SIZE_MISMATCH");
        assertThat(mediaRepository.findById(mediaId).orElseThrow().getStatus()).isEqualTo(MediaStatus.PENDING);

        // 고아 배치: R2 삭제가 또 실패하면 행을 남기고, 성공하면 객체와 행을 함께 지운다.
        agePendingRow(mediaId);
        storage.failNextDelete();
        assertThat(mediaMaintenanceService.cleanupOrphans(Instant.now().minusSeconds(3600))).isZero();
        assertThat(mediaRepository.findById(mediaId)).isPresent();

        assertThat(mediaMaintenanceService.cleanupOrphans(Instant.now().minusSeconds(3600))).isEqualTo(1);
        assertThat(mediaRepository.findById(mediaId)).isEmpty();
        assertThat(storage.objectExists(key)).isFalse();
    }

    /** 완료 콜백이 글 확인을 통과한 직후 글이 삭제돼도 READY 로 넘어가지 않는다(고아 배치가 정리). */
    @Test
    void mark_ready_refuses_media_of_a_post_deleted_in_between() {
        String leader = signup("mc-race-l@band.app", "리더");
        long bandId = createBand(leader, "경합밴드");
        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = data(issueUploadUrl(leader, bandId, postId, "image/jpeg", ONE_KB)).get("mediaId").asLong();

        assertThat(delete(postPath(bandId, postId), leader).getStatusCode().value()).isEqualTo(204);

        assertThat(mediaRepository.markReady(mediaId, Instant.now(), null)).isZero();
        assertThat(mediaRepository.findById(mediaId).orElseThrow().getStatus()).isEqualTo(MediaStatus.PENDING);
    }

    private long uploadReady(String token, long bandId, long postId) {
        long mediaId = data(issueUploadUrl(token, bandId, postId, "image/jpeg", ONE_KB)).get("mediaId").asLong();
        storage.putObject(storage.lastPresignedPutKey(), ONE_KB, "image/jpeg");
        assertThat(completeUpload(token, bandId, postId, mediaId).getStatusCode().value()).isEqualTo(200);
        return mediaId;
    }

    private void agePendingRow(long mediaId) {
        jdbc.update("update media_attachments set created_at = ? where id = ?",
                Timestamp.from(Instant.now().minusSeconds(7200)), mediaId);
    }
}
