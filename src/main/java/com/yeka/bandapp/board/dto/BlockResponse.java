package com.yeka.bandapp.board.dto;

import com.yeka.bandapp.board.entity.UserBlock;

import java.time.Instant;

/** 차단 한 건. */
public record BlockResponse(
        Long id,
        Long blockedUserId,
        String blockedUserName,
        Instant createdAt
) {
    public static BlockResponse of(UserBlock block, String blockedUserName) {
        return new BlockResponse(
                block.getId(),
                block.getBlockedUserId(),
                blockedUserName,
                block.getCreatedAt());
    }
}
