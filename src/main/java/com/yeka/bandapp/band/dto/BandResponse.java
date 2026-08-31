package com.yeka.bandapp.band.dto;

import com.yeka.bandapp.band.entity.Band;

import java.time.Instant;

public record BandResponse(
        Long id,
        String name,
        Long leaderId,
        String reservationPermission,
        Instant createdAt
) {
    public static BandResponse from(Band band) {
        return new BandResponse(
                band.getId(),
                band.getName(),
                band.getLeaderId(),
                band.getReservationPermission().name(),
                band.getCreatedAt());
    }
}
