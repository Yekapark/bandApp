package com.yeka.bandapp.band.controller;

import com.yeka.bandapp.band.dto.BandResponse;
import com.yeka.bandapp.band.dto.CreateBandRequest;
import com.yeka.bandapp.band.dto.DelegateLeadershipRequest;
import com.yeka.bandapp.band.dto.DeleteBandRequest;
import com.yeka.bandapp.band.dto.MyBandListResponse;
import com.yeka.bandapp.band.dto.UpdateBandSettingsRequest;
import com.yeka.bandapp.band.service.BandDeletionService;
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
    private final BandDeletionService bandDeletionService;

    public BandController(BandService bandService, BandMemberService bandMemberService,
                          BandDeletionService bandDeletionService) {
        this.bandService = bandService;
        this.bandMemberService = bandMemberService;
        this.bandDeletionService = bandDeletionService;
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

    @Operation(summary = "밴드 삭제",
            description = "밴드와 그 안의 모든 데이터를 되돌릴 수 없게 지운다 — 합주 일정, 정산 내역, "
                    + "게시글과 첨부한 사진·영상(R2 객체 포함), 합주실, 셋리스트, 정기 규칙, 초대코드, "
                    + "요금제, 알림 이력. 밴드장만(그 외 403 NOT_BAND_LEADER). "
                    + "오입력 방지를 위해 body 의 confirmName 이 실제 밴드 이름과 정확히 같아야 한다"
                    + "(다르면 400 BAND_NAME_MISMATCH). "
                    + "저장소 삭제가 실패하면 502 이고 아무것도 지워지지 않는다 — 다시 시도하면 된다. "
                    + "사람↔사람 차단 기록과 사용자 계정·알림 설정은 밴드와 무관하므로 남는다. "
                    + "DELETE 가 아니라 POST 인 이유: 확인용 본문이 필요한데 본문 있는 DELETE 는 "
                    + "클라이언트·프록시 지원이 고르지 않다. 계정 탈퇴(POST /users/me/withdraw)와 같은 형태다.")
    @PostMapping("/{bandId}/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable long bandId,
                       @Valid @RequestBody DeleteBandRequest request) {
        bandDeletionService.delete(bandId, principal.userId(), request.confirmName());
    }
}
