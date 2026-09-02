package com.yeka.bandapp.board.dto;

import com.yeka.bandapp.board.entity.BoardPost;

import java.time.Instant;
import java.util.List;

/**
 * 게시글 상세. {@code editable}은 요청자가 작성자 본인이거나 밴드장이라 수정·삭제할 수 있는지다.
 * {@code media}의 READY 첨부에는 짧은 만료의 presigned GET URL 이 들어 있다.
 */
public record PostResponse(
        Long id,
        Long bandId,
        Long authorId,
        String authorName,
        String title,
        String content,
        Instant createdAt,
        boolean editable,
        int mediaCount,
        List<MediaResponse> media
) {
    public static PostResponse of(BoardPost post, String authorName, boolean editable,
                                  List<MediaResponse> media) {
        return new PostResponse(
                post.getId(),
                post.getBandId(),
                post.getAuthorId(),
                authorName,
                post.getTitle(),
                post.getContent(),
                post.getCreatedAt(),
                editable,
                media.size(),
                media);
    }
}
