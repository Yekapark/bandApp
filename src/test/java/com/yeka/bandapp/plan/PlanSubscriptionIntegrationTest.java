package com.yeka.bandapp.plan;

import com.yeka.bandapp.board.entity.MediaStatus;
import com.yeka.bandapp.board.repository.MediaAttachmentRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 요금제 전환과 첨부 미디어 보관기한 재계산.
 *
 * <p>완료 기준(BUILD_PLAN Phase 10): no-op 게이트웨이로 FREE → PREMIUM 전환 시 기존 미디어의 만료일이
 * 연장(= NULL 무제한)되는 것을 확인한다.
 */
@Import(StorageTestConfig.class)
class PlanSubscriptionIntegrationTest extends PlanApiSupport {

    @Autowired
    private FakeStorageClient storage;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MediaMaintenanceService mediaMaintenanceService;

    @Autowired
    private MediaAttachmentRepository mediaRepository;

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    @Test
    void upgrade_to_premium_clears_expiry_of_existing_media() {
        String leader = signup("up-a@band.app", "리더");
        long bandId = createBand(leader, "업그레이드밴드");
        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = uploadReadyMedia(storage, leader, bandId, postId);

        // FREE 상태: 만료일 = 업로드 + 30일
        Instant freeExpiry = expiresAt(mediaId);
        assertThat(freeExpiry).isNotNull();
        assertThat(Duration.between(Instant.now(), freeExpiry).toDays()).isBetween(28L, 31L);

        ResponseEntity<String> res = subscribe(leader, bandId);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(data(res).get("tier").asText()).isEqualTo("PREMIUM");
        assertThat(data(res).get("mediaRetentionDays").isNull()).isTrue();
        assertThat(data(res).get("expiresAt").isNull()).isFalse();

        assertThat(expiresAt(mediaId)).isNull();
    }

    @Test
    void media_uploaded_after_upgrade_has_no_expiry() {
        String leader = signup("up-b@band.app", "리더");
        long bandId = createBand(leader, "프리미엄밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = uploadReadyMedia(storage, leader, bandId, postId);

        assertThat(expiresAt(mediaId)).isNull();
    }

    @Test
    void cancel_gives_existing_media_a_thirty_day_grace() {
        String leader = signup("dn-a@band.app", "리더");
        long bandId = createBand(leader, "다운그레이드밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = uploadReadyMedia(storage, leader, bandId, postId);
        assertThat(expiresAt(mediaId)).isNull(); // PREMIUM 무제한

        ResponseEntity<String> res = cancel(leader, bandId);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(data(res).get("tier").asText()).isEqualTo("FREE");
        assertThat(data(res).get("mediaRetentionDays").asInt()).isEqualTo(30);

        Instant grace = expiresAt(mediaId);
        assertThat(grace).isNotNull();
        assertThat(Duration.between(Instant.now(), grace).toDays()).isBetween(28L, 31L);
    }

    @Test
    void cancel_leaves_already_expired_media_untouched() {
        String leader = signup("dn-b@band.app", "리더");
        long bandId = createBand(leader, "만료밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = uploadReadyMedia(storage, leader, bandId, postId);

        // 이미 EXPIRED 로 강제(과거 만료일)
        jdbc.update("update media_attachments set status = 'EXPIRED', expires_at = ? where id = ?",
                Timestamp.from(Instant.now().minusSeconds(3600)), mediaId);

        assertThat(cancel(leader, bandId).getStatusCode().value()).isEqualTo(200);

        String status = jdbc.queryForObject(
                "select status from media_attachments where id = ?", String.class, mediaId);
        assertThat(status).isEqualTo("EXPIRED");
    }

    @Test
    void downgraded_bands_media_expires_via_the_phase9_batch_after_grace() {
        String leader = signup("dn-batch@band.app", "리더");
        long bandId = createBand(leader, "유예만료밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = uploadReadyMedia(storage, leader, bandId, postId);

        assertThat(cancel(leader, bandId).getStatusCode().value()).isEqualTo(200);
        // 유예기간이 지난 것처럼 만료일을 과거로 당긴다(다운그레이드가 넣은 값을 교체).
        jdbc.update("update media_attachments set expires_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), mediaId);

        int expired = mediaMaintenanceService.expireOverdue(Instant.now());

        assertThat(expired).isEqualTo(1);
        assertThat(mediaRepository.findById(mediaId).orElseThrow().getStatus())
                .isEqualTo(MediaStatus.EXPIRED);
    }

    @Test
    void repeated_transition_in_the_same_direction_is_conflict() {
        String leader = signup("idem@band.app", "리더");
        long bandId = createBand(leader, "멱등밴드");

        assertThat(cancel(leader, bandId).getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(cancel(leader, bandId))).isEqualTo("PLAN_ALREADY_FREE");

        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        ResponseEntity<String> second = subscribe(leader, bandId);
        assertThat(second.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(second)).isEqualTo("PLAN_ALREADY_PREMIUM");
    }

    @Test
    void concurrent_subscribe_lands_exactly_one_premium_and_never_500() throws Exception {
        String leader = signup("conc@band.app", "리더");
        long bandId = createBand(leader, "동시밴드");

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Integer>> calls = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                calls.add(() -> subscribe(leader, bandId).getStatusCode().value());
            }
            List<Future<Integer>> results = pool.invokeAll(calls);

            int ok = 0;
            int conflict = 0;
            for (Future<Integer> f : results) {
                int status = f.get();
                assertThat(status).isIn(200, 409); // 500 절대 없음
                if (status == 200) {
                    ok++;
                } else {
                    conflict++;
                }
            }
            assertThat(ok).isEqualTo(1);
            assertThat(conflict).isEqualTo(threads - 1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("PREMIUM");
    }

    private Instant expiresAt(long mediaId) {
        Timestamp ts = jdbc.queryForObject(
                "select expires_at from media_attachments where id = ?", Timestamp.class, mediaId);
        return ts == null ? null : ts.toInstant();
    }
}
