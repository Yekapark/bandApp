package com.yeka.bandapp.band.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 초대코드 발급 옵션. 본문 없이 호출하면 둘 다 기본값이다.
 */
public record IssueInviteRequest(
        @Schema(description = "사용 가능 횟수. 생략(null)하면 무제한.", example = "1")
        @Min(1) Integer maxUses,

        @Schema(description = "만료까지 일수. 생략(null)하면 7일. 1~90.", example = "7")
        @Min(1) @Max(90) Integer ttlDays
) {
}
