package com.yeka.bandapp.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 게시글 작성 요청. 제목·본문 모두 필수다. */
public record CreatePostRequest(
        @Schema(description = "제목. 1~100자.", example = "3월 2일 합주 사진")
        @NotBlank @Size(max = 100) String title,

        @Schema(description = "본문. 1~4000자.", example = "드럼 새로 바뀐 방에서 첫 합주. 사진 첨부합니다.")
        @NotBlank @Size(max = 4000) String content
) {
}
