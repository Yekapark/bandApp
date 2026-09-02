package com.yeka.bandapp.plan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 요금제 설정. {@code app.plan.*}. 값이 유효하지 않으면 안전한 기본값으로 되돌린다
 * ({@code RateLimitProperties} 와 같은 방식).
 *
 * @param premiumPeriodDays  PREMIUM 한 구독기간의 일수(no-op 게이트웨이가 구독기간 종료를 계산할 때)
 * @param downgradeGraceDays PREMIUM 해지 시 기존 미디어에 부여하는 유예기간(일)
 * @param planCode           게이트웨이에 넘기는 요금제 코드
 */
@ConfigurationProperties(prefix = "app.plan")
public record PlanProperties(int premiumPeriodDays, int downgradeGraceDays, String planCode) {

    public PlanProperties {
        if (premiumPeriodDays <= 0) {
            premiumPeriodDays = 30;
        }
        if (downgradeGraceDays <= 0) {
            downgradeGraceDays = 30;
        }
        if (planCode == null || planCode.isBlank()) {
            planCode = "PREMIUM_MONTHLY";
        }
    }
}
