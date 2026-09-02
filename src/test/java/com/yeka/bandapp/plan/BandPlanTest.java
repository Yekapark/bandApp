package com.yeka.bandapp.plan;

import com.yeka.bandapp.plan.entity.BandPlan;
import com.yeka.bandapp.plan.entity.PlanTier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BandPlan} 상태 전이 단위 테스트 — Docker 불필요.
 * FREE⇒보관일수 있음/만료일 없음, PREMIUM⇒보관일수 NULL 불변식을 메서드가 항상 맞추는지 본다.
 */
class BandPlanTest {

    private static final Instant NOW = Instant.parse("2026-03-01T00:00:00Z");

    @Test
    void free_plan_has_retention_days_and_no_expiry() {
        BandPlan plan = BandPlan.freePlan(1L, NOW);

        assertThat(plan.getTier()).isEqualTo(PlanTier.FREE);
        assertThat(plan.isFree()).isTrue();
        assertThat(plan.retentionDaysOrNull()).isEqualTo(BandPlan.FREE_RETENTION_DAYS);
        assertThat(plan.getExpiresAt()).isNull();
        assertThat(plan.getSubscriptionRef()).isNull();
        assertThat(plan.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    void upgrade_to_premium_clears_retention_and_records_period_and_ref() {
        BandPlan plan = BandPlan.freePlan(1L, NOW);
        Instant periodEnd = NOW.plus(30, ChronoUnit.DAYS);

        plan.upgradeToPremium(NOW, periodEnd, "noop-1");

        assertThat(plan.getTier()).isEqualTo(PlanTier.PREMIUM);
        assertThat(plan.isPremium()).isTrue();
        assertThat(plan.retentionDaysOrNull()).isNull();
        assertThat(plan.getExpiresAt()).isEqualTo(periodEnd);
        assertThat(plan.getSubscriptionRef()).isEqualTo("noop-1");
    }

    @Test
    void downgrade_to_free_restores_retention_and_clears_period_and_ref() {
        BandPlan plan = BandPlan.freePlan(1L, NOW);
        plan.upgradeToPremium(NOW, NOW.plus(30, ChronoUnit.DAYS), "noop-1");

        Instant later = NOW.plus(10, ChronoUnit.DAYS);
        plan.downgradeToFree(later);

        assertThat(plan.getTier()).isEqualTo(PlanTier.FREE);
        assertThat(plan.retentionDaysOrNull()).isEqualTo(BandPlan.FREE_RETENTION_DAYS);
        assertThat(plan.getExpiresAt()).isNull();
        assertThat(plan.getSubscriptionRef()).isNull();
        assertThat(plan.getStartedAt()).isEqualTo(later);
    }

    @Test
    void renew_extends_the_period_only_for_premium() {
        BandPlan premium = BandPlan.freePlan(1L, NOW);
        premium.upgradeToPremium(NOW, NOW.plus(30, ChronoUnit.DAYS), "noop-1");
        Instant newEnd = NOW.plus(60, ChronoUnit.DAYS);

        premium.renew(NOW, newEnd);
        assertThat(premium.getExpiresAt()).isEqualTo(newEnd);

        BandPlan free = BandPlan.freePlan(2L, NOW);
        assertThatThrownBy(() -> free.renew(NOW, newEnd)).isInstanceOf(IllegalStateException.class);
    }
}
