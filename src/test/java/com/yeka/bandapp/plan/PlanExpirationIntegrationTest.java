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
        // 강등은 기존 미디어에 유예기간(기본 30일)을 준다 — 수동 해지와 같은 동작.
        Instant grace = mediaExpiresAt(mediaId);
        assertThat(grace).isNotNull();
        assertThat(Duration.between(Instant.now(), grace).toDays()).isBetween(28L, 31L);
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

    /** 구독기간 종료를 원하는 시각으로 옮긴다(시간이 흐른 것처럼). */
    private void expirePlan(long bandId, Instant when) {
        jdbc.update("update band_plans set expires_at = ? where band_id = ?", Timestamp.from(when), bandId);
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
