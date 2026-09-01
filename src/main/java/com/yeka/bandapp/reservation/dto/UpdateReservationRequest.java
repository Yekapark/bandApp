package com.yeka.bandapp.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 일정 수정 요청. {@code PUT} 전체 교체다({@code RoomController}와 같은 방침) — 보내지 않은 선택 필드는
 * 비워진다. 등록자 본인 또는 밴드장만 호출할 수 있다.
 *
 * <p>{@code APPROVAL_REQUIRED} 밴드에서 이미 확정된 일정의 <b>시간 또는 합주실</b>이 바뀌면 다시
 * 승인 대기({@code PENDING})로 돌아간다. 비고·비용만 바뀌면 확정 상태를 유지한다.
 */
public record UpdateReservationRequest(
        @Schema(description = "합주실 id. 이 밴드의 삭제되지 않은 합주실이어야 한다.", example = "1")
        @NotNull Long roomId,

        @Schema(description = "시작 시각(UTC ISO-8601).", example = "2026-09-10T10:00:00Z")
        @NotNull Instant startAt,

        @Schema(description = "종료 시각(UTC ISO-8601). 시작 시각보다 뒤여야 한다.", example = "2026-09-10T13:00:00Z")
        @NotNull Instant endAt,

        @Schema(description = "합주실 비용(원). 생략 시 비워진다.", example = "30000")
        @PositiveOrZero Integer cost,

        @Schema(description = "자유 기재. 생략 시 비워진다. 최대 500자.", example = "장소 변경: 사운드박스 B로")
        @Size(max = 500) String note
) {
}
