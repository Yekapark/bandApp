package com.yeka.bandapp.band.dto;

import java.util.List;

public record BandMemberListResponse(
        Long bandId,
        int memberCount,
        List<BandMemberResponse> members
) {
}
