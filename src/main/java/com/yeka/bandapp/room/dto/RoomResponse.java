package com.yeka.bandapp.room.dto;

import com.yeka.bandapp.room.entity.Room;

import java.time.Instant;

/**
 * 합주실 응답. {@code lat}/{@code lng}는 지오코딩이 안 됐으면 {@code null}이다 —
 * 클라이언트는 좌표가 없는 합주실을 지도에 못 찍을 뿐, 목록·선택에는 문제없이 써야 한다.
 */
public record RoomResponse(
        Long id,
        String name,
        String address,
        Double lat,
        Double lng,
        String phone,
        String memo,
        int usageCount,
        Long createdBy,
        Instant createdAt
) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getAddress(),
                room.getLat(),
                room.getLng(),
                room.getPhone(),
                room.getMemo(),
                room.getUsageCount(),
                room.getCreatedBy(),
                room.getCreatedAt());
    }
}
