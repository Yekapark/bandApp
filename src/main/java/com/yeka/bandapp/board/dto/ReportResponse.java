package com.yeka.bandapp.board.dto;

import com.yeka.bandapp.board.entity.Report;
import com.yeka.bandapp.board.entity.ReportStatus;
import com.yeka.bandapp.board.entity.ReportTargetType;

import java.time.Instant;

/** 신고 접수 결과. 접수 시 항상 {@code status=OPEN}. */
public record ReportResponse(
        Long id,
        ReportTargetType targetType,
        Long targetId,
        ReportStatus status,
        Instant createdAt
) {
    public static ReportResponse from(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getStatus(),
                report.getCreatedAt());
    }
}
