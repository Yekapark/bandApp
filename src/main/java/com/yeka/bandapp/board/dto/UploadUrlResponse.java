package com.yeka.bandapp.board.dto;

import java.time.Instant;
import java.util.Map;

/**
 * presigned PUT URL 발급 결과. 클라이언트는 이 {@code uploadUrl}로 {@code method}(PUT) 요청을 보내며,
 * {@code requiredHeaders}(현재 {@code Content-Type})를 그대로 붙여야 서명이 맞는다. 파일 바이트는
 * 이 URL 로 R2 에 직접 전송되고 백엔드를 지나지 않는다.
 *
 * <p>업로드가 끝나면 {@code POST .../media/{mediaId}/complete}를 호출해야 첨부가 READY 로 확정된다.
 */
public record UploadUrlResponse(
        Long mediaId,
        String uploadUrl,
        String method,
        Map<String, String> requiredHeaders,
        Instant urlExpiresAt,
        long maxSizeBytes
) {
}
