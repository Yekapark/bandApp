package com.yeka.bandapp.plan.service;

import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.plan.config.PlanProperties;
import com.yeka.bandapp.plan.dto.PlanResponse;
import com.yeka.bandapp.plan.entity.BandPlan;
import com.yeka.bandapp.plan.entity.ProcessedStoreEvent;
import com.yeka.bandapp.plan.entity.Store;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway.StoreSubscription;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import com.yeka.bandapp.plan.repository.ProcessedStoreEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 스토어 인앱결제 반영. 구매는 클라이언트에서 끝나고(Play Billing) 서버는 두 가지만 한다:
 *
 * <ol>
 *   <li>{@link #verifyGooglePurchase} — 클라이언트가 결제 직후 보낸 구매 토큰을 스토어에 확인하고 PREMIUM 부여
 *   <li>{@link #handleGoogleNotification} — RTDN(Pub/Sub) 웹훅으로 갱신·해지·환불을 반영
 * </ol>
 *
 * <p><b>{@code @Transactional} 없음</b> — 스토어 조회(외부 I/O)가 트랜잭션 안에서 커넥션을 붙잡지 않도록
 * (CLAUDE.md). 조회를 먼저 끝내고, 확정된 값으로 {@link PlanMutationService} 의 짧은 트랜잭션을 부른다.
 */
@Service
public class StoreSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(StoreSubscriptionService.class);

    // Google RTDN SubscriptionNotification.notificationType
    // https://developer.android.com/google/play/billing/rtdn-reference#sub
    private static final int SUB_RECOVERED = 1;
    private static final int SUB_RENEWED = 2;
    private static final int SUB_CANCELED = 3;
    private static final int SUB_PURCHASED = 4;
    private static final int SUB_ON_HOLD = 5;
    private static final int SUB_IN_GRACE_PERIOD = 6;
    private static final int SUB_RESTARTED = 7;
    private static final int SUB_REVOKED = 12;
    private static final int SUB_EXPIRED = 13;

    private final BandAccessGuard accessGuard;
    private final BandPlanRepository bandPlanRepository;
    private final PlanMutationService planMutationService;
    private final StoreBillingGateway billingGateway;
    private final ProcessedStoreEventRepository processedEvents;
    private final PlanProperties planProperties;

    public StoreSubscriptionService(BandAccessGuard accessGuard, BandPlanRepository bandPlanRepository,
                                    PlanMutationService planMutationService, StoreBillingGateway billingGateway,
                                    ProcessedStoreEventRepository processedEvents, PlanProperties planProperties) {
        this.accessGuard = accessGuard;
        this.bandPlanRepository = bandPlanRepository;
        this.planMutationService = planMutationService;
        this.billingGateway = billingGateway;
        this.processedEvents = processedEvents;
        this.planProperties = planProperties;
    }

    /**
     * 클라이언트가 Play 결제를 끝내고 보낸 구매 토큰을 검증하고 PREMIUM 으로 올린다. 밴드장만.
     * 토큰이 스토어에 없거나 구독이 유효 상태가 아니면 {@code PURCHASE_NOT_VERIFIED}(402).
     * 이미 PREMIUM 이면 조회된 만료일로 연장한다(앱 재시작·재전송에 안전).
     */
    public PlanResponse verifyGooglePurchase(long bandId, long userId, String purchaseToken) {
        accessGuard.requireLeader(bandId, userId);
        if (purchaseToken == null || purchaseToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        StoreSubscription sub = billingGateway.fetch(Store.GOOGLE_PLAY, purchaseToken)
                .filter(s -> s.state().grantsPremium())
                .orElseThrow(() -> new BusinessException(ErrorCode.PURCHASE_NOT_VERIFIED));

        BandPlan updated = grantPremium(bandId, Instant.now(), sub);

        if (!sub.acknowledged()) {
            billingGateway.acknowledge(Store.GOOGLE_PLAY, purchaseToken);
        }
        return PlanResponse.from(updated);
    }

    /**
     * RTDN 웹훅 한 건. 이미 처리한 {@code messageId} 면 조용히 무시(멱등). 항상 스토어에서 현재 상태를
     * 다시 읽어 반영하므로, 이벤트가 빠지거나 순서가 뒤바뀌어도 다음 이벤트가 자기수정한다.
     */
    public void handleGoogleNotification(String messageId, int notificationType, String purchaseToken) {
        if (processedEvents.existsById(messageId)) {
            log.debug("RTDN: 이미 처리한 메시지 messageId={}", messageId);
            return;
        }
        Long bandId = bandPlanRepository.findBandIdByPurchaseToken(purchaseToken).orElse(null);
        if (bandId == null) {
            log.warn("RTDN: 모르는 구매 토큰 (밴드 없음) type={}", notificationType);
            markProcessed(messageId, notificationType, purchaseToken);
            return;
        }

        Instant now = Instant.now();
        Instant graceUntil = now.plus(planProperties.downgradeGraceDays(), ChronoUnit.DAYS);
        Optional<StoreSubscription> sub = billingGateway.fetch(Store.GOOGLE_PLAY, purchaseToken);

        switch (notificationType) {
            case SUB_PURCHASED, SUB_RENEWED, SUB_RECOVERED, SUB_RESTARTED, SUB_IN_GRACE_PERIOD ->
                    sub.filter(s -> s.state().grantsPremium())
                            .ifPresentOrElse(s -> grantPremium(bandId, now, s),
                                    () -> log.info("RTDN type={} 인데 스토어 상태가 유효하지 않다 bandId={}",
                                            notificationType, bandId));
            case SUB_CANCELED -> planMutationService.applyCancelAtPeriodEndIfPremium(bandId, now);
            case SUB_REVOKED -> planMutationService.applyRevoke(bandId, now);
            case SUB_EXPIRED, SUB_ON_HOLD -> planMutationService.applyDowngradeIfPremium(bandId, now, graceUntil);
            default -> log.info("RTDN: 처리하지 않는 type={} bandId={}", notificationType, bandId);
        }

        // 처리가 끝난 뒤 기록한다 — 처리와 기록 사이에서 죽으면 재전송돼 한 번 더 처리되지만,
        // 모든 반영이 멱등이라(applyUpgrade→이미 PREMIUM이면 store-renew, …IfPremium/Revoke는 no-op) 무해하다.
        markProcessed(messageId, notificationType, purchaseToken);
    }

    /** FREE 면 업그레이드, 이미 PREMIUM 이면 만료일 연장(스토어 토큰도 함께 기록). */
    private BandPlan grantPremium(long bandId, Instant now, StoreSubscription sub) {
        BandPlan current = bandPlanRepository.findByBandId(bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        if (current.isFree()) {
            return planMutationService.applyUpgrade(bandId, now, sub.expiryTime(),
                    sub.orderId(), sub.store(), sub.purchaseToken());
        }
        return planMutationService.applyStoreRenew(bandId, now, sub.expiryTime(),
                sub.orderId(), sub.store(), sub.purchaseToken());
    }

    /** 처리 완료 표시. 이미 있으면(동시 중복 전달) 삼킨다. */
    private void markProcessed(String messageId, int notificationType, String purchaseToken) {
        try {
            processedEvents.saveAndFlush(ProcessedStoreEvent.of(
                    messageId, Store.GOOGLE_PLAY, notificationType, purchaseToken, Instant.now()));
        } catch (DataIntegrityViolationException alreadyMarked) {
            log.debug("RTDN: 메시지 기록 경합 messageId={}", messageId);
        }
    }
}
