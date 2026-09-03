package com.yeka.bandapp.room.controller;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.security.AuthPrincipal;
import com.yeka.bandapp.room.dto.CreateRoomRequest;
import com.yeka.bandapp.room.dto.PlaceSearchResponse;
import com.yeka.bandapp.room.dto.RoomListResponse;
import com.yeka.bandapp.room.dto.RoomResponse;
import com.yeka.bandapp.room.dto.UpdateRoomRequest;
import com.yeka.bandapp.room.service.RoomService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 밴드별 합주실 CRUD. Bearer 인증 필요, 모든 엔드포인트가 밴드 멤버십을 검증한다.
 * {@code PATCH} 미지원 클라이언트를 위해 수정은 {@code PUT}(전체 교체)이다.
 */
@Tag(name = "6. 합주실",
        description = "밴드별 합주실(주소록) 등록·조회·수정·삭제. 등록/수정/삭제는 밴드 멤버 누구나 가능. "
                + "주소를 주면 네이버 지오코딩으로 좌표를 채운다(서버에 키가 없으면 lat/lng는 null).")
@RestController
@RequestMapping("/api/v1/bands/{bandId}/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @Operation(summary = "합주실 등록",
            description = "이름만 필수. 주소가 있으면 좌표 변환을 시도하되 실패해도 등록은 성공한다(201, lat/lng는 null). "
                    + "같은 밴드에 같은 이름이 있으면 409 ROOM_NAME_DUPLICATED. 그 밴드 멤버만.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoomResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable long bandId,
                                            @Valid @RequestBody CreateRoomRequest request) {
        return ApiResponse.ok(roomService.create(bandId, principal.userId(), request));
    }

    @Operation(summary = "합주실 목록",
            description = "그 밴드의 삭제되지 않은 합주실을 usageCount(사용 횟수) 내림차순으로 반환한다. 그 밴드 멤버만.")
    @GetMapping
    public ApiResponse<RoomListResponse> list(@AuthenticationPrincipal AuthPrincipal principal,
                                              @PathVariable long bandId) {
        return ApiResponse.ok(roomService.list(bandId, principal.userId()));
    }

    @Operation(summary = "합주실 주소 검색",
            description = "네이버 지역검색으로 합주실 이름·주소 후보를 최대 5건 반환한다. 등록 폼에서 검색해 자동 입력하는 용도. "
                    + "검색어가 비었거나 서버에 검색 키가 없으면 빈 목록(places=[])을 준다 — 이때도 200이다. "
                    + "그 밴드 멤버만(비멤버 403 NOT_BAND_MEMBER). 계정당 분당 호출 상한이 있어 초과 시 429.")
    @GetMapping("/search")
    public ApiResponse<PlaceSearchResponse> search(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable long bandId,
                                                   @RequestParam(defaultValue = "") String query) {
        return ApiResponse.ok(roomService.searchPlaces(bandId, principal.userId(), query));
    }

    @Operation(summary = "합주실 상세",
            description = "다른 밴드의 roomId를 넣으면 존재 여부와 무관하게 404 ROOM_NOT_FOUND.")
    @GetMapping("/{roomId}")
    public ApiResponse<RoomResponse> get(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable long bandId,
                                         @PathVariable long roomId) {
        return ApiResponse.ok(roomService.get(bandId, roomId, principal.userId()));
    }

    @Operation(summary = "합주실 수정",
            description = "PUT 전체 교체 — 보내지 않은 필드는 비워진다. 주소가 실제로 바뀐 경우에만 좌표를 다시 계산한다"
                    + "(못 얻으면 null로 비움). 이름 충돌 409 ROOM_NAME_DUPLICATED, 삭제된 방 404 ROOM_NOT_FOUND.")
    @PutMapping("/{roomId}")
    public ApiResponse<RoomResponse> update(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable long bandId,
                                            @PathVariable long roomId,
                                            @Valid @RequestBody UpdateRoomRequest request) {
        return ApiResponse.ok(roomService.update(bandId, roomId, principal.userId(), request));
    }

    @Operation(summary = "합주실 삭제",
            description = "소프트 삭제(204). 행은 남아 과거 일정이 계속 참조할 수 있고, 목록·조회에서는 빠진다. "
                    + "삭제한 이름은 다시 등록에 쓸 수 있다.")
    @DeleteMapping("/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal,
                       @PathVariable long bandId,
                       @PathVariable long roomId) {
        roomService.delete(bandId, roomId, principal.userId());
    }
}
