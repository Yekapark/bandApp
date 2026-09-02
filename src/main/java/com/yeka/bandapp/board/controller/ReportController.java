package com.yeka.bandapp.board.controller;

import com.yeka.bandapp.board.dto.CreateReportRequest;
import com.yeka.bandapp.board.dto.ReportResponse;
import com.yeka.bandapp.board.service.ReportService;
import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시글·미디어·사용자 신고 접수. 밴드와 무관한 전역 엔드포인트다(Report 에 bandId 필드가 없다).
 * POST·MEDIA 신고는 요청자가 그 콘텐츠가 있는 밴드의 멤버여야 하며, 아니면 존재를 알리지 않고
 * 404 REPORT_TARGET_NOT_FOUND.
 */
@Tag(name = "14. 신고",
        description = "부적절한 게시글/미디어/사용자를 신고한다. 접수만 하며(status=OPEN) 처리는 운영자 몫이다. "
                + "자기 자신·자기 글은 400 CANNOT_REPORT_SELF, 같은 대상 중복 접수는 409 REPORT_ALREADY_SUBMITTED.")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "신고 접수",
            description = "targetType(POST|MEDIA|USER)·targetId·reason 필수. 대상이 안 보이면 404 "
                    + "REPORT_TARGET_NOT_FOUND, 자기 대상이면 400 CANNOT_REPORT_SELF, 이미 미처리 신고가 있으면 "
                    + "409 REPORT_ALREADY_SUBMITTED, 과다 요청은 429.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportResponse> report(@AuthenticationPrincipal AuthPrincipal principal,
                                              @Valid @RequestBody CreateReportRequest request) {
        return ApiResponse.ok(reportService.report(principal.userId(), request));
    }
}
