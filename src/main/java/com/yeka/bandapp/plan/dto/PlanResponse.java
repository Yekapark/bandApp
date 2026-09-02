package com.yeka.bandapp.plan.dto;

import com.yeka.bandapp.plan.entity.BandPlan;
import com.yeka.bandapp.plan.service.PlanDirectoryService.PlanView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 밴드 요금제 응답.
 */
public record PlanResponse(
        @Schema(description = "요금제 티어", example = "FREE", allowableValues = {"FREE", "PREMIUM"})
        String tier,

        @Schema(description = "첨부 미디어 보관일수. FREE=30, PREMIUM 은 무제한이라 null.", example = "30")
        Integer mediaRetentionDays,

        @Schema(description = "현재 티어가 시작된 시각")
        Instant startedAt,

        @Schema(description = "PREMIUM 현재 구독기간 종료 시각. FREE 면 null. "
                + "이번 릴리스에서는 정보성 — 경과해도 자동으로 FREE 로 되돌리지 않는다.")
        Instant expiresAt
) {

    public static PlanResponse from(PlanView view) {
        return new PlanResponse(view.tier().name(), view.mediaRetentionDays(), view.startedAt(),
                view.expiresAt());
    }

    public static PlanResponse from(BandPlan plan) {
        return new PlanResponse(plan.getTier().name(), plan.retentionDaysOrNull(), plan.getStartedAt(),
                plan.getExpiresAt());
    }
}
