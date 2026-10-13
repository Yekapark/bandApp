package com.yeka.bandapp.settlement.dto;

import com.yeka.bandapp.settlement.repository.MyOutstandingRow;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 한 밴드에서 내가 아직 안 낸 돈 전체(홈 화면 요약 카드용).
 *
 * <p>정산 목록의 {@code myOutstandingTotal} 은 그 페이지의 합계라, 정산이 한 페이지를 넘으면 오래된 미납이
 * 빠진다. 여기는 페이지와 상관없이 전부 더한다. 면제된 몫은 빼고 센다.
 */
public record MyOutstandingResponse(
        @Schema(description = "내가 아직 안 낸 금액의 합.", example = "45000")
        long amount,

        @Schema(description = "아직 안 낸 정산 건수.", example = "2")
        long count
) {

    public static MyOutstandingResponse from(MyOutstandingRow row) {
        return new MyOutstandingResponse(row.amount(), row.count());
    }
}
