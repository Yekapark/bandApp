package com.yeka.bandapp.recurring.controller;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.recurring.dto.CreateRecurringRuleRequest;
import com.yeka.bandapp.recurring.dto.RecurringRuleDetailResponse;
import com.yeka.bandapp.recurring.dto.RecurringRuleListResponse;
import com.yeka.bandapp.recurring.dto.RecurringRuleWriteResponse;
import com.yeka.bandapp.recurring.service.RecurringRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정기 합주 규칙. Bearer 인증 필요, 모든 엔드포인트가 밴드 멤버십을 검증한다.
 *
 * <p>규칙을 등록하면 앞으로 8주분 회차가 {@code Reservation}으로 만들어진다. <b>개별 회차의 수정·취소는
 * 일반 일정 API({@code /reservations/{id}})를 그대로 쓴다</b> — 규칙은 유지된다. 규칙 <b>수정</b>은
 * 제공하지 않으며(삭제 후 재등록), 시간대 겹침은 등록을 막지 않고 응답 {@code overlaps}에만 싣는다.
 */
@Tag(name = "8. 정기 일정",
        description = "매주/격주/매월 반복되는 합주 규칙을 등록하면 앞으로의 회차가 일정으로 자동 생성된다. "
                + "회차는 등록 시 CONFIRMED 로 시작하고, 개별 회차 수정·취소는 일반 일정 API 로 한다. "
                + "규칙 등록 권한은 밴드의 reservationPermission 을 따르되 ANYONE 만 일반 멤버가 가능하고 "
                + "LEADER_ONLY·APPROVAL_REQUIRED 는 밴드장 전용이다. 규칙 삭제 시 미래 회차만 취소되고 과거 회차는 남는다.")
@RestController
@RequestMapping("/api/v1/bands/{bandId}/recurring-rules")
public class RecurringRuleController {

    private final RecurringRuleService recurringRuleService;

    public RecurringRuleController(RecurringRuleService recurringRuleService) {
        this.recurringRuleService = recurringRuleService;
    }

    @Operation(summary = "정기 일정 규칙 등록",
            description = "roomId·frequency·dayOfWeek·startTime·endTime·startDate 필수. endTime 은 startTime 보다 "
                    + "뒤여야 하고(400 INVALID_RECURRING_TIME), endDate 는 startDate 이상이어야 한다(400 "
                    + "INVALID_RECURRING_DATE_RANGE). ANYONE 이 아닌 밴드에서 일반 멤버가 등록하면 403 NOT_BAND_LEADER. "
                    + "다른 밴드/삭제된 roomId 면 404 ROOM_NOT_FOUND. 겹치는 일정이 있어도 201이며 overlaps 에 담긴다. "
                    + "회차는 오늘 ± horizonWeeks(기본 8주) 구간에서만 생성되므로 startDate 를 과거로 멀리 잡아도 "
                    + "대량 백필되지 않는다. 생성된 회차 수만큼 해당 합주실 usageCount 가 증가한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RecurringRuleWriteResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                                          @PathVariable long bandId,
                                                          @Valid @RequestBody CreateRecurringRuleRequest request) {
        return ApiResponse.ok(recurringRuleService.create(bandId, principal.userId(), request));
    }

    @Operation(summary = "정기 일정 규칙 목록",
            description = "밴드의 활성 규칙을 최신 등록순으로. 그 밴드 멤버만(비멤버 403 NOT_BAND_MEMBER).")
    @GetMapping
    public ApiResponse<RecurringRuleListResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                                       @PathVariable long bandId) {
        return ApiResponse.ok(recurringRuleService.list(bandId, principal.userId()));
    }

    @Operation(summary = "정기 일정 규칙 상세",
            description = "규칙과 최근 구간(오늘 − horizonWeeks 이후)의 회차(취소분 포함, 시작 시각 오름차순). "
                    + "그 이전 회차는 GET .../reservations?from=&to= 캘린더 API 로 조회한다. 다른 밴드/삭제된 "
                    + "ruleId 면 404 RECURRING_RULE_NOT_FOUND.")
    @GetMapping("/{ruleId}")
    public ApiResponse<RecurringRuleDetailResponse> get(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @PathVariable long bandId,
                                                        @PathVariable long ruleId) {
        return ApiResponse.ok(recurringRuleService.get(bandId, ruleId, principal.userId()));
    }

    @Operation(summary = "정기 일정 규칙 삭제",
            description = "규칙을 소프트 삭제하고 아직 시작하지 않은 회차만 CANCELLED 로 바꾼다(과거 회차는 유지). "
                    + "등록자 본인 또는 밴드장만(그 외 403 NOT_RECURRING_RULE_OWNER). 취소된 회차 수만큼 합주실 "
                    + "usageCount 가 감소한다. 이미 삭제된 규칙이면 404.")
    @DeleteMapping("/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable long bandId,
                       @PathVariable long ruleId) {
        recurringRuleService.delete(bandId, ruleId, principal.userId());
    }
}
