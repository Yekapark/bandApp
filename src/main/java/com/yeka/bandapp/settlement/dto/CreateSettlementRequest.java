package com.yeka.bandapp.settlement.dto;

import com.yeka.bandapp.settlement.entity.SplitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 정산 생성 요청. 일정당 한 번만 만들 수 있다(이미 있으면 409 {@code SETTLEMENT_ALREADY_EXISTS}).
 * 이후 총액·참석자 변화는 재계산 API 로 반영한다.
 */
public record CreateSettlementRequest(
        @Schema(description = "정산 총액(원). 0보다 커야 한다.", example = "30000")
        @NotNull @Positive Integer totalAmount,

        @Schema(description = "분배 방식. EQUAL=현재 활성 멤버 전원 균등, "
                + "ATTENDEES_ONLY=참석(ATTENDING) 멤버만 균등(참석자 0명이면 409).", example = "EQUAL")
        @NotNull SplitType splitType
) {
}
