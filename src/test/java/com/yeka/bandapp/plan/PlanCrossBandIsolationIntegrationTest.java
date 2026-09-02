package com.yeka.bandapp.plan;

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
 * 요금제 변경의 미디어 재계산이 해당 밴드에만 적용되는지(band_id 서브쿼리 필터) 확인한다.
 */
@Import(StorageTestConfig.class)
class PlanCrossBandIsolationIntegrationTest extends PlanApiSupport {

    @Autowired
    private FakeStorageClient storage;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    @Test
    void upgrade_only_affects_the_upgrading_bands_media() {
        String leader = signup("iso-l@band.app", "리더");
        long bandA = createBand(leader, "밴드A");
        long bandB = createBand(leader, "밴드B");
        long mediaA = uploadReadyMedia(storage, leader, bandA, createPost(leader, bandA, "a", "a"));
        long mediaB = uploadReadyMedia(storage, leader, bandB, createPost(leader, bandB, "b", "b"));

        assertThat(subscribe(leader, bandA).getStatusCode().value()).isEqualTo(200);

        assertThat(expiresAt(mediaA)).isNull();          // A: 무제한
        assertThat(expiresAt(mediaB)).isNotNull();       // B: 그대로 30일
        assertThat(data(viewPlan(leader, bandB)).get("tier").asText()).isEqualTo("FREE");
    }

    @Test
    void downgrade_grace_only_affects_the_downgrading_band() {
        String leader = signup("iso2-l@band.app", "리더");
        long bandA = createBand(leader, "밴드A2");
        long bandB = createBand(leader, "밴드B2");
        assertThat(subscribe(leader, bandA).getStatusCode().value()).isEqualTo(200);
        assertThat(subscribe(leader, bandB).getStatusCode().value()).isEqualTo(200);
        long mediaA = uploadReadyMedia(storage, leader, bandA, createPost(leader, bandA, "a", "a"));
        long mediaB = uploadReadyMedia(storage, leader, bandB, createPost(leader, bandB, "b", "b"));

        assertThat(cancel(leader, bandA).getStatusCode().value()).isEqualTo(200);

        assertThat(expiresAt(mediaA)).isNotNull();       // A: 유예 30일 설정
        assertThat(expiresAt(mediaB)).isNull();          // B: 여전히 무제한
    }

    private Instant expiresAt(long mediaId) {
        Timestamp ts = jdbc.queryForObject(
                "select expires_at from media_attachments where id = ?", Timestamp.class, mediaId);
        return ts == null ? null : ts.toInstant();
    }
}
