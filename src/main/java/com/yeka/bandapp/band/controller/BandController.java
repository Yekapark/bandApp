package com.yeka.bandapp.band.controller;

import com.yeka.bandapp.band.dto.BandResponse;
import com.yeka.bandapp.band.dto.CreateBandRequest;
import com.yeka.bandapp.band.dto.DelegateLeadershipRequest;
import com.yeka.bandapp.band.dto.UpdateBandSettingsRequest;
import com.yeka.bandapp.band.service.BandMemberService;
import com.yeka.bandapp.band.service.BandService;
import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 밴드 생성·조회·설정·밴드장 위임. Bearer 인증 필요. */
@RestController
@RequestMapping("/api/v1/bands")
public class BandController {

    private final BandService bandService;
    private final BandMemberService bandMemberService;

    public BandController(BandService bandService, BandMemberService bandMemberService) {
        this.bandService = bandService;
        this.bandMemberService = bandMemberService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BandResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                            @Valid @RequestBody CreateBandRequest request) {
        return ApiResponse.ok(bandService.create(principal.userId(), request));
    }

    @GetMapping("/{bandId}")
    public ApiResponse<BandResponse> get(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable long bandId) {
        return ApiResponse.ok(bandService.get(bandId, principal.userId()));
    }

    /** 일정 등록 권한 모드 변경. 밴드장만 가능(그 외 403). {@code PATCH} 미지원 클라이언트 대비 {@code PUT}. */
    @PutMapping("/{bandId}/settings")
    public ApiResponse<BandResponse> updateSettings(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @PathVariable long bandId,
                                                    @Valid @RequestBody UpdateBandSettingsRequest request) {
        return ApiResponse.ok(bandService.updateSettings(bandId, principal.userId(), request));
    }

    /** 밴드장 위임. 기존 밴드장 → MEMBER, 대상 → LEADER (원자적). 밴드장만 호출 가능. */
    @PostMapping("/{bandId}/leader")
    public ApiResponse<BandResponse> delegateLeadership(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @PathVariable long bandId,
                                                        @Valid @RequestBody DelegateLeadershipRequest request) {
        return ApiResponse.ok(bandMemberService.delegateLeadership(
                bandId, principal.userId(), request.newLeaderUserId()));
    }
}
