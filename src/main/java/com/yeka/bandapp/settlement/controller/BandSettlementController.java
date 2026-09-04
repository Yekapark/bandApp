package com.yeka.bandapp.settlement.controller;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.settlement.dto.BandSettlementListResponse;
import com.yeka.bandapp.settlement.service.BandSettlementListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 밴드 단위 정산 목록(읽기 전용). 정산 생성·재계산·납부 체크는 일정 아래의
 * {@code /reservations/{id}/settlement} 가 맡는다 — 정산은 일정에 매달린 개념이라 그대로 둔다.
 */
@Tag(name = "9. 정산", description = "밴드의 정산 목록 — 일정을 하나씩 열어보지 않고 내 미납을 한눈에 본다.")
@RestController
@RequestMapping("/api/v1/bands/{bandId}/settlements")
public class BandSettlementController {

    private final BandSettlementListService listService;

    public BandSettlementController(BandSettlementListService listService) {
        this.listService = listService;
    }

    @Operation(summary = "밴드 정산 목록",
            description = "그 밴드의 정산을 최신순(정산 등록 역순)으로 반환한다. 각 건에 합주 일시·합주실·총액과 "
                    + "함께 내 몫·납부 여부가 담기고, myOutstandingTotal 은 이 목록에서 내가 아직 안 낸 금액의 합이다. "
                    + "다음 페이지는 nextCursor 를 cursor 로 다시 보낸다(null 이면 마지막). size 는 최대 50, 기본 20. "
                    + "그 밴드 멤버만(비멤버 403 NOT_BAND_MEMBER).")
    @GetMapping
    public ApiResponse<BandSettlementListResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @PathVariable long bandId,
                                                        @RequestParam(required = false) Long cursor,
                                                        @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(listService.list(bandId, principal.userId(), cursor, size));
    }
}
