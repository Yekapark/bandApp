package com.yeka.bandapp.board.controller;

import com.yeka.bandapp.board.dto.BlockListResponse;
import com.yeka.bandapp.board.dto.BlockResponse;
import com.yeka.bandapp.board.dto.CreateBlockRequest;
import com.yeka.bandapp.board.service.UserBlockService;
import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
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
 * 전역(밴드 무관) 사용자 차단. 차단하면 게시판 목록·상세에서 그 사용자의 글이 <b>양방향</b>으로 빠진다
 * (내가 차단한 사람 + 나를 차단한 사람 모두).
 */
@Tag(name = "15. 사용자 차단",
        description = "다른 사용자를 차단/해제한다. 차단은 밴드와 무관한 전역 설정이며, 게시판에서 서로의 글이 "
                + "보이지 않게 된다. 자기 차단은 400 CANNOT_BLOCK_SELF, 중복은 409 ALREADY_BLOCKED.")
@RestController
@RequestMapping("/api/v1/users/me/blocks")
public class UserBlockController {

    private final UserBlockService userBlockService;

    public UserBlockController(UserBlockService userBlockService) {
        this.userBlockService = userBlockService;
    }

    @Operation(summary = "사용자 차단",
            description = "blockedUserId 필수. 없는 사용자면 404 USER_NOT_FOUND, 자기 자신이면 400 CANNOT_BLOCK_SELF, "
                    + "이미 차단했으면 409 ALREADY_BLOCKED.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BlockResponse> block(@AuthenticationPrincipal AuthPrincipal principal,
                                            @Valid @RequestBody CreateBlockRequest request) {
        return ApiResponse.ok(userBlockService.block(principal.userId(), request));
    }

    @Operation(summary = "차단 목록", description = "내가 차단한 사용자를 최근순으로 반환한다.")
    @GetMapping
    public ApiResponse<BlockListResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(userBlockService.list(principal.userId()));
    }

    @Operation(summary = "차단 해제",
            description = "차단하지 않은 사용자면 404 BLOCK_NOT_FOUND. 성공 시 204.")
    @DeleteMapping("/{blockedUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@AuthenticationPrincipal AuthPrincipal principal,
                        @PathVariable long blockedUserId) {
        userBlockService.unblock(principal.userId(), blockedUserId);
    }
}
