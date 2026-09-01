package com.yeka.bandapp.reservation.controller;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.reservation.dto.CreateSetlistItemRequest;
import com.yeka.bandapp.reservation.dto.ReorderSetlistRequest;
import com.yeka.bandapp.reservation.dto.SetlistItemResponse;
import com.yeka.bandapp.reservation.dto.SetlistResponse;
import com.yeka.bandapp.reservation.dto.UpdateSetlistItemRequest;
import com.yeka.bandapp.reservation.service.SetlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 일정별 셋리스트 CRUD. Bearer 인증 필요, 모든 엔드포인트가 밴드 멤버십을 검증한다.
 * 밴드 멤버 누구나 편집할 수 있다(등록자 제한 없음).
 */
@Tag(name = "10. 셋리스트",
        description = "일정별 연주 곡 목록(곡명·아티스트·참고 링크·순서). 밴드 멤버 누구나 추가·수정·삭제·재정렬할 수 있다. "
                + "새 곡은 맨 뒤에 붙고, 순서는 재정렬 API로 1..N을 다시 매긴다.")
@RestController
@RequestMapping("/api/v1/bands/{bandId}/reservations/{reservationId}/setlist")
public class SetlistController {

    private final SetlistService setlistService;

    public SetlistController(SetlistService setlistService) {
        this.setlistService = setlistService;
    }

    @Operation(summary = "셋리스트 조회", description = "orderNo 오름차순. 그 밴드 멤버만(비멤버 403).")
    @GetMapping
    public ApiResponse<SetlistResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable long bandId,
                                             @PathVariable long reservationId) {
        return ApiResponse.ok(setlistService.list(bandId, reservationId, principal.userId()));
    }

    @Operation(summary = "곡 추가",
            description = "title 필수(최대 200자). artist·referenceUrl 선택. 목록 맨 뒤에 추가된다(orderNo = 현재 최대 + 1).")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SetlistItemResponse> add(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable long bandId,
                                                @PathVariable long reservationId,
                                                @Valid @RequestBody CreateSetlistItemRequest request) {
        return ApiResponse.ok(setlistService.add(bandId, reservationId, principal.userId(), request));
    }

    @Operation(summary = "셋리스트 재정렬",
            description = "itemIds에 그 일정의 모든 셋리스트 항목 id를 원하는 순서대로 넣는다. 빠지거나 남거나 "
                    + "중복이면 400 SETLIST_REORDER_MISMATCH. 그 순서대로 orderNo가 1..N으로 다시 매겨진다.")
    @PutMapping("/reorder")
    public ApiResponse<SetlistResponse> reorder(@AuthenticationPrincipal AuthPrincipal principal,
                                                @PathVariable long bandId,
                                                @PathVariable long reservationId,
                                                @Valid @RequestBody ReorderSetlistRequest request) {
        return ApiResponse.ok(setlistService.reorder(bandId, reservationId, principal.userId(), request));
    }

    @Operation(summary = "곡 수정",
            description = "PUT 전체 교체 — 보내지 않은 선택 필드는 비워진다. 다른 일정의 itemId면 404 "
                    + "SETLIST_ITEM_NOT_FOUND. 순서는 이 API로 바꾸지 않는다.")
    @PutMapping("/{itemId}")
    public ApiResponse<SetlistItemResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable long bandId,
                                                   @PathVariable long reservationId,
                                                   @PathVariable long itemId,
                                                   @Valid @RequestBody UpdateSetlistItemRequest request) {
        return ApiResponse.ok(setlistService.update(bandId, reservationId, itemId, principal.userId(), request));
    }

    @Operation(summary = "곡 삭제",
            description = "다른 일정의 itemId면 404 SETLIST_ITEM_NOT_FOUND. 남은 항목의 orderNo는 그대로다"
                    + "(연속성이 필요하면 재정렬 API 호출).")
    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable long bandId,
                       @PathVariable long reservationId,
                       @PathVariable long itemId) {
        setlistService.delete(bandId, reservationId, itemId, principal.userId());
    }
}
