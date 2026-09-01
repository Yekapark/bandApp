package com.yeka.bandapp.band.controller;

import com.yeka.bandapp.band.dto.BandResponse;
import com.yeka.bandapp.band.dto.InviteResponse;
import com.yeka.bandapp.band.dto.IssueInviteRequest;
import com.yeka.bandapp.band.dto.JoinBandRequest;
import com.yeka.bandapp.band.service.BandInviteService;
import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.common.web.ClientIp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "4. 초대", description = "밴드 초대코드 발급/재발급/무효화, 코드로 밴드 참여.")
@RestController
@RequestMapping("/api/v1/bands")
public class BandInviteController {

    private final BandInviteService bandInviteService;

    public BandInviteController(BandInviteService bandInviteService) {
        this.bandInviteService = bandInviteService;
    }

    @Operation(summary = "초대코드 발급/재발급",
            description = "8자 코드와 공유용 링크를 발급한다(201). 본문은 선택 — 생략하면 만료 7일, 사용 횟수 무제한. "
                    + "재발급하면 그 밴드의 기존 활성 코드는 즉시 무효화된다. 밴드장만 가능(그 외 403 NOT_BAND_LEADER).")
    @PostMapping("/{bandId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InviteResponse> issue(@AuthenticationPrincipal AuthPrincipal principal,
                                             @PathVariable long bandId,
                                             @Valid @RequestBody(required = false) IssueInviteRequest request) {
        return ApiResponse.ok(bandInviteService.issue(bandId, principal.userId(), request));
    }

    @Operation(summary = "현재 활성 초대코드 조회",
            description = "그 밴드의 유효한 코드 하나를 반환한다. 없으면 404 INVITE_NOT_FOUND. 밴드장만 가능.")
    @GetMapping("/{bandId}/invites/current")
    public ApiResponse<InviteResponse> current(@AuthenticationPrincipal AuthPrincipal principal,
                                               @PathVariable long bandId) {
        return ApiResponse.ok(bandInviteService.current(bandId, principal.userId()));
    }

    @Operation(summary = "현재 초대코드 무효화",
            description = "그 밴드의 활성 코드를 모두 무효화한다(204). 이미 없어도 204(멱등). 밴드장만 가능.")
    @DeleteMapping("/{bandId}/invites/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeCurrent(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable long bandId) {
        bandInviteService.revokeCurrent(bandId, principal.userId());
    }

    @Operation(summary = "초대코드로 밴드 참여",
            description = "코드를 넣어 그 밴드의 멤버가 된다(200, 응답은 밴드 정보). "
                    + "거부 사유가 각각 다르다: 없는 코드 404 INVITE_NOT_FOUND / 무효화 410 INVITE_REVOKED / "
                    + "만료 410 INVITE_EXPIRED / 사용 횟수 소진 409 INVITE_EXHAUSTED / 이미 그 밴드 멤버 409 ALREADY_BAND_MEMBER. "
                    + "계정당 분당 10회, IP당 분당 20회 제한(초과 429).")
    @PostMapping("/join")
    public ApiResponse<BandResponse> join(@AuthenticationPrincipal AuthPrincipal principal,
                                          @Valid @RequestBody JoinBandRequest request,
                                          HttpServletRequest httpRequest) {
        return ApiResponse.ok(bandInviteService.join(
                principal.userId(), request.code(), ClientIp.of(httpRequest)));
    }
}
