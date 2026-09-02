package com.yeka.bandapp.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 업로드 URL 발급 요청. 클라이언트가 올릴 파일의 형식·크기를 <b>신고</b>하는 값이다 — 완료 콜백에서
 * R2 HEAD 로 실제 값과 대조한다.
 */
public record IssueUploadUrlRequest(
        @Schema(description = "MIME 타입. 허용: image/jpeg, image/png, image/webp, video/mp4, video/quicktime.",
                example = "image/jpeg")
        @NotBlank String contentType,

        @Schema(description = "파일 크기(바이트). 이미지 최대 10MB, 영상 최대 50MB.", example = "3145728")
        @NotNull @Positive Long sizeBytes
) {
}
