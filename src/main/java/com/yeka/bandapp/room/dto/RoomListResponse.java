package com.yeka.bandapp.room.dto;

import java.util.List;

/** 밴드의 합주실 목록. {@code usageCount} 내림차순. */
public record RoomListResponse(Long bandId, int roomCount, List<RoomResponse> rooms) {
}
