package com.yeka.bandapp.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 탈퇴 계정 개인정보 파기 정책. {@code app.withdrawal.*}.
 * {@code purgeCron}/{@code purgeZone}은 {@code @Scheduled}가 직접 참조하므로 여기서는 보관일수만 쓴다.
 */
@ConfigurationProperties(prefix = "app.withdrawal")
public record WithdrawalProperties(int retentionDays) {

    public WithdrawalProperties {
        if (retentionDays <= 0) {
            retentionDays = 90;
        }
    }
}
