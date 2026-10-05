package com.yeka.bandapp.board;

import com.yeka.bandapp.board.entity.MediaStatus;
import com.yeka.bandapp.board.repository.MediaAttachmentRepository;
import com.yeka.bandapp.board.service.MediaMaintenanceService;
import com.yeka.bandapp.support.FakeStorageClient;
import com.yeka.bandapp.support.StorageTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 배치 1 — 보관기한 만료 READY 미디어 → R2 삭제 + EXPIRED.
 *
 * <p>완료 기준(BUILD_PLAN Phase 9): R2 삭제 실패 시에도 트랜잭션이 깨지지 않고 재시도 가능한 구조.
 * {@code storage.failNextDelete()}로 한 건을 실패시켜, 그 행이 READY 로 남고 다음 실행에서 성공하는지 본다.
 */
@Import(StorageTestConfig.class)
class MediaExpirationJobTest extends BoardApiSupport {

    private static final long ONE_KB = 1024;

    @Autowired
    private MediaMaintenanceService mediaMaintenanceService;

    @Autowired
    private MediaAttachmentRepository mediaRepository;

    @Autowired
    private FakeStorageClient storage;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.yeka.bandapp.board.service.MediaDirectoryService mediaDirectory;

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    @Test
    void expired_ready_media_is_deleted_from_r2_and_marked_expired() {
        long mediaId = uploadReady("exp-a");
        String key = storage.lastPresignedPutKey();
        expireAt(mediaId, Instant.now().minusSeconds(3600));

        int expired = mediaMaintenanceService.expireOverdue(Instant.now());

        assertThat(expired).isEqualTo(1);
        assertThat(mediaRepository.findById(mediaId).orElseThrow().getStatus()).isEqualTo(MediaStatus.EXPIRED);
        assertThat(storage.deletedKeys()).contains(key);
        assertThat(storage.objectExists(key)).isFalse();
    }

    @Test
    void r2_delete_failure_leaves_the_row_ready_and_succeeds_on_the_next_run() {
        long first = uploadReady("exp-1");
        String firstKey = storage.lastPresignedPutKey();
        long second = uploadReady("exp-2");
        String secondKey = storage.lastPresignedPutKey();
        // firstKey 가 먼저 조회되도록 더 오래된 만료 시각을 준다(정렬: expires_at asc).
        expireAt(first, Instant.now().minusSeconds(7200));
        expireAt(second, Instant.now().minusSeconds(3600));

        storage.failNextDelete();   // 첫 건의 R2 삭제가 터진다
        int firstRun = mediaMaintenanceService.expireOverdue(Instant.now());

        assertThat(firstRun).isEqualTo(1);
        assertThat(mediaRepository.findById(first).orElseThrow().getStatus()).isEqualTo(MediaStatus.READY);
        assertThat(mediaRepository.findById(second).orElseThrow().getStatus()).isEqualTo(MediaStatus.EXPIRED);
        assertThat(storage.deletedKeys()).contains(secondKey).doesNotContain(firstKey);

        // 다음 실행에서 첫 건이 재시도되어 성공한다.
        int secondRun = mediaMaintenanceService.expireOverdue(Instant.now());
        assertThat(secondRun).isEqualTo(1);
        assertThat(mediaRepository.findById(first).orElseThrow().getStatus()).isEqualTo(MediaStatus.EXPIRED);
        assertThat(storage.deletedKeys()).contains(firstKey);
    }

    @Test
    void non_expired_ready_media_is_untouched_and_the_job_is_idempotent() {
        long fresh = uploadReady("exp-fresh");     // expires_at = 업로드 + 30일 (미래)
        long overdue = uploadReady("exp-old");
        expireAt(overdue, Instant.now().minusSeconds(3600));

        assertThat(mediaMaintenanceService.expireOverdue(Instant.now())).isEqualTo(1);
        assertThat(mediaRepository.findById(fresh).orElseThrow().getStatus()).isEqualTo(MediaStatus.READY);

        int deletedBefore = storage.deletedKeys().size();
        assertThat(mediaMaintenanceService.expireOverdue(Instant.now())).isZero();
        assertThat(storage.deletedKeys()).hasSize(deletedBefore);
    }

