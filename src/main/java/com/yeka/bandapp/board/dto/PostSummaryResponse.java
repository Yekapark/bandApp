package com.yeka.bandapp.board.dto;

import com.yeka.bandapp.board.entity.BoardPost;

import java.time.Instant;

/**
 * 목록 한 줄. 본문은 앞부분만({@code preview}), 대표 이미지가 있으면 {@code thumbnailUrl}(짧은 만료의
 * presigned GET). 전체 본문·첨부는 상세 조회에서 받는다.
 */
public record PostSummaryResponse(
        Long id,
        Long authorId,
        String authorName,
        String title,
        String preview,
        Instant createdAt,
        int mediaCount,
        String thumbnailUrl
) {
    private static final int PREVIEW_LENGTH = 100;

    public static PostSummaryResponse of(BoardPost post, String authorName, int mediaCount,
                                         String thumbnailUrl) {
        String content = post.getContent();
        String preview = content.length() > PREVIEW_LENGTH
                ? content.substring(0, PREVIEW_LENGTH)
                : content;
        return new PostSummaryResponse(
                post.getId(),
                post.getAuthorId(),
                authorName,
                post.getTitle(),
                preview,
                post.getCreatedAt(),
                mediaCount,
                thumbnailUrl);
    }
}
