package com.yeka.bandapp.plan;

import com.yeka.bandapp.notification.entity.NotificationType;
import com.yeka.bandapp.notification.repository.NotificationDispatchRepository;
import com.yeka.bandapp.notification.service.PlanExpiryReminderService;
import com.yeka.bandapp.plan.entity.BandPlan;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 구독이 끝나기 전에 밴드장이 알 수 있어야 한다.
 *
 * <p>이 알림이 없으면 PREMIUM 이 조용히 끝나고 유예 30일 뒤 사진·영상이 사라진다.
 * 밴드원에게는 합주 영상이 예고 없이 없어지는 일이라, 요금제에서 가장 중요한 안전장치다.
 */
class PlanExpiryReminderIntegrationTest extends PlanApiSupport {

    @Autowired
    private PlanExpiryReminderService reminderService;

    @Autowired
    private BandPlanRepository bandPlanRepository;

    @Autowired
    private NotificationDispatchRepository dispatchRepository;

    /** 구독 만료일을 원하는 시점으로 당긴다 — 1년을 기다릴 수는 없다. */
    private void setExpiry(long bandId, Instant expiresAt) {
        BandPlan plan = bandPlanRepository.findByBandId(bandId).orElseThrow();
        plan.upgradeToPremium(plan.getStartedAt(), expiresAt, "noop-" + bandId);
        bandPlanRepository.saveAndFlush(plan);
    }

    private long countExpiringSoon() {
        return dispatchRepository.findAll().stream()
                .filter(d -> d.getType() == NotificationType.PLAN_EXPIRING_SOON)
                .count();
    }

    @Test
    @DisplayName("만료가 다가오면 밴드장에게 알린다 — 같은 시점은 한 번만")
    void reminds_leader_once_per_lead_time() {
        String leader = signup("exp-lead@band.app", "리더");
        long bandId = createBand(leader, "만료예고밴드");
        subscribe(leader, bandId);

        Instant now = Instant.now();
        setExpiry(bandId, now.plus(6, ChronoUnit.DAYS));   // 7일 구간에만 든다

        assertThat(reminderService.remindExpiringSoon(now)).isEqualTo(1);
        assertThat(countExpiringSoon()).isEqualTo(1);

        // 같은 날 또 돌아도 다시 보내지 않는다(멱등).
        assertThat(reminderService.remindExpiringSoon(now)).isZero();
        assertThat(countExpiringSoon()).isEqualTo(1);
    }

    @Test
    @DisplayName("아직 한참 남았으면 아무것도 안 보낸다")
    void quiet_when_far_from_expiry() {
        String leader = signup("exp-far@band.app", "리더");
        long bandId = createBand(leader, "여유밴드");
        subscribe(leader, bandId);

        Instant now = Instant.now();
        setExpiry(bandId, now.plus(200, ChronoUnit.DAYS));

        assertThat(reminderService.remindExpiringSoon(now)).isZero();
        assertThat(countExpiringSoon()).isZero();
    }

    @Test
    @DisplayName("이미 지난 구독은 예고 대상이 아니다 — 그건 강등 배치의 몫")
    void already_overdue_is_not_reminded() {
        String leader = signup("exp-past@band.app", "리더");
        long bandId = createBand(leader, "지난밴드");
        subscribe(leader, bandId);

        Instant now = Instant.now();
        setExpiry(bandId, now.minus(1, ChronoUnit.DAYS));

        assertThat(reminderService.remindExpiringSoon(now)).isZero();
        assertThat(countExpiringSoon()).isZero();
    }

    @Test
    @DisplayName("멤버에게는 안 간다 — 요금제를 바꿀 수 있는 건 밴드장뿐")
    void only_leader_receives() {
        String leader = signup("exp-l2@band.app", "리더");
        String member = signup("exp-m2@band.app", "멤버");
        long bandId = createBand(leader, "멤버있는밴드");
        join(member, issueInvite(leader, bandId, null));
        subscribe(leader, bandId);

        Instant now = Instant.now();
        setExpiry(bandId, now.plus(6, ChronoUnit.DAYS));

        assertThat(reminderService.remindExpiringSoon(now)).isEqualTo(1);

        long memberUserId = myUserId(member);
        assertThat(dispatchRepository.findAll())
                .filteredOn(d -> d.getType() == NotificationType.PLAN_EXPIRING_SOON)
                .noneMatch(d -> d.getUserId() == memberUserId);
    }
}
