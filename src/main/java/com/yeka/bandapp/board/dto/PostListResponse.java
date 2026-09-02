package com.yeka.bandapp.board.dto;

import java.util.List;

/**
 * 게시글 목록 한 페이지. {@code (createdAt DESC, id DESC)} 순이며, 차단한(또는 나를 차단한) 사용자의
 * 글은 빠져 있다. {@code hasNext}가 true 면 {@code nextCursor}를 다음 요청의 {@code ?cursor=}로 넘긴다.
 */
public record PostListResponse(
        Long bandId,
        int count,
        List<PostSummaryResponse> posts,
        String nextCursor,
        boolean hasNext
) {
}
