package com.yeka.bandapp.band.dto;

import com.yeka.bandapp.band.entity.ReservationPermission;
import jakarta.validation.constraints.NotNull;

/**
 * 밴드 설정 변경. 현재는 일정 등록 권한 모드만 다룬다.
 * 알 수 없는 문자열이 오면 Jackson 역직렬화 단계에서 400(INVALID_INPUT)으로 걸린다.
 */
public record UpdateBandSettingsRequest(
        @NotNull ReservationPermission reservationPermission
) {
}
