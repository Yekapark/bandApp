package com.yeka.bandapp.plan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 요금제 설정. {@code app.plan.*}. 값이 유효하지 않으면 안전한 기본값으로 되돌린다
 * ({@code RateLimitProperties} 와 같은 방식).
 *
 * @param premiumPeriodDays  PREMIUM 한 구독기간의 일수. 밴드별 <b>1년 단위</b> 구독이라 기본 365
 *                           (no-op 게이트웨이가 구독기간 종료를 계산할 때 쓴다)
 * @param downgradeGraceDays PREMIUM 해지·만료 시 기존 미디어에 부여하는 유예기간(일).
 *                           구독 주기가 아니라 보관 유예라 1년 구독과 무관하게 30일이다
 * @param planCode           게이트웨이에 넘기는 요금제 코드
 * @param expireCron         구독기간 지난 PREMIUM 을 FREE 로 되돌리는 배치 cron. {@code "-"} 면 비활성
 * @param zone               배치 cron 해석 기준 시간대
 */
@ConfigurationProperties(prefix = "app.plan")
public record PlanProperties(int premiumPeriodDays, int downgradeGraceDays, String planCode,
                             String expireCron, String zone) {

    public PlanProperties {
        if (premiumPeriodDays <= 0) {
            premiumPeriodDays = 365;
        }
        if (downgradeGraceDays <= 0) {
            downgradeGraceDays = 30;
        }
        if (planCode == null || planCode.isBlank()) {
            planCode = "PREMIUM_YEARLY";
        }
        if (expireCron == null || expireCron.isBlank()) {
            expireCron = "0 45 4 * * *";
        }
        if (zone == null || zone.isBlank()) {
            zone = "Asia/Seoul";
        }
    }
}
