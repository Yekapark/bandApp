package com.yeka.bandapp.band.controller;

import com.yeka.bandapp.band.dto.BandMemberListResponse;
import com.yeka.bandapp.band.service.BandMemberService;
import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
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
@RestController
@RequestMapping("/api/v1/bands/{bandId}/members")
public class BandMemberController {

    private final BandMemberService bandMemberService;

    public BandMemberController(BandMemberService bandMemberService) {
        this.bandMemberService = bandMemberService;
    }

    @GetMapping
    public ApiResponse<BandMemberListResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @PathVariable long bandId) {
        return ApiResponse.ok(bandMemberService.list(bandId, principal.userId()));
    }

    /** 자발적 탈퇴. 밴드장은 위임 전에는 409. */
    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long bandId) {
        bandMemberService.leave(bandId, principal.userId());
    }

    /** 밴드장의 멤버 추방. 밴드장만 가능(그 외 403). */
    @DeleteMapping("/{targetUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kick(@AuthenticationPrincipal AuthPrincipal principal,
                     @PathVariable long bandId,
                     @PathVariable long targetUserId) {
        bandMemberService.kick(bandId, principal.userId(), targetUserId);
    }
}
