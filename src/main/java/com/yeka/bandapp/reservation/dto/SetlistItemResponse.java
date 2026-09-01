package com.yeka.bandapp.reservation.dto;

import com.yeka.bandapp.reservation.entity.SetlistItem;

public record SetlistItemResponse(
        Long id,
        String title,
        String artist,
        String referenceUrl,
        int orderNo
) {
    public static SetlistItemResponse from(SetlistItem item) {
        return new SetlistItemResponse(
                item.getId(),
                item.getTitle(),
                item.getArtist(),
                item.getReferenceUrl(),
                item.getOrderNo());
    }
}
