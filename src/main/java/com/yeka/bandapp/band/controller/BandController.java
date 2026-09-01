package com.yeka.bandapp.band.controller;

import com.yeka.bandapp.band.dto.BandResponse;
import com.yeka.bandapp.band.dto.CreateBandRequest;
import com.yeka.bandapp.band.dto.DelegateLeadershipRequest;
import com.yeka.bandapp.band.dto.MyBandListResponse;
import com.yeka.bandapp.band.dto.UpdateBandSettingsRequest;
import com.yeka.bandapp.band.service.BandMemberService;
import com.yeka.bandapp.band.service.BandService;
import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "3. 밴드", description = "밴드 생성/조회, 내 밴드 목록, 일정 등록 권한 설정, 밴드장 위임.")
@RestController
@RequestMapping("/api/v1/bands")
public class BandController {

    private final BandService bandService;
    private final BandMemberService bandMemberService;

    public BandController(BandService bandService, BandMemberService bandMemberService) {
        this.bandService = bandService;
        this.bandMemberService = bandMemberService;
    }

    @Operation(summary = "밴드 생성",
            description = "밴드를 만들고(201) 만든 사람이 자동으로 밴드장(LEADER)이 된다. "
                    + "일정 등록 권한 기본값은 LEADER_ONLY.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BandResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                            @Valid @RequestBody CreateBandRequest request) {
        return ApiResponse.ok(bandService.create(principal.userId(), request));
    }

    @Operation(summary = "내가 속한 밴드 목록",
            description = "활성 멤버로 속한 밴드를 가입순으로 반환한다(밴드별 내 역할, 멤버 수 포함). "
                    + "탈퇴/추방된 밴드는 빠진다. 클라이언트의 밴드 전환 스위처용.")
    @GetMapping
    public ApiResponse<MyBandListResponse> listMine(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(bandService.listMine(principal.userId()));
    }

    @Operation(summary = "밴드 조회",
            description = "밴드 기본 정보. 그 밴드의 활성 멤버만 볼 수 있다 — 비멤버는 밴드 존재 여부와 무관하게 "
                    + "403 NOT_BAND_MEMBER.")
    @GetMapping("/{bandId}")
    public ApiResponse<BandResponse> get(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable long bandId) {
        return ApiResponse.ok(bandService.get(bandId, principal.userId()));
    }

    @Operation(summary = "일정 등록 권한 모드 변경",
            description = "reservationPermission을 LEADER_ONLY / ANYONE / APPROVAL_REQUIRED 중 하나로 바꾼다. "
                    + "밴드장만 가능(그 외 403 NOT_BAND_LEADER). PATCH 미지원 클라이언트 대비로 PUT.")
    @PutMapping("/{bandId}/settings")
    public ApiResponse<BandResponse> updateSettings(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @PathVariable long bandId,
                                                    @Valid @RequestBody UpdateBandSettingsRequest request) {
        return ApiResponse.ok(bandService.updateSettings(bandId, principal.userId(), request));
    }

    @Operation(summary = "밴드장 위임",
            description = "현재 밴드장을 MEMBER로 강등하고 대상(newLeaderUserId)을 LEADER로 승격한다(한 트랜잭션). "
                    + "현재 밴드장만 호출 가능. 자기 자신 대상은 400 CANNOT_DELEGATE_TO_SELF, "
                    + "대상이 그 밴드 멤버가 아니면 404 MEMBER_NOT_FOUND.")
    @PostMapping("/{bandId}/leader")
    public ApiResponse<BandResponse> delegateLeadership(@AuthenticationPrincipal AuthPrincipal principal,
                                                        @PathVariable long bandId,
                                                        @Valid @RequestBody DelegateLeadershipRequest request) {
        return ApiResponse.ok(bandMemberService.delegateLeadership(
                bandId, principal.userId(), request.newLeaderUserId()));
    }
}
