package com.yeka.bandapp.plan.service;

import com.yeka.bandapp.plan.entity.BandPlan;
import com.yeka.bandapp.plan.entity.Store;
import com.yeka.bandapp.plan.gateway.StoreBillingGateway;
import com.yeka.bandapp.plan.repository.BandPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 결제한 회원이 탈퇴하면 그 사람이 결제한 구독의 <b>다음 결제</b>를 멈춘다(LAUNCH_REVIEW B13).
 *
 * <p>예전에는 탈퇴 화면에서 "구독은 따로 해지하세요" 라고 안내만 했다 — 안내를 놓친 사람은 앱을 쓸 수도 없는데 매년
 * 청구됐다. 이제 서버가 Play 에 해지를 요청한다. 이미 결제한 기간은 그대로라 그 밴드는 기간이 끝날 때까지 PREMIUM 이고,
 * 해지가 반영되면 Play 가 CANCELED 알림을 보내 요금제가 "해지 예약" 이 된다(웹훅이 처리).
 *
 * <p><b>해지 호출은 탈퇴가 커밋된 뒤에 한다</b> — 외부 HTTP 를 트랜잭션 안에서 부르지 않는다(CLAUDE.md). 호출이 실패해도
 * 탈퇴는 되돌리지 않는다(계정 삭제를 막으면 스토어 정책 위반). 실패는 ERROR 로그로 남아 매일 점검(prod-check)에 걸리고,
 * 운영자가 Play Console › 주문 관리에서 직접 해지한다.
 */
@Service
public class WithdrawnPurchaserSubscriptions {

    private static final Logger log = LoggerFactory.getLogger(WithdrawnPurchaserSubscriptions.class);

    private final BandPlanRepository bandPlanRepository;
    private final StoreBillingGateway billingGateway;

    public WithdrawnPurchaserSubscriptions(BandPlanRepository bandPlanRepository,
                                           StoreBillingGateway billingGateway) {
        this.bandPlanRepository = bandPlanRepository;
        this.billingGateway = billingGateway;
    }

    private record Target(long bandId, Store store, String purchaseToken) {
    }

    /**
     * 탈퇴 트랜잭션 안에서 부른다. 이 회원이 결제자로 적힌 요금제에서 연결을 끊고, 자동 갱신 중인 스토어 구독은
     * 커밋 뒤에 해지한다. 결제자 연결은 해지 여부와 관계없이 끊는다 — 탈퇴한 사람과 결제 기록을 이어 둘 이유가 없다.
     */
    @Transactional
    public void cancelRenewalsAfterWithdrawal(long userId) {
        List<BandPlan> plans = bandPlanRepository.findByPurchaserForUpdate(userId);
        List<Target> targets = plans.stream()
                .filter(BandPlan::isAutoRenewingStoreSubscription)
                .map(p -> new Target(p.getBandId(), p.getStore(), p.getPurchaseToken()))
                .toList();
        plans.forEach(BandPlan::forgetPurchaser);
        if (targets.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cancelAll(userId, targets);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cancelAll(userId, targets);
            }
        });
    }

    private void cancelAll(long userId, List<Target> targets) {
        for (Target t : targets) {
            try {
                billingGateway.cancelRenewal(t.store(), t.purchaseToken());
                log.info("탈퇴한 결제자의 구독 자동 갱신 해지 userId={} bandId={}", userId, t.bandId());
            } catch (RuntimeException e) {
                log.error("탈퇴한 결제자의 구독 자동 갱신을 해지하지 못했다 userId={} bandId={} "
                        + "— Play Console › 주문 관리에서 이 밴드의 구독을 직접 해지할 것", userId, t.bandId(), e);
            }
        }
    }
}
