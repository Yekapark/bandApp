package com.yeka.bandapp.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 일정 등록 요청. 겹치는 시간대여도 등록은 성공한다 — 응답에 경고만 실린다(BUILD_PLAN 2장).
 * 시간 순서({@code endAt > startAt})는 서비스에서 검증해 400 {@code INVALID_RESERVATION_PERIOD}로 응답한다.
 */
public record CreateReservationRequest(
        @Schema(description = "합주실 id. 이 밴드의 삭제되지 않은 합주실이어야 한다.", example = "1")
        @NotNull Long roomId,

        @Schema(description = "시작 시각(UTC ISO-8601).", example = "2026-09-10T10:00:00Z")
        @NotNull Instant startAt,

        @Schema(description = "종료 시각(UTC ISO-8601). 시작 시각보다 뒤여야 한다.", example = "2026-09-10T13:00:00Z")
        @NotNull Instant endAt,

        @Schema(description = "합주실 비용(원). 참고용 메모 성격. 생략 가능.", example = "30000")
        @PositiveOrZero Integer cost,

        @Schema(description = "외부 예약 방법 등 자유 기재. 최대 500자.", example = "카톡 예약 완료, 예약자 홍길동")
        @Size(max = 500) String note
) {
}
