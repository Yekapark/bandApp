package com.yeka.bandapp.board.dto;

import com.yeka.bandapp.board.entity.MediaAttachment;
import com.yeka.bandapp.board.entity.MediaStatus;
import com.yeka.bandapp.board.entity.MediaType;

import java.time.Instant;

/**
 * 게시글에 딸린 첨부 하나. {@code downloadUrl}은 {@code status=READY}일 때만 채워지는 짧은 만료의
 * presigned GET URL 이다(버킷은 비공개). PENDING·EXPIRED 면 {@code null}.
 */
public record MediaResponse(
        Long id,
        MediaType type,
        MediaStatus status,
        String contentType,
        long sizeBytes,
        Instant uploadedAt,
        Instant expiresAt,
        String downloadUrl
) {
    public static MediaResponse of(MediaAttachment media, String downloadUrl) {
        return new MediaResponse(
                media.getId(),
                media.getType(),
                media.getStatus(),
                media.getContentType(),
                media.getSizeBytes(),
                media.getUploadedAt(),
                media.getExpiresAt(),
                downloadUrl);
    }
}
