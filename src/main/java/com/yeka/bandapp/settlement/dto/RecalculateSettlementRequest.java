package com.yeka.bandapp.settlement.dto;

import com.yeka.bandapp.settlement.entity.SplitType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

/**
 * 정산 재계산 요청. 현재 밴드 멤버·참석자를 기준으로 몫을 다시 만든다.
 *
 * <p>{@code totalAmount}·{@code splitType}은 둘 다 선택이다 — 넘기면 그 값으로 갱신하고, 생략하면
 * 기존 값을 유지한 채 사람만 다시 반영한다. 계속 대상인 멤버의 납부 여부({@code paid})는 보존된다.
 */
public record RecalculateSettlementRequest(
        @Schema(description = "새 총액(원). 생략하면 기존 값 유지.", example = "32000")
        @Positive Integer totalAmount,

        @Schema(description = "새 분배 방식. 생략하면 기존 값 유지.", example = "ATTENDEES_ONLY")
        SplitType splitType
) {
}
