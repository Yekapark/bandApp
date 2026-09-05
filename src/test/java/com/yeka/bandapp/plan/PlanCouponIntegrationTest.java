package com.yeka.bandapp.plan;

import com.yeka.bandapp.plan.entity.PlanTier;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PREMIUM 맛보기 쿠폰 사용.
 *
 * <p>발급 화면이 없어(앱 전역 관리자 역할 부재 — V12 주석) 쿠폰은 운영자가 SQL 로 넣는다.
 * 테스트도 같은 방식으로 만든다 — 실제 운영 절차를 그대로 검증하는 셈이다.
 */
class PlanCouponIntegrationTest extends PlanApiSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BandPlanRepository bandPlanRepository;

    @Test
    void free_band_becomes_premium_for_the_granted_days() {
        String leader = signup("cp-a@band.app", "리더");
        long bandId = createBand(leader, "쿠폰밴드");
        insertCoupon("TASTE30A", 30, null, null);

        ResponseEntity<String> res = redeemCoupon(leader, bandId, "TASTE30A");

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(data(res).get("tier").asText()).isEqualTo("PREMIUM");
        assertThat(data(res).get("mediaRetentionDays").isNull()).isTrue();
        assertThat(daysUntilExpiry(bandId)).isBetween(28L, 31L);
    }

    /** 이미 PREMIUM 이면 남은 기간에 더한다 — 쿠폰을 일찍 쓴 사람이 손해 보지 않게. */
    @Test
    void premium_band_gets_the_days_added_to_its_remaining_period() {
        String leader = signup("cp-b@band.app", "리더");
        long bandId = createBand(leader, "연장밴드");
        assertThat(subscribe(leader, bandId).getStatusCode().value()).isEqualTo(200);
        long before = daysUntilExpiry(bandId);           // 1년 구독이라 대략 365
        insertCoupon("TASTE60B", 60, null, null);

        assertThat(redeemCoupon(leader, bandId, "TASTE60B").getStatusCode().value()).isEqualTo(200);

        assertThat(daysUntilExpiry(bandId)).isBetween(before + 59, before + 61);
        assertThat(tierOf(bandId)).isEqualTo(PlanTier.PREMIUM);
    }

    @Test
    void lowercase_and_padded_codes_are_accepted() {
        String leader = signup("cp-c@band.app", "리더");
        long bandId = createBand(leader, "대소문자밴드");
        insertCoupon("TASTE90C", 90, null, null);

        assertThat(redeemCoupon(leader, bandId, "  taste90c ").getStatusCode().value()).isEqualTo(200);
        assertThat(tierOf(bandId)).isEqualTo(PlanTier.PREMIUM);
    }

    @Test
    void same_band_cannot_use_the_same_coupon_twice() {
        String leader = signup("cp-d@band.app", "리더");
        long bandId = createBand(leader, "재사용밴드");
        insertCoupon("REUSE001", 30, null, null);

        assertThat(redeemCoupon(leader, bandId, "REUSE001").getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> again = redeemCoupon(leader, bandId, "REUSE001");
        assertThat(again.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(again)).isEqualTo("COUPON_ALREADY_USED");
    }

    @Test
    void exhausted_coupon_is_rejected() {
        String first = signup("cp-e1@band.app", "리더1");
        String second = signup("cp-e2@band.app", "리더2");
        long bandOne = createBand(first, "선착순밴드");
        long bandTwo = createBand(second, "늦은밴드");
        insertCoupon("ONLYONE1", 30, 1, null);

        assertThat(redeemCoupon(first, bandOne, "ONLYONE1").getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> res = redeemCoupon(second, bandTwo, "ONLYONE1");
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(res)).isEqualTo("COUPON_EXHAUSTED");
        assertThat(tierOf(bandTwo)).isEqualTo(PlanTier.FREE);
    }

    @Test
    void expired_coupon_is_rejected() {
        String leader = signup("cp-f@band.app", "리더");
        long bandId = createBand(leader, "기한밴드");
        insertCoupon("OLDONE01", 30, null, Instant.now().minus(1, ChronoUnit.DAYS));

        ResponseEntity<String> res = redeemCoupon(leader, bandId, "OLDONE01");

        assertThat(res.getStatusCode().value()).isEqualTo(410);
        assertThat(errorCode(res)).isEqualTo("COUPON_EXPIRED");
        assertThat(tierOf(bandId)).isEqualTo(PlanTier.FREE);
    }

    @Test
    void revoked_coupon_is_rejected() {
        String leader = signup("cp-g@band.app", "리더");
        long bandId = createBand(leader, "무효밴드");
        insertCoupon("REVOKED1", 30, null, null);
        jdbc.update("update plan_coupons set revoked = true where code = ?", "REVOKED1");

        assertThat(redeemCoupon(leader, bandId, "REVOKED1").getStatusCode().value()).isEqualTo(410);
        assertThat(tierOf(bandId)).isEqualTo(PlanTier.FREE);
    }

    @Test
    void unknown_code_is_not_found() {
        String leader = signup("cp-h@band.app", "리더");
        long bandId = createBand(leader, "없는코드밴드");

        ResponseEntity<String> res = redeemCoupon(leader, bandId, "NOSUCH01");

        assertThat(res.getStatusCode().value()).isEqualTo(404);
        assertThat(errorCode(res)).isEqualTo("COUPON_NOT_FOUND");
    }

    @Test
    void member_who_is_not_leader_cannot_redeem() {
        String leader = signup("cp-i1@band.app", "리더");
        String member = signup("cp-i2@band.app", "멤버");
        long bandId = createBand(leader, "권한밴드");
        join(member, issueInvite(leader, bandId, null));
        insertCoupon("LEADONLY", 30, null, null);

        ResponseEntity<String> res = redeemCoupon(member, bandId, "LEADONLY");

        assertThat(res.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("NOT_BAND_LEADER");
        assertThat(tierOf(bandId)).isEqualTo(PlanTier.FREE);
    }

    @Test
    void outsider_cannot_redeem_for_someone_elses_band() {
        String leader = signup("cp-j1@band.app", "리더");
        String outsider = signup("cp-j2@band.app", "외부인");
        long bandId = createBand(leader, "격리밴드");
        createBand(outsider, "남의밴드");
        insertCoupon("ISOLATE1", 30, null, null);

        ResponseEntity<String> res = redeemCoupon(outsider, bandId, "ISOLATE1");

        assertThat(res.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(res)).isEqualTo("NOT_BAND_MEMBER");
        assertThat(tierOf(bandId)).isEqualTo(PlanTier.FREE);
    }

    /** 실패한 사용이 쿠폰 횟수를 축내지 않는지 — 소진 판정이 어긋나면 남은 장수가 새어 나간다. */
    @Test
    void failed_redemption_does_not_consume_a_use() {
        String leader = signup("cp-k1@band.app", "리더");
        String outsider = signup("cp-k2@band.app", "외부인");
        long bandId = createBand(leader, "누수밴드");
        createBand(outsider, "외부밴드");
        insertCoupon("NOLEAK01", 30, 1, null);

        assertThat(redeemCoupon(outsider, bandId, "NOLEAK01").getStatusCode().value()).isEqualTo(403);

        assertThat(usedCount("NOLEAK01")).isZero();
        assertThat(redeemCoupon(leader, bandId, "NOLEAK01").getStatusCode().value()).isEqualTo(200);
        assertThat(usedCount("NOLEAK01")).isEqualTo(1);
    }

    private ResponseEntity<String> redeemCoupon(String token, long bandId, String code) {
        return post(planPath(bandId) + "/coupons/redeem", "{\"code\":\"" + code + "\"}", token);
    }

    /** 운영자가 하는 발급 INSERT 와 같은 형태(V12 주석의 예시). */
    private void insertCoupon(String code, int grantDays, Integer maxUses, Instant expiresAt) {
        jdbc.update("insert into plan_coupons (code, grant_days, max_uses, expires_at, created_at) "
                        + "values (?, ?, ?, ?, ?)",
                code, grantDays, maxUses,
                expiresAt == null ? null : Timestamp.from(expiresAt),
                Timestamp.from(Instant.now()));
    }

    private int usedCount(String code) {
        Integer n = jdbc.queryForObject(
                "select used_count from plan_coupons where code = ?", Integer.class, code);
        return n == null ? 0 : n;
    }

    private PlanTier tierOf(long bandId) {
        return bandPlanRepository.findByBandId(bandId).orElseThrow().getTier();
    }

    private long daysUntilExpiry(long bandId) {
        Instant expiresAt = bandPlanRepository.findByBandId(bandId).orElseThrow().getExpiresAt();
        assertThat(expiresAt).isNotNull();
        return Duration.between(Instant.now(), expiresAt).toDays();
    }
}
