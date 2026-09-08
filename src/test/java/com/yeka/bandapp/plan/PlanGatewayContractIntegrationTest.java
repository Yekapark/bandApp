package com.yeka.bandapp.plan;

import com.yeka.bandapp.support.FakeStorageClient;
import com.yeka.bandapp.support.StorageTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스토어가 구매를 확인해 주지 않을 때 — 402 로 끝나고 요금제·미디어가 그대로여야 한다.
 * 요금제 도메인이 {@link com.yeka.bandapp.plan.gateway.StoreBillingGateway} 인터페이스에만 의존함을 보인다.
 */
@Import({StorageTestConfig.class, RejectingStoreBillingGatewayConfig.class})
class PlanGatewayContractIntegrationTest extends PlanApiSupport {

    @Autowired
    private FakeStorageClient storage;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    @Test
    void unverified_purchase_keeps_the_band_on_free_and_media_untouched() {
        String leader = signup("gw-l@band.app", "리더");
        long bandId = createBand(leader, "게이트웨이밴드");
        long postId = createPost(leader, bandId, "글", "본문");
        long mediaId = uploadReadyMedia(storage, leader, bandId, postId);
        Timestamp before = jdbc.queryForObject(
                "select expires_at from media_attachments where id = ?", Timestamp.class, mediaId);

        ResponseEntity<String> res = subscribe(leader, bandId);

        assertThat(res.getStatusCode().value()).isEqualTo(402);
        assertThat(errorCode(res)).isEqualTo("PURCHASE_NOT_VERIFIED");
        assertThat(data(viewPlan(leader, bandId)).get("tier").asText()).isEqualTo("FREE");

        Timestamp after = jdbc.queryForObject(
                "select expires_at from media_attachments where id = ?", Timestamp.class, mediaId);
        assertThat(after).isEqualTo(before);
    }
}
