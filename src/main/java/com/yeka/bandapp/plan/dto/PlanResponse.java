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
                + "이 시각이 지나면 야간 배치(PlanExpirationJob)가 FREE 로 되돌린다. 되돌아간 뒤 유예 기간이 지나면 첨부 미디어가 만료된다.")
        Instant expiresAt,

        @Schema(description = "해지 예약됨. PREMIUM 인 채로 true 면 \"결제 기간은 남아 있지만 갱신하지 않는다\" 는 뜻이고, "
                + "expiresAt 이 지나면 야간 배치가 FREE 로 내린다. 해지해도 남은 기간의 혜택은 그대로다.")
        boolean canceled,

        @Schema(description = "Google Play 자동 갱신 구독 중(PREMIUM·스토어 결제·해지 예약 아님). 이 밴드는 다음 주기에 "
                + "또 청구되므로 삭제할 수 없고(409 BAND_HAS_ACTIVE_SUBSCRIPTION), 앱은 탈퇴·위임 창에서 안내한다. "
                + "쿠폰 PREMIUM 은 false.")
        boolean autoRenewing,

        @Schema(description = "결제 보류 중. FREE 인데 Google Play 구독이 결제 실패로 계정 보류(ON_HOLD)에 들어가 있다 — 결제 수단을 "
                + "고치면 같은 구독이 되살아나 PREMIUM 으로 돌아온다. 이때 새로 결제하면 이중 청구라 서버가 409 "
                + "BAND_ALREADY_SUBSCRIBED 로 거절한다. 쿠폰·환불·보통 FREE 는 false.")
        boolean onHold
) {

    public static PlanResponse from(PlanView view) {
        return new PlanResponse(view.tier().name(), view.mediaRetentionDays(), view.startedAt(),
                view.expiresAt(), view.canceled(), view.autoRenewing(), view.onHold());
    }

    public static PlanResponse from(BandPlan plan) {
        return new PlanResponse(plan.getTier().name(), plan.retentionDaysOrNull(), plan.getStartedAt(),
                plan.getExpiresAt(), plan.isCanceled(), plan.isAutoRenewingStoreSubscription(), plan.isOnHold());
    }
}
