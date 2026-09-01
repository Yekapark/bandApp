package com.yeka.bandapp.band.dto;

import com.yeka.bandapp.band.entity.Band;
import com.yeka.bandapp.band.entity.BandMember;

import java.time.Instant;
import java.util.List;

/**
 * "내가 속한 밴드 목록" 응답. 클라이언트가 밴드 스위처를 그리는 데 필요한 최소 정보만 담는다
 * (BACKLOG §1.9 — Phase 3~4 클라이언트 작업 전 필요).
 */
public record MyBandListResponse(int bandCount, List<MyBandResponse> bands) {

    public record MyBandResponse(
            Long id,
            String name,
            String myRole,
            int memberCount,
            Instant joinedAt
    ) {
        public static MyBandResponse of(Band band, BandMember myMembership, long memberCount) {
            return new MyBandResponse(
                    band.getId(),
                    band.getName(),
                    myMembership.getRole().name(),
                    (int) memberCount,
                    myMembership.getJoinedAt());
        }
    }
}
