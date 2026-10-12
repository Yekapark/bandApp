package com.yeka.bandapp.plan.schedule;

import com.yeka.bandapp.plan.service.StoreSubscriptionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 밴드를 못 찾은 구독(V24)을 10분마다 다시 본다 — 밴드에 붙었거나, 스토어에서 끝났거나, 운영자가 반영할 밴드를 정했으면
 * 해결한다({@link StoreSubscriptionService#resolveUnmatchedPurchases}). 운영자가 {@code tools/purchases.py link} 를 치면 늦어도
 * 10분 안에 반영된다. 테스트에서는 {@code unmatched-resolve-cron="-"} 로 끈다.
 * ponytail: 단일 서버라 겹침 잠금이 없다 — 겹쳐도 반영은 같은 밴드 연장이고 해결 표시는 처음 것을 지킨다.
 */
@Component
public class UnmatchedPurchaseResolveJob {

    private final StoreSubscriptionService storeSubscriptionService;

    public UnmatchedPurchaseResolveJob(StoreSubscriptionService storeSubscriptionService) {
        this.storeSubscriptionService = storeSubscriptionService;
    }

    @Scheduled(cron = "${app.plan.unmatched-resolve-cron:0 */10 * * * *}", zone = "${app.plan.zone}")
    public void resolve() {
        storeSubscriptionService.resolveUnmatchedPurchases();
    }
}
