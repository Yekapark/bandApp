package com.yeka.bandapp.band.dto;

import com.yeka.bandapp.band.entity.ReservationPermission;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 밴드 설정 변경. 현재는 일정 등록 권한 모드만 다룬다.
 * 알 수 없는 문자열이 오면 Jackson 역직렬화 단계에서 400(INVALID_INPUT)으로 걸린다.
 */
public record UpdateBandSettingsRequest(
        @Schema(description = "일정 등록 권한 모드. LEADER_ONLY=밴드장만 / ANYONE=아무 멤버나 / "
                + "APPROVAL_REQUIRED=멤버가 신청하면 밴드장이 승인",
                example = "ANYONE")
        @NotNull ReservationPermission reservationPermission
) {
}
