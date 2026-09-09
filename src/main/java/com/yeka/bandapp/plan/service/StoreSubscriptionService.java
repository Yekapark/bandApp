package com.yeka.bandapp.plan.service;

import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.plan.config.PlanProperties;
import com.yeka.bandapp.plan.config.StoreBillingProperties;
import com.yeka.bandapp.plan.dto.PlanResponse;
import com.yeka.bandapp.plan.entity.BandPlan;
import com.yeka.bandapp.plan.entity.ProcessedStoreEvent;
import com.yeka.bandapp.plan.entity.Store;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway.StoreSubscription;
import com.yeka.bandapp.plan.gateway.StoreBillingUnavailableException;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import com.yeka.bandapp.plan.repository.ProcessedStoreEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

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

    /** 밴드에 안 붙은 구매·갱신 알림을 재전송받아 볼 시간. 이보다 오래된 메시지는 포기한다. */
    private static final Duration GRANT_RETRY_WINDOW = Duration.ofHours(1);

    private final BandAccessGuard accessGuard;
    private final BandPlanRepository bandPlanRepository;
    private final PlanMutationService planMutationService;
    private final StoreBillingGateway billingGateway;
    private final ProcessedStoreEventRepository processedEvents;
    private final PlanProperties planProperties;
    private final StoreBillingProperties billingProperties;

    public StoreSubscriptionService(BandAccessGuard accessGuard, BandPlanRepository bandPlanRepository,
                                    PlanMutationService planMutationService, StoreBillingGateway billingGateway,
                                    ProcessedStoreEventRepository processedEvents, PlanProperties planProperties,
                                    StoreBillingProperties billingProperties) {
        this.accessGuard = accessGuard;
        this.bandPlanRepository = bandPlanRepository;
        this.planMutationService = planMutationService;
        this.billingGateway = billingGateway;
        this.processedEvents = processedEvents;
        this.planProperties = planProperties;
        this.billingProperties = billingProperties;
    }

    /**
     * 스토어가 돌려준 구독에 PREMIUM 을 줘도 되는지. 세 가지를 다 봐야 한다.
     *
     * <ol>
     *   <li><b>상태</b> — ACTIVE/CANCELED/IN_GRACE 만 준다.
     *   <li><b>상품</b> — 우리가 파는 그 상품이어야 한다({@code app.plan.billing.google-product-id}).
     *       {@code subscriptionsv2.get} 은 패키지 단위라 <b>이 앱의 어떤 구독 토큰이든 통과한다</b> —
     *       상품이 하나뿐인 지금은 무해하지만, 더 싼 상품을 하나라도 추가하는 순간 그 토큰으로
     *       PREMIUM 을 받는 길이 열린다. 상품이 늘기 전에 막아 둔다.
     *   <li><b>만료일</b> — {@code null} 이면 거부한다. 그대로 저장하면 {@code band_plans.expires_at}
     *       이 NULL 이 되고, 만료 배치의 {@code expires_at < now} 에 <b>영원히 걸리지 않아</b>
     *       공짜 무기한 PREMIUM 이 된다. 정상 구독이면 항상 값이 온다.
     * </ol>
     *
     * <p>거부는 호출 경로에 따라 다르게 끝난다 — 사용자 검증은 402, 웹훅은 재전송 대기. 웹훅이
     * 재시도하는 건 만료일 누락 같은 일시적 응답에는 맞고, 상품 불일치처럼 영영 안 바뀌는 건
     * Pub/Sub 보존기간(기본 7일)이 지나면 스스로 포기한다.
     */
    private boolean grantable(StoreSubscription sub) {
        if (!sub.state().grantsPremium()) {
            return false;
        }
        if (!billingProperties.googleProductId().equals(sub.productId())) {
            log.warn("구매 검증: 우리 상품이 아니다 productId={} (기대={})",
                    sub.productId(), billingProperties.googleProductId());
            return false;
        }
        if (sub.expiryTime() == null) {
            log.warn("구매 검증: 만료일이 없다 productId={} — 무기한 PREMIUM 이 되지 않게 거부한다",
                    sub.productId());
            return false;
        }
        return true;
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

        StoreSubscription sub;
        try {
            sub = billingGateway.fetch(Store.GOOGLE_PLAY, purchaseToken)
                    .filter(this::grantable)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PURCHASE_NOT_VERIFIED));
        } catch (StoreBillingUnavailableException transientFailure) {
            // 스토어가 일시적으로 응답 못 함 — 402 로 "잠시 후 다시" 를 안내한다(클라가 재시도).
            log.warn("Play 조회 일시 실패 bandId={}", bandId, transientFailure);
            throw new BusinessException(ErrorCode.PURCHASE_NOT_VERIFIED);
        }

        BandPlan updated = grantPremium(bandId, Instant.now(), sub);

        if (!sub.acknowledged()) {
            // 확인 처리 실패로 응답을 깨지 않는다 — 등급은 이미 올라갔다. 3일 안에 acknowledge 가
            // 안 되면 Play 가 자동 환불하고, 그때 REVOKED 웹훅이 와서 FREE 로 되돌린다(자기수정).
            try {
                billingGateway.acknowledge(Store.GOOGLE_PLAY, sub.productId(), purchaseToken);
            } catch (RuntimeException e) {
                log.error("구매 acknowledge 실패 bandId={} — Play 자동환불 위험", bandId, e);
            }
        }
        return PlanResponse.from(updated);
    }

    /**
     * RTDN 웹훅 한 건. 이미 처리한 {@code messageId} 면 조용히 무시(멱등). 항상 스토어에서 현재 상태를
     * 다시 읽어 반영하므로, 이벤트가 빠지거나 순서가 뒤바뀌어도 다음 이벤트가 자기수정한다.
     */
    public void handleGoogleNotification(String messageId, int notificationType, String purchaseToken,
                                         String publishTime) {
        if (processedEvents.existsById(messageId)) {
            log.debug("RTDN: 이미 처리한 메시지 messageId={}", messageId);
            return;
        }
        Long bandId = bandPlanRepository.findBandIdByPurchaseToken(purchaseToken).orElse(null);
        if (bandId == null) {
            // 갱신·구매 알림인데 아직 밴드에 토큰이 안 붙었다 = 클라이언트 verify 가 곧 온다(경합).
            // 재전송받아 두면, 그 사이 verify 가 토큰을 붙였을 때 다음 재시도가 밴드를 찾는다.
            //
            // 단 영원히 기다리진 않는다. verify 가 끝내 안 오면 토큰은 영영 안 붙고, 그 메시지는
            // Pub/Sub 보존기간(기본 7일) 내내 초당 한 번꼴로 503 을 받아가며 재전송된다. 2026-09-09 에
            // 실제로 이 폭풍이 났다(10분에 600건). 경합은 초 단위라 한 시간이면 넉넉하고, 그 뒤엔
            // 포기해도 잃는 게 없다 — PREMIUM 부여는 클라이언트 verify 가 하고, 늦게라도 verify 가
            // 오면 그쪽이 스토어에 직접 물어 등급을 올린다.
            if (isGrantType(notificationType) && withinGrantRetryWindow(publishTime)) {
                throw new StoreWebhookRetryException(
                        "type=" + notificationType + " 인데 아직 밴드에 안 붙은 토큰 — 재전송 대기");
            }
            log.warn("RTDN: 모르는 구매 토큰 (밴드 없음) type={} — 무시", notificationType);
            markProcessed(messageId, notificationType, purchaseToken);
            return;
        }

        Instant now = Instant.now();
        Instant graceUntil = now.plus(planProperties.downgradeGraceDays(), ChronoUnit.DAYS);

        switch (notificationType) {
            case SUB_PURCHASED, SUB_RENEWED, SUB_RECOVERED, SUB_RESTARTED, SUB_IN_GRACE_PERIOD -> {  // = isGrantType
                // 결제한 밴드의 만료일을 연장하는 경로 — 조회가 실패하면 삼키지 말고 재전송받는다.
                // (만료일이 안 늘어나면 결제한 밴드가 만료 배치에 강등된다.)
                StoreSubscription sub = billingGateway.fetch(Store.GOOGLE_PLAY, purchaseToken)
                        .filter(this::grantable)
                        .orElseThrow(() -> new StoreWebhookRetryException(
                                "type=" + notificationType + " 인데 스토어 조회 실패/무효 bandId=" + bandId));
                grantPremium(bandId, now, sub);
            }
            case SUB_CANCELED -> planMutationService.applyCancelAtPeriodEndIfPremium(bandId, now);
            case SUB_REVOKED -> planMutationService.applyRevoke(bandId, now);
            case SUB_EXPIRED, SUB_ON_HOLD -> planMutationService.applyDowngradeIfPremium(bandId, now, graceUntil);
            default -> log.info("RTDN: 처리하지 않는 type={} bandId={}", notificationType, bandId);
        }

        // 처리가 끝난 뒤 기록한다 — 처리와 기록 사이에서 죽으면 재전송돼 한 번 더 처리되지만,
        // 모든 반영이 멱등이라(applyUpgrade→이미 PREMIUM이면 store-renew, …IfPremium/Revoke는 no-op) 무해하다.
        log.info("RTDN 처리 완료 type={} bandId={} messageId={}", notificationType, bandId, messageId);
        markProcessed(messageId, notificationType, purchaseToken);
    }

    /**
     * FREE 면 업그레이드, 이미 PREMIUM 이면 만료일 연장(스토어 토큰도 함께 기록).
     *
     * <p>한 구매 토큰은 <b>한 밴드</b>에만 붙는다 — 같은 토큰이 다른 밴드에 이미 연결돼 있으면
     * {@code PURCHASE_ALREADY_LINKED}(409). 한 번 산 구독으로 여러 밴드를 PREMIUM 만드는 것을 막는다.
     * (Play 의 obfuscatedAccountId 대조는 슬라이스 2에서 더한다.)
     */
    private BandPlan grantPremium(long bandId, Instant now, StoreSubscription sub) {
        Long linkedBand = bandPlanRepository.findBandIdByPurchaseToken(sub.purchaseToken()).orElse(null);
        if (linkedBand != null && linkedBand != bandId) {
            throw new BusinessException(ErrorCode.PURCHASE_ALREADY_LINKED);
        }
        BandPlan current = bandPlanRepository.findByBandId(bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        if (current.isFree()) {
            try {
                return planMutationService.applyUpgrade(bandId, now, sub.expiryTime(),
                        sub.orderId(), sub.store(), sub.purchaseToken());
            } catch (BusinessException raced) {
                if (raced.errorCode() != ErrorCode.PLAN_ALREADY_PREMIUM) {
                    throw raced;
                }
                // 동시 요청(클라 verify + PURCHASED 웹훅)이 먼저 올려놨다 — 연장으로 이어간다.
            }
        }
        return planMutationService.applyStoreRenew(bandId, now, sub.expiryTime(),
                sub.orderId(), sub.store(), sub.purchaseToken());
    }

    /**
     * 밴드에 안 붙은 grant 이벤트를 아직 재전송받을 만한가. {@code publishTime} 은 Pub/Sub 이 찍은
     * 최초 발행 시각이라 재전송돼도 그대로다 — 그래서 메시지의 나이가 된다.
     *
     * <p>없거나 못 읽으면 {@code true}(예전처럼 재전송 요청). 시각을 모르는 것 때문에 정상 경합을
     * 놓치는 쪽이 더 나쁘다.
     */
    private static boolean withinGrantRetryWindow(String publishTime) {
        if (publishTime == null || publishTime.isBlank()) {
            return true;
        }
        try {
            return Instant.parse(publishTime).isAfter(Instant.now().minus(GRANT_RETRY_WINDOW));
        } catch (DateTimeParseException unreadable) {
            log.warn("RTDN: publishTime 을 못 읽었다 ({}) — 재전송 요청으로 둔다", publishTime);
            return true;
        }
    }

    /** PREMIUM 을 부여·연장하는 알림인지(스토어 재조회가 필요한 쪽). */
    private static boolean isGrantType(int notificationType) {
        return switch (notificationType) {
            case SUB_PURCHASED, SUB_RENEWED, SUB_RECOVERED, SUB_RESTARTED, SUB_IN_GRACE_PERIOD -> true;
            default -> false;
        };
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
