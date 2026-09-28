package com.yeka.bandapp.plan;

import com.yeka.bandapp.plan.entity.PlanTier;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import com.yeka.bandapp.plan.service.PlanService;
import com.yeka.bandapp.support.FakeStorageClient;
import com.yeka.bandapp.support.StorageTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 구독기간이 지난 PREMIUM 밴드를 FREE 로 되돌리는 배치({@code PlanExpirationJob} → {@code PlanService#expireOverdue}).
 *
 * <p>시각은 {@code Clock} 대신 {@code jdbc.update} 로 {@code expires_at} 을 직접 옮겨 흉내낸다
 * ({@code PlanSubscriptionIntegrationTest}·{@code MediaExpirationJobTest} 와 같은 방식). 배치 cron 은
 * {@code IntegrationTestSupport} 가 {@code "-"} 로 꺼 두므로 스케줄러가 끼어들지 않고, 테스트가 서비스를
 * 직접 호출한다.
 */
@Import(StorageTestConfig.class)
class PlanExpirationIntegrationTest extends PlanApiSupport {

    @Autowired
    private FakeStorageClient storage;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlanService planService;

    @Autowired
    private BandPlanRepository bandPlanRepository;

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    @Test
    void overdue_premium_is_downgraded_and_media_gets_the_grace_expiry() {
        String leader = signup("exp-a@band.app", "리더");
        long bandId = createBand(leader, "만료밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = uploadReadyMedia(storage, leader, bandId, postId);
        assertThat(mediaExpiresAt(mediaId)).isNull();   // PREMIUM: 무제한

        expirePlan(bandId, Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(planService.expireOverdue(Instant.now())).isEqualTo(1);

        assertThat(tierOf(bandId)).isEqualTo(PlanTier.FREE);
        // 강등은 기존 미디어에 유예기간(기본 30일)을 준다. 수동 해지도 결국 이 경로로 온다 —
        // 해지는 즉시 강등이 아니라 만료일 강등 예약이다(BandPlan.cancelAtPeriodEnd).
        Instant grace = mediaExpiresAt(mediaId);
        assertThat(grace).isNotNull();
        assertThat(Duration.between(Instant.now(), grace).toDays()).isBetween(28L, 31L);
    }

    /**
     * 갱신 알림이 늦어 DB 만료일은 지났지만 스토어에선 아직 유효한 구독 — 강등하지 않고 스토어 만료일로 늘린다(B8).
     * 예전에는 돈을 낸 밴드가 FREE 로 내려갔다.
     */
    @Test
    void store_says_still_active_so_it_is_extended_not_downgraded() {
        String leader = signup("exp-store-live@band.app", "리더");
        long bandId = createBand(leader, "갱신늦은밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        expirePlan(bandId, Instant.now().minus(1, ChronoUnit.DAYS), "tok-live-" + bandId);   // no-op: ACTIVE

        assertThat(planService.expireOverdue(Instant.now())).isZero();
        assertThat(tierOf(bandId)).isEqualTo(PlanTier.PREMIUM);
        assertThat(planExpiresAt(bandId)).isAfter(Instant.now().plus(300, ChronoUnit.DAYS));
    }

    /** 스토어가 잠깐 답하지 않으면 며칠은 강등을 미룬다. */
    @Test
    void store_unavailable_briefly_postpones_the_downgrade() {
        String leader = signup("exp-store-down@band.app", "리더");
        long bandId = createBand(leader, "장애밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        expirePlan(bandId, Instant.now().minus(1, ChronoUnit.DAYS), "unavailable-" + bandId);

        assertThat(planService.expireOverdue(Instant.now())).isZero();
        assertThat(tierOf(bandId)).isEqualTo(PlanTier.PREMIUM);
    }

    /** 스토어 장애가 길어져도 무기한 PREMIUM 이 되지는 않는다. */
    @Test
    void store_unavailable_for_days_still_downgrades() {
        String leader = signup("exp-store-long@band.app", "리더");
        long bandId = createBand(leader, "긴장애밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        expirePlan(bandId, Instant.now().minus(5, ChronoUnit.DAYS), "unavailable-" + bandId);

        assertThat(planService.expireOverdue(Instant.now())).isEqualTo(1);
        assertThat(tierOf(bandId)).isEqualTo(PlanTier.FREE);
    }

    @Test
    void premium_within_its_period_is_untouched() {
        String leader = signup("exp-b@band.app", "리더");
        long bandId = createBand(leader, "유효밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        expirePlan(bandId, Instant.now().plus(10, ChronoUnit.DAYS));

        assertThat(planService.expireOverdue(Instant.now())).isZero();
        assertThat(tierOf(bandId)).isEqualTo(PlanTier.PREMIUM);
    }

    /**
     * {@code expires_at} 이 NULL 인 PREMIUM 은 건드리지 않는다. 1년 구독에서는 정상적으로 나올 수 없는
     * 상태지만, 데이터가 어긋났을 때 <b>남의 미디어를 실수로 만료시키지 않는 쪽</b>으로 고정한다.
     */
    @Test
    void premium_without_expiry_is_never_downgraded() {
        String leader = signup("exp-c@band.app", "리더");
        long bandId = createBand(leader, "무기한밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);

        jdbc.update("update band_plans set expires_at = null where band_id = ?", bandId);

        assertThat(planService.expireOverdue(Instant.now())).isZero();
        assertThat(tierOf(bandId)).isEqualTo(PlanTier.PREMIUM);
    }

    @Test
    void free_bands_are_ignored() {
        String leader = signup("exp-d@band.app", "리더");
        long bandId = createBand(leader, "무료밴드");   // 생성 시 FREE 한 행이 붙는다

        assertThat(planService.expireOverdue(Instant.now())).isZero();
        assertThat(tierOf(bandId)).isEqualTo(PlanTier.FREE);
    }

    @Test
    void is_idempotent() {
        String leader = signup("exp-e@band.app", "리더");
        long bandId = createBand(leader, "멱등밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        expirePlan(bandId, Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(planService.expireOverdue(Instant.now())).isEqualTo(1);
        assertThat(planService.expireOverdue(Instant.now())).isZero();   // 두 번째는 대상이 없다
        assertThat(tierOf(bandId)).isEqualTo(PlanTier.FREE);
    }

    @Test
    void other_bands_are_not_affected() {
        String leader = signup("exp-f@band.app", "리더");
        long overdue = createBand(leader, "만료될밴드");
        long healthy = createBand(leader, "멀쩡한밴드");
        assertThat(subscribe(leader, overdue).getStatusCode().value()).isEqualTo(200);
        assertThat(subscribe(leader, healthy).getStatusCode().value()).isEqualTo(200);

        expirePlan(overdue, Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(planService.expireOverdue(Instant.now())).isEqualTo(1);
        assertThat(tierOf(overdue)).isEqualTo(PlanTier.FREE);
        assertThat(tierOf(healthy)).isEqualTo(PlanTier.PREMIUM);
    }

    /**
     * 구독기간 종료를 원하는 시각으로 옮긴다(시간이 흐른 것처럼). 스토어도 "끝났다" 고 답하도록 구매 토큰을
     * no-op 게이트웨이의 {@code expired-} 로 바꾼다 — 배치가 강등 전에 스토어에 다시 묻기 때문이다(B8).
     */
    private void expirePlan(long bandId, Instant when) {
        expirePlan(bandId, when, "expired-" + bandId);
    }

    /** 만료일과 스토어가 답할 구매 토큰을 함께 정한다. */
    private void expirePlan(long bandId, Instant when, String storeToken) {
        jdbc.update("update band_plans set expires_at = ?, purchase_token = ? where band_id = ?",
                Timestamp.from(when), storeToken, bandId);
    }

    private Instant planExpiresAt(long bandId) {
        return jdbc.queryForObject("select expires_at from band_plans where band_id = ?", Timestamp.class, bandId)
                .toInstant();
    }

    private PlanTier tierOf(long bandId) {
        return bandPlanRepository.findByBandId(bandId).orElseThrow().getTier();
    }

    private Instant mediaExpiresAt(long mediaId) {
        Timestamp ts = jdbc.queryForObject(
                "select expires_at from media_attachments where id = ?", Timestamp.class, mediaId);
        return ts == null ? null : ts.toInstant();
    }
}
