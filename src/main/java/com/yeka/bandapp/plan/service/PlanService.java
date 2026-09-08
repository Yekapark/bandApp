package com.yeka.bandapp.plan.service;

import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.notification.event.NotificationEvents;
import com.yeka.bandapp.plan.config.PlanProperties;
import com.yeka.bandapp.plan.dto.PlanResponse;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 요금제 조회, 그리고 구독기간이 지난 PREMIUM 을 FREE 로 되돌리는 야간 배치.
 *
 * <p>구독 시작·갱신·해지는 스토어(Play Billing)에서 일어나고 {@link StoreSubscriptionService} 가
 * 검증·웹훅으로 반영한다 — 이 서비스는 더 이상 결제를 건드리지 않는다.
 *
 * <p>{@link #expireOverdue(Instant)} 는 스토어 연동과 무관하게 DB 상태만 정리한다(웹훅이 늦거나
 * 빠졌을 때의 안전망). 요청자가 없어 {@code accessGuard} 를 타지 않는다.
 */
@Service
public class PlanService {

    /** 만료 강등 한 페이지 크기 ({@code MediaMaintenanceService} 와 같은 값). */
    public static final int PAGE_SIZE = 200;

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    private final BandAccessGuard accessGuard;
    private final BandPlanRepository bandPlanRepository;
    private final PlanDirectoryService planDirectory;
    private final PlanMutationService planMutationService;
    private final PlanProperties planProperties;
    private final ApplicationEventPublisher eventPublisher;

    public PlanService(BandAccessGuard accessGuard, BandPlanRepository bandPlanRepository,
                       PlanDirectoryService planDirectory, PlanMutationService planMutationService,
                       PlanProperties planProperties, ApplicationEventPublisher eventPublisher) {
        this.accessGuard = accessGuard;
        this.bandPlanRepository = bandPlanRepository;
        this.planDirectory = planDirectory;
        this.planMutationService = planMutationService;
        this.planProperties = planProperties;
        this.eventPublisher = eventPublisher;
    }

    /** 현재 요금제 조회. 밴드 멤버면 누구나. */
    public PlanResponse view(long bandId, long userId) {
        accessGuard.requireActiveMember(bandId, userId);
        return PlanResponse.from(planDirectory.currentPlan(bandId));
    }

    /**
     * 구독기간이 지난 PREMIUM 밴드를 FREE 로 되돌린다. <b>배치 전용</b> — 요청자가 없어
     * {@code accessGuard} 를 타지 않는다({@code PlanExpirationJob} 만 호출한다).
     *
     * <p>스토어 웹훅(EXPIRED)이 정상이면 이 배치가 할 일이 없다. 웹훅이 늦거나 빠진 경우를 위한
     * 안전망이라 스토어를 호출하지 않고 {@code expires_at} 만 보고 정리한다.
     *
     * <p>유예기간은 수동 해지와 같다({@code downgradeGraceDays}, 기본 30일) — 사용자에게
     * "해지든 만료든 30일" 로 설명이 단순해진다.
     *
     * <p>건별 try/catch 라 한 밴드의 실패가 나머지를 막지 않는다({@code MediaMaintenanceService} 관례).
     * 사용자가 같은 순간 해지를 눌러 이미 FREE 가 됐으면 {@code PLAN_ALREADY_FREE} 가 나고 여기서 삼킨다 —
     * {@code applyDowngrade} 가 행 잠금을 잡으므로 동시성 가드가 따라온다.
     *
     * @return FREE 로 강등한 밴드 수
     */
    public int expireOverdue(Instant now) {
        Instant graceUntil = now.plus(planProperties.downgradeGraceDays(), ChronoUnit.DAYS);
        List<Long> bandIds = bandPlanRepository.findExpiredPremiumBandIds(now, PageRequest.of(0, PAGE_SIZE));
        int done = 0;
        for (Long bandId : bandIds) {
            try {
                planMutationService.applyDowngrade(bandId, now, graceUntil);
                // 구독이 조용히 끝나면 유예 뒤 사진·영상이 예고 없이 사라진다. 밴드장에게 알린다.
                eventPublisher.publishEvent(new NotificationEvents.PlanExpired(
                        bandId, planProperties.downgradeGraceDays()));
                done++;
            } catch (RuntimeException e) {
                log.warn("요금제 만료 강등 실패 bandId={} — 다음 실행에서 재시도한다", bandId, e);
            }
        }
        return done;
    }
}
