package com.yeka.bandapp.recurring.dto;

import com.yeka.bandapp.recurring.entity.RecurringFrequency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 정기 일정 규칙 등록 요청. {@code dayOfWeek}·{@code startTime}·{@code endTime}은 로컬(Asia/Seoul)
 * 기준으로 해석한다. 시간 순서({@code endTime > startTime})와 날짜 순서({@code endDate >= startDate})는
 * 서비스에서 검증한다.
 */
public record CreateRecurringRuleRequest(
        @Schema(description = "합주실 id. 이 밴드의 삭제되지 않은 합주실이어야 한다.", example = "1")
        @NotNull Long roomId,

        @Schema(description = "반복 주기.", example = "WEEKLY")
        @NotNull RecurringFrequency frequency,

        @Schema(description = "반복 요일(java.time.DayOfWeek 이름).", example = "SATURDAY")
        @NotNull DayOfWeek dayOfWeek,

        @Schema(description = "합주 시작 시각(로컬, Asia/Seoul).", example = "15:00")
        @NotNull LocalTime startTime,

        @Schema(description = "합주 종료 시각(로컬, Asia/Seoul). 시작 시각보다 뒤여야 한다.", example = "18:00")
        @NotNull LocalTime endTime,

        @Schema(description = "규칙 시작일. 이 날짜 이후 처음 맞는 요일부터 회차가 생긴다.", example = "2026-09-05")
        @NotNull LocalDate startDate,

        @Schema(description = "규칙 종료일. 생략하면 종료일 없음(배치가 계속 이어 만든다).", example = "2026-12-31")
        LocalDate endDate,

        @Schema(description = "회차마다 복사되는 비용(원). 참고용. 생략 가능.", example = "30000")
        @PositiveOrZero Integer cost,

        @Schema(description = "회차마다 복사되는 메모. 최대 500자. 생략 가능.", example = "정기 합주 · 예약자 홍길동")
        @Size(max = 500) String note
) {
}
