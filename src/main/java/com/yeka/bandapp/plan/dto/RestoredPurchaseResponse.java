package com.yeka.bandapp.plan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 미완료 구매 복구 결과 — 어느 밴드에 반영됐는지와 그 밴드의 요금제. */
public record RestoredPurchaseResponse(
        @Schema(description = "구매가 반영된 밴드 id (구매에 적힌 밴드)", example = "7")
        long bandId,

        @Schema(description = "반영 뒤 그 밴드의 요금제")
        PlanResponse plan
) {

    public static RestoredPurchaseResponse of(long bandId, PlanResponse plan) {
        return new RestoredPurchaseResponse(bandId, plan);
    }
}
