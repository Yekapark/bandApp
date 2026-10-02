package com.yeka.bandapp.plan.service;

import com.yeka.bandapp.plan.entity.BandPlan;
import com.yeka.bandapp.plan.entity.Store;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

/**
 * 결제한 회원이 탈퇴하면 그 사람이 결제한 구독의 <b>다음 결제</b>를 멈춘다(LAUNCH_REVIEW B13). 계정 탈퇴뿐 아니라
 * <b>그 밴드에서 나가거나 추방돼도</b> 같다 — 밴드를 떠난 사람이 남의 밴드 요금을 매년 내게 두지 않는다. 해지가 확인되면
 * 밴드장에게 "기간이 끝나면 프리미엄이 끝나니 누군가 다시 결제해야 한다" 고 알린다({@link PurchaserSubscriptionCanceled}).
 *
 * <p>예전에는 탈퇴 화면에서 "구독은 따로 해지하세요" 라고 안내만 했다 — 안내를 놓친 사람은 앱을 쓸 수도 없는데 매년
 * 청구됐다. 이제 서버가 Play 에 해지를 요청한다. 이미 결제한 기간은 그대로라 그 밴드는 기간이 끝날 때까지 PREMIUM 이고,
 * 해지가 반영되면 Play 가 CANCELED 알림을 보내 요금제가 "해지 예약" 이 된다(웹훅이 처리).
 *
 * <p><b>해지 호출은 탈퇴가 커밋된 뒤에 한다</b> — 외부 HTTP 를 트랜잭션 안에서 부르지 않는다(CLAUDE.md). 호출이 실패해도
 * 탈퇴는 되돌리지 않는다(계정 삭제를 막으면 스토어 정책 위반).
 *
 * <p><b>결제자 연결은 해지가 확인된 뒤에만 끊는다</b>(B17). 실패하면 연결이 남고, {@link #retryPending} 이 매시
 * "결제자가 탈퇴했는데 연결이 남은 구독" 을 다시 해지한다. 같은 배치가 검증·탈퇴가 겹쳐 탈퇴 <b>뒤에</b> 적힌 결제자(B16)도
 * 잡는다. 실패할 때마다 {@value #FAILURE_MARKER} 를 ERROR 로 남겨 매일 점검(deploy/prod-check.sh)이 한 건이라도
 * 있으면 실패로 알린다 — 계속 실패하면 운영자가 Play Console › 주문 관리에서 직접 해지하고, 다음 재시도가 "이미 해지됨" 을
 * 확인해 연결을 정리한다.
 *
 * <p>해지 대상은 요금제 혜택(PREMIUM)이 아니라 <b>스토어 토큰이 남아 있는가</b>로 고른다(B15). 결제 보류(ON_HOLD) 중이면
 * 요금제는 FREE 지만 결제가 복구되면 청구가 이어진다. 이미 끝난 구독인지는 게이트웨이가 스토어에 확인한다(B14).
 */
@Service
public class WithdrawnPurchaserSubscriptions {

    /** prod-check 가 grep 하는 표시. 바꾸면 deploy/prod-check.sh 도 같이 바꾼다. */
    public static final String FAILURE_MARKER = "SUBSCRIPTION_CANCEL_FAILED";

    private static final Logger log = LoggerFactory.getLogger(WithdrawnPurchaserSubscriptions.class);

    private final BandPlanRepository bandPlanRepository;
    private final StoreBillingGateway billingGateway;
    private final PlanMutationService planMutationService;
    private final ApplicationEventPublisher events;

    public WithdrawnPurchaserSubscriptions(BandPlanRepository bandPlanRepository,
                                           StoreBillingGateway billingGateway,
                                           PlanMutationService planMutationService,
                                           ApplicationEventPublisher events) {
        this.bandPlanRepository = bandPlanRepository;
        this.billingGateway = billingGateway;
        this.planMutationService = planMutationService;
        this.events = events;
    }

