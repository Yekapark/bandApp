package com.yeka.bandapp.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 게시글 수정 요청. PUT 전체 교체 — 제목·본문을 모두 보낸다. */
public record UpdatePostRequest(
        @Schema(description = "제목. 1~100자.", example = "3월 2일 합주 사진 (영상 추가)")
        @NotBlank @Size(max = 100) String title,

        @Schema(description = "본문. 1~4000자.", example = "영상도 올렸습니다.")
        @NotBlank @Size(max = 4000) String content
) {
}
