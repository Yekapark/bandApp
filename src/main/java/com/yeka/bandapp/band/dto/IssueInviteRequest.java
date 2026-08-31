package com.yeka.bandapp.band.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 초대코드 발급 옵션. 본문 없이 호출하면 둘 다 기본값이다.
 *
 * @param maxUses 사용 가능 횟수. {@code null} 이면 무제한.
 * @param ttlDays 만료까지 일수. {@code null} 이면 7일.
 */
public record IssueInviteRequest(
        @Min(1) Integer maxUses,
        @Min(1) @Max(90) Integer ttlDays
) {
}
