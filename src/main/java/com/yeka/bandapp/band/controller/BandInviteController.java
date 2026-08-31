package com.yeka.bandapp.band.controller;

import com.yeka.bandapp.band.dto.BandResponse;
import com.yeka.bandapp.band.dto.InviteResponse;
import com.yeka.bandapp.band.dto.IssueInviteRequest;
import com.yeka.bandapp.band.dto.JoinBandRequest;
import com.yeka.bandapp.band.service.BandInviteService;
import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.common.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
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

/** 초대코드 발급/조회/무효화(밴드장) + 코드로 참여(모든 인증 사용자). Bearer 인증 필요. */
@RestController
@RequestMapping("/api/v1/bands")
public class BandInviteController {

    private final BandInviteService bandInviteService;

    public BandInviteController(BandInviteService bandInviteService) {
        this.bandInviteService = bandInviteService;
    }

    /** 발급/재발급. 본문은 선택({@code maxUses}, {@code ttlDays}). 기존 활성 코드는 무효화된다. */
    @PostMapping("/{bandId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InviteResponse> issue(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable long bandId,
                                             @Valid @RequestBody(required = false) IssueInviteRequest request) {
        return ApiResponse.ok(bandInviteService.issue(bandId, principal.userId(), request));
    }

    @GetMapping("/{bandId}/invites/current")
    public ApiResponse<InviteResponse> current(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable long bandId) {
        return ApiResponse.ok(bandInviteService.current(bandId, principal.userId()));
    }

    @DeleteMapping("/{bandId}/invites/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeCurrent(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long bandId) {
        bandInviteService.revokeCurrent(bandId, principal.userId());
    }

    /** 초대코드로 밴드 참여. 계정·IP 기준 분당 레이트리밋. */
    @PostMapping("/join")
    public ApiResponse<BandResponse> join(@AuthenticationPrincipal AuthPrincipal principal,
                                          @Valid @RequestBody JoinBandRequest request,
                                          HttpServletRequest httpRequest) {
        return ApiResponse.ok(bandInviteService.join(
                principal.userId(), request.code(), ClientIp.of(httpRequest)));
    }
}
