package com.yeka.bandapp.board.dto;

import com.yeka.bandapp.board.entity.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 신고 접수 요청. 대상 종류·id 와 사유를 받는다. */
public record CreateReportRequest(
        @Schema(description = "신고 대상 종류.", example = "POST")
        @NotNull ReportTargetType targetType,

        @Schema(description = "대상 id. POST=게시글 id, MEDIA=첨부 id, USER=사용자 id.", example = "42")
        @NotNull @Positive Long targetId,

        @Schema(description = "신고 사유. 1~500자.", example = "부적절한 사진")
        @NotBlank @Size(max = 500) String reason
) {
}
