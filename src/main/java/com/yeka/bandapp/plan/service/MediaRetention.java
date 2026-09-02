package com.yeka.bandapp.plan.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 미디어 보관기한 계산(순수 함수). 상태·트랜잭션이 없어 Docker 없이 단위 테스트한다
 * ({@code MediaPolicy}/{@code SettlementCalculator} 선례).
 *
 * <p>보관일수는 밴드의 현재 요금제에서 온다: FREE={@link #FREE_RETENTION_DAYS}, PREMIUM=null(무제한).
 */
public final class MediaRetention {

    /** FREE 플랜 보관일수. {@code BandPlan.FREE_RETENTION_DAYS} 및 V10 백필과 일치한다. */
    public static final int FREE_RETENTION_DAYS = 30;

    private MediaRetention() {
    }

    /**
     * 업로드 완료 시각으로부터의 보관기한.
     *
     * @param retentionDays 보관일수. {@code null} 이면 무제한(→ {@code null} 반환), 0 이하이면 호출 오류.
     * @return 만료 시각, 또는 무제한이면 {@code null}
     */
    public static Instant expiresAt(Instant uploadedAt, Integer retentionDays) {
        if (retentionDays == null) {
            return null;
        }
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("보관일수는 1 이상이어야 합니다: " + retentionDays);
        }
        return uploadedAt.plus(retentionDays, ChronoUnit.DAYS);
    }
}
