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
 * 배치 2 — 콜백이 오지 않아 오래 PENDING 인 고아 첨부 정리. 최근 PENDING 과 READY 는 건드리지 않고,
 * 재실행해도 문제 없어야 한다(멱등).
 */
@Import(StorageTestConfig.class)
class OrphanMediaCleanupJobTest extends BoardApiSupport {

    private static final long ONE_KB = 1024;

    @Autowired
    private MediaMaintenanceService mediaMaintenanceService;

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
    void old_pending_is_removed_recent_pending_survives() {
        String leader = signup("orph-l@band.app", "리더");
        long bandId = createBand(leader, "혁오");
        long postId = createPost(leader, bandId, "글", "본문");

        long stale = issuePending(leader, bandId, postId);
        String staleKey = storage.lastPresignedPutKey();
        long recent = issuePending(leader, bandId, postId);

        agePendingRow(stale, Instant.now().minusSeconds(7200));   // 2시간 전 생성으로 위조

        int removed = mediaMaintenanceService.cleanupOrphans(Instant.now().minusSeconds(3600));

        assertThat(removed).isEqualTo(1);
        assertThat(mediaRepository.findById(stale)).isEmpty();
        assertThat(storage.deletedKeys()).contains(staleKey);
        assertThat(mediaRepository.findById(recent).orElseThrow().getStatus()).isEqualTo(MediaStatus.PENDING);

        // 재실행 — 남은 게 없어 아무 일도 하지 않는다.
        assertThat(mediaMaintenanceService.cleanupOrphans(Instant.now().minusSeconds(3600))).isZero();
    }

    @Test
    void ready_media_is_never_treated_as_orphan() {
        String leader = signup("orph-ready-l@band.app", "리더");
        long bandId = createBand(leader, "잔나비");
        long postId = createPost(leader, bandId, "글", "본문");

        long mediaId = issuePending(leader, bandId, postId);
        storage.putObject(storage.lastPresignedPutKey(), ONE_KB, "image/jpeg");
        assertThat(completeUpload(leader, bandId, postId, mediaId).getStatusCode().value()).isEqualTo(200);
        agePendingRow(mediaId, Instant.now().minusSeconds(7200));   // created_at 을 과거로 밀어도

        assertThat(mediaMaintenanceService.cleanupOrphans(Instant.now().minusSeconds(3600))).isZero();
        assertThat(mediaRepository.findById(mediaId).orElseThrow().getStatus()).isEqualTo(MediaStatus.READY);
    }

    private long issuePending(String token, long bandId, long postId) {
        return data(issueUploadUrl(token, bandId, postId, "image/jpeg", ONE_KB)).get("mediaId").asLong();
    }

    private void agePendingRow(long mediaId, Instant createdAt) {
        jdbc.update("update media_attachments set created_at = ? where id = ?",
                Timestamp.from(createdAt), mediaId);
    }
}
