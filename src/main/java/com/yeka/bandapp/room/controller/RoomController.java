package com.yeka.bandapp.room.controller;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.room.dto.CreateRoomRequest;
import com.yeka.bandapp.room.dto.RoomListResponse;
import com.yeka.bandapp.room.dto.RoomResponse;
import com.yeka.bandapp.room.dto.UpdateRoomRequest;
import com.yeka.bandapp.room.service.RoomService;
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
 * 밴드별 합주실 CRUD. Bearer 인증 필요, 모든 엔드포인트가 밴드 멤버십을 검증한다.
 * {@code PATCH} 미지원 클라이언트를 위해 수정은 {@code PUT}(전체 교체)이다.
 */
@RestController
@RequestMapping("/api/v1/bands/{bandId}/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoomResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable long bandId,
                                            @Valid @RequestBody CreateRoomRequest request) {
        return ApiResponse.ok(roomService.create(bandId, principal.userId(), request));
    }

    @GetMapping
    public ApiResponse<RoomListResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable long bandId) {
        return ApiResponse.ok(roomService.list(bandId, principal.userId()));
    }

    @GetMapping("/{roomId}")
    public ApiResponse<RoomResponse> get(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable long bandId,
                                         @PathVariable long roomId) {
        return ApiResponse.ok(roomService.get(bandId, roomId, principal.userId()));
    }

    @PutMapping("/{roomId}")
    public ApiResponse<RoomResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable long bandId,
                                            @PathVariable long roomId,
                                            @Valid @RequestBody UpdateRoomRequest request) {
        return ApiResponse.ok(roomService.update(bandId, roomId, principal.userId(), request));
    }

    @DeleteMapping("/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable long bandId,
                       @PathVariable long roomId) {
        roomService.delete(bandId, roomId, principal.userId());
    }
}