    /**
     * 결제자가 떠난 구독의 자동 결제 해지를 확인했다. 밴드장 알림용. {@code premiumUntil} 은 결제한 기간의 끝(이미 FREE 면 null).
     */
    public record PurchaserSubscriptionCanceled(long bandId, long purchaserUserId, Instant premiumUntil) {
    }

    private record Target(long userId, long bandId, Store store, String purchaseToken) {
        static Target of(BandPlan p) {
            return new Target(p.getPurchasedByUserId(), p.getBandId(), p.getStore(), p.getPurchaseToken());
        }
    }

    /**
     * 탈퇴 트랜잭션 안에서 부른다. 이 회원이 결제자로 적힌 요금제 중 스토어 토큰이 남은 것은 커밋 뒤에 해지하고(성공하면 그때
     * 연결을 끊는다), 토큰이 없는 것은 해지할 게 없으니 바로 연결을 끊는다.
     */
    @Transactional
    public void cancelRenewalsAfterWithdrawal(long userId) {
        cancelAfterCommit(bandPlanRepository.findByPurchaserForUpdate(userId));
    }

    /**
     * 밴드 탈퇴·추방 트랜잭션 안에서 부른다. 떠난 회원이 이 밴드 구독의 결제자면 계정 탈퇴와 똑같이 커밋 뒤 해지한다.
     */
    @Transactional
    public void cancelRenewalAfterLeavingBand(long bandId, long userId) {
        cancelAfterCommit(bandPlanRepository.findByBandIdForUpdate(bandId)
                .filter(p -> Long.valueOf(userId).equals(p.getPurchasedByUserId()))
                .stream().toList());
    }

    private void cancelAfterCommit(List<BandPlan> plans) {
        List<Target> targets = plans.stream()
                .filter(p -> p.getStore() != null && p.getPurchaseToken() != null)
                .map(Target::of)
                .toList();
        plans.stream()
                .filter(p -> p.getStore() == null || p.getPurchaseToken() == null)
                .forEach(BandPlan::forgetPurchaser);
        if (targets.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cancelAll(targets);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cancelAll(targets);
            }
        });
    }

    /**
     * 해지가 확인되지 않은 떠난 결제자(계정 탈퇴·밴드 탈퇴·추방)의 구독을 다시 해지한다. 정상이면 대상이 없어 쿼리 한 번으로 끝난다.
     * ponytail: 단일 서버라 동시 실행 잠금이 없다 — 겹쳐도 해지는 멱등이라 Play 호출이 한 번 더 갈 뿐.
     */
    @Scheduled(cron = "${app.plan.withdrawn-cancel-retry-cron:0 20 * * * *}", zone = "${app.plan.zone}")
    public void retryPending() {
        cancelAll(bandPlanRepository.findWithDepartedPurchaser().stream().map(Target::of).toList());
    }

    private void cancelAll(List<Target> targets) {
        for (Target t : targets) {
            try {
                billingGateway.cancelRenewal(t.store(), t.purchaseToken());
                BandPlan plan = planMutationService.forgetCanceledPurchaser(t.bandId(), t.purchaseToken(), t.userId());
                log.info("떠난 결제자의 구독 자동 갱신 해지 userId={} bandId={}", t.userId(), t.bandId());
                // 끝난 지 오래된 구독(FREE·보류 아님)이면 알릴 게 없다 — 프리미엄이 남았거나 보류 중일 때만.
                if (plan != null && (plan.isPremium() || plan.isOnHold())) {
                    events.publishEvent(new PurchaserSubscriptionCanceled(t.bandId(), t.userId(), plan.getExpiresAt()));
                }
            } catch (RuntimeException e) {
                // 스택은 남기지 않는다 — 매시 재시도라 같은 스택이 쌓여 에러 급증 검사까지 덮는다. 원인은 메시지(status)에 있다.
                log.error("{} userId={} bandId={} cause={} — 매시 다시 시도한다. 계속되면 Play Console › 주문 관리에서 "
                        + "이 밴드의 구독을 직접 해지할 것", FAILURE_MARKER, t.userId(), t.bandId(), e.toString());
            }
        }
    }
}
