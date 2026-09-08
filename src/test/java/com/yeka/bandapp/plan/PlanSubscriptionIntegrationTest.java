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
import static org.assertj.core.api.Assertions.within;

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

    /**
     * 해지해도 결제한 기간까지는 PREMIUM 이다. 예전에는 해지 버튼을 누른 자리에서 FREE 로
     * 내려서, 1년치를 결제하고 하루 뒤 해지하면 364일이 증발했다.
     */
    @Test
    void cancel_keeps_premium_and_media_until_the_period_ends() {
        String leader = signup("dn-a@band.app", "리더");
        long bandId = createBand(leader, "다운그레이드밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = uploadReadyMedia(storage, leader, bandId, postId);
        assertThat(expiresAt(mediaId)).isNull(); // PREMIUM 무제한

        // 사용자가 Play 스토어에서 자동갱신을 끄면 RTDN CANCELED 웹훅이 온다.
        assertThat(cancel(leader, bandId).getStatusCode().value()).isEqualTo(200);

        var plan = data(viewPlan(leader, bandId));
        assertThat(plan.get("tier").asText()).isEqualTo("PREMIUM");
        assertThat(plan.get("canceled").asBoolean()).isTrue();
        assertThat(plan.get("mediaRetentionDays").isNull()).isTrue(); // 여전히 무제한
        assertThat(plan.get("expiresAt").isNull()).isFalse();         // 남은 기간 그대로

        // 미디어에는 아직 유예를 주지 않는다 — 만료일 배치가 그때 준다
        // (PlanExpirationIntegrationTest.overdue_premium_is_downgraded_and_media_gets_the_grace_expiry).
        assertThat(expiresAt(mediaId)).isNull();
    }

    @Test
    void cancel_does_not_touch_media() {
        String leader = signup("dn-b@band.app", "리더");
        long bandId = createBand(leader, "만료밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = uploadReadyMedia(storage, leader, bandId, postId);

        // 이미 EXPIRED 로 강제(과거 만료일)
        Instant past = Instant.now().minusSeconds(3600);
        jdbc.update("update media_attachments set status = 'EXPIRED', expires_at = ? where id = ?",
                Timestamp.from(past), mediaId);

        assertThat(cancel(leader, bandId).getStatusCode().value()).isEqualTo(200);

        String status = jdbc.queryForObject(
                "select status from media_attachments where id = ?", String.class, mediaId);
        assertThat(status).isEqualTo("EXPIRED");
        assertThat(expiresAt(mediaId)).isCloseTo(past, within(1, ChronoUnit.SECONDS));
    }

    @Test
    void cancel_webhook_is_idempotent() {
        String leader = signup("dn-c@band.app", "리더");
        long bandId = createBand(leader, "이중해지밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        // RTDN 은 같은 이벤트를 재전송할 수 있다 — 두 번 와도 상태는 한 번 온 것과 같아야 한다.
        assertThat(cancel(leader, bandId).getStatusCode().value()).isEqualTo(200);
        assertThat(cancel(leader, bandId).getStatusCode().value()).isEqualTo(200);

        var plan = data(viewPlan(leader, bandId));
        assertThat(plan.get("tier").asText()).isEqualTo("PREMIUM");
        assertThat(plan.get("canceled").asBoolean()).isTrue();
    }

    @Test
    void media_past_its_expiry_is_expired_by_the_phase9_batch() {
        String leader = signup("dn-batch@band.app", "리더");
        long bandId = createBand(leader, "유예만료밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = uploadReadyMedia(storage, leader, bandId, postId);

        assertThat(cancel(leader, bandId).getStatusCode().value()).isEqualTo(200);
        // 해지는 미디어를 건드리지 않아 만료일이 아직 없다(PREMIUM 무제한). 유예까지 지난 상태를
        // 만들려고 직접 과거로 넣는다 — 여기서 보려는 건 배치가 지난 것을 실제로 만료시키는지다.
        jdbc.update("update media_attachments set expires_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), mediaId);

        int expired = mediaMaintenanceService.expireOverdue(Instant.now());

        assertThat(expired).isEqualTo(1);
        assertThat(mediaRepository.findById(mediaId).orElseThrow().getStatus())
                .isEqualTo(MediaStatus.EXPIRED);
    }

    @Test
    void a_purchase_token_cannot_be_linked_to_a_second_band() {
        String leader = signup("link@band.app", "리더");
        long bandA = createBand(leader, "구매밴드A");
        long bandB = createBand(leader, "구매밴드B");

        assertThat(verifyGoogle(leader, bandA, "shared-tok").getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> reused = verifyGoogle(leader, bandB, "shared-tok");
        assertThat(reused.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(reused)).isEqualTo("PURCHASE_ALREADY_LINKED");
        assertThat(data(viewPlan(leader, bandB)).get("tier").asText()).isEqualTo("FREE");
    }

    @Test
    void re_verifying_an_active_purchase_extends_instead_of_erroring() {
        String leader = signup("idem@band.app", "리더");
        long bandId = createBand(leader, "멱등밴드");

        // 아직 결제 안 한 밴드에 CANCELED 웹훅이 와도(모르는 토큰) 조용히 무시.
        assertThat(cancel(leader, bandId).getStatusCode().value()).isEqualTo(200);
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("FREE");

        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        // 앱 재시작 등으로 같은 구매 토큰을 다시 보내도 409 가 아니라 200(만료일 연장).
        ResponseEntity<String> second = subscribe(leader, bandId);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(data(second).get("tier").asText()).isEqualTo("PREMIUM");
    }

    @Test
    void concurrent_verify_never_500s_and_lands_on_premium() throws Exception {
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
            for (Future<Integer> f : results) {
                int status = f.get();
                assertThat(status).isIn(200, 409); // 첫 업그레이드가 이기고 나머지는 연장(200) 또는 경합(409). 500 없음.
                if (status == 200) {
                    ok++;
                }
            }
            assertThat(ok).isGreaterThanOrEqualTo(1);
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
