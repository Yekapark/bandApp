package com.yeka.bandapp.band.dto;

import com.yeka.bandapp.band.entity.BandInvite;

import java.time.Instant;

/**
 * @param link 공유용 초대 링크. 앱 설치 시 앱이 열리고, 미설치 시 스토어로 유도하는 웹 페이지가 뜬다.
 */
public record InviteResponse(
        String code,
        String link,
        Instant expiresAt,
        Integer maxUses,
        int usedCount,
        boolean revoked
) {
    public static InviteResponse of(BandInvite invite, String link) {
        return new InviteResponse(
                invite.getCode(),
                link,
                invite.getExpiresAt(),
                invite.getMaxUses(),
                invite.getUsedCount(),
                invite.isRevoked());
    }
}
