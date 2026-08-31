package com.yeka.bandapp.band.dto;

import com.yeka.bandapp.band.entity.BandMember;
import com.yeka.bandapp.user.service.UserDirectoryService.UserSummary;

import java.time.Instant;

public record BandMemberResponse(
        Long userId,
        String name,
        String role,
        Instant joinedAt
) {
    public static BandMemberResponse of(BandMember member, UserSummary summary) {
        String name = summary != null ? summary.name() : "탈퇴한 사용자";
        return new BandMemberResponse(member.getUserId(), name, member.getRole().name(), member.getJoinedAt());
    }
}
