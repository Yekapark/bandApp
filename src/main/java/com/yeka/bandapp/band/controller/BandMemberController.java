package com.yeka.bandapp.band.controller;

import com.yeka.bandapp.band.dto.BandMemberListResponse;
import com.yeka.bandapp.band.service.BandMemberService;
import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 밴드 멤버 목록·탈퇴·추방. Bearer 인증 필요. */
@Tag(name = "5. 밴드 멤버", description = "멤버 목록 조회, 자발적 탈퇴, 밴드장의 멤버 추방.")
@RestController
@RequestMapping("/api/v1/bands/{bandId}/members")
public class BandMemberController {

    private final BandMemberService bandMemberService;

    public BandMemberController(BandMemberService bandMemberService) {
        this.bandMemberService = bandMemberService;
    }

    @Operation(summary = "멤버 목록",
            description = "활성 멤버를 가입순으로(역할 LEADER/MEMBER, 이름 포함) 반환한다. 그 밴드 멤버만 조회 가능.")
    @GetMapping
    public ApiResponse<BandMemberListResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @PathVariable long bandId) {
        return ApiResponse.ok(bandMemberService.list(bandId, principal.userId()));
    }

    @Operation(summary = "자발적 탈퇴",
            description = "내가 이 밴드에서 나간다(204). 밴드장은 먼저 위임해야 하며, 위임 없이 호출하면 "
                    + "409 LEADER_MUST_DELEGATE_BEFORE_LEAVING.")
    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long bandId) {
        bandMemberService.leave(bandId, principal.userId());
    }

    @Operation(summary = "멤버 추방",
            description = "밴드장이 다른 멤버를 내보낸다(204). 밴드장만 가능(그 외 403 NOT_BAND_LEADER), "
                    + "자기 자신 대상은 400 CANNOT_KICK_SELF.")
    @DeleteMapping("/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kick(@AuthenticationPrincipal AuthPrincipal principal,
                     @PathVariable long bandId,
                     @Parameter(description = "추방할 멤버의 user id (GET /api/v1/users/me 로 확인)")
                     @PathVariable long targetUserId) {
        bandMemberService.kick(bandId, principal.userId(), targetUserId);
    }
}