    // --- helpers -------------------------------------------------------

    /** QA MEDIA-09 — 만료된 첨부는 주소가 막히고(downloadUrl 없음) 글 제목·본문은 그대로 남는다. */
    @Test
    void expired_media_has_no_download_url_and_the_post_body_survives() {
        String leader = signup("exp-body-l@band.app", "리더");
        long bandId = createBand(leader, "만료본문밴드");
        long postId = createPost(leader, bandId, "남는 제목", "남는 본문");
        long mediaId = data(issueUploadUrl(leader, bandId, postId, "image/jpeg", ONE_KB)).get("mediaId").asLong();
        storage.putObject(storage.lastPresignedPutKey(), ONE_KB, "image/jpeg");
        assertThat(completeUpload(leader, bandId, postId, mediaId).getStatusCode().value()).isEqualTo(200);
        String path = "/api/v1/bands/" + bandId + "/posts/" + postId;
        assertThat(data(get(path, leader)).at("/media/0/downloadUrl").isTextual()).isTrue();

        expireAt(mediaId, Instant.now().minusSeconds(60));
        assertThat(mediaMaintenanceService.expireOverdue(Instant.now())).isEqualTo(1);

        var post = data(get(path, leader));
        assertThat(post.get("title").asText()).isEqualTo("남는 제목");
        assertThat(post.get("content").asText()).isEqualTo("남는 본문");
        var media = post.at("/media/0");
        assertThat(media.get("status").asText()).isEqualTo("EXPIRED");
        assertThat(media.path("downloadUrl").isNull() || media.path("downloadUrl").isMissingNode()).isTrue();
    }

    /** QA MEDIA-10 — 한 밴드의 업그레이드(보관기한 해제)·강등(유예 기한)이 다른 밴드 첨부의 보관기한을 건드리지 않는다. */
    @Test
    void upgrade_and_downgrade_change_retention_only_for_that_band() {
        long a = uploadReady("iso-a");
        long b = uploadReady("iso-b");
        long bandA = jdbc.queryForObject("select p.band_id from media_attachments m join board_posts p on p.id = m.board_post_id "
                + "where m.id = ?", Long.class, a);
        java.util.function.LongFunction<Timestamp> expiry = id ->
                jdbc.queryForObject("select expires_at from media_attachments where id = ?", Timestamp.class, id);
        Timestamp bBefore = expiry.apply(b);
        assertThat(bBefore).isNotNull(); // 무료라 30일 기한

        mediaDirectory.extendRetentionForBand(bandA);   // A 업그레이드
        assertThat(expiry.apply(a)).isNull();
        assertThat(expiry.apply(b)).isEqualTo(bBefore);

        Instant grace = Instant.now().plus(java.time.Duration.ofDays(7));
        mediaDirectory.applyGracePeriodForBand(bandA, grace);   // A 강등 — 유예 기한
        assertThat(expiry.apply(a)).isNotNull();
        assertThat(expiry.apply(b)).isEqualTo(bBefore);
    }

    private long uploadReady(String slugPrefix) {
        String leader = signup(slugPrefix + "-l@band.app", "리더");
        long bandId = createBand(leader, slugPrefix + "밴드");
        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = data(issueUploadUrl(leader, bandId, postId, "image/jpeg", ONE_KB))
                .get("mediaId").asLong();
        storage.putObject(storage.lastPresignedPutKey(), ONE_KB, "image/jpeg");
        assertThat(completeUpload(leader, bandId, postId, mediaId).getStatusCode().value()).isEqualTo(200);
        return mediaId;
    }

    private void expireAt(long mediaId, Instant when) {
        jdbc.update("update media_attachments set expires_at = ? where id = ?", Timestamp.from(when), mediaId);
    }
}
