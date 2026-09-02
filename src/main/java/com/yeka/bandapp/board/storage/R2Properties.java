package com.yeka.bandapp.board.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cloudflare R2(S3 호환) 연동 설정. {@code app.r2.*}.
 *
 * <p>{@code accountId}/{@code bucket}/키가 비어 있을 수 있다 — 그 경우 미디어 업로드·조회 API 만
 * {@code MEDIA_STORAGE_NOT_CONFIGURED}(503)로 응답하고, 게시글·신고·차단은 정상 동작한다
 * ({@code KakaoProperties}와 같은 방식).
 *
 * <p>{@code uploadUrlTtl}/{@code downloadUrlTtl}은 BUILD_PLAN 제한값(5~15분)을 벗어나지 못하도록
 * compact 생성자에서 clamp 한다 — 설정 실수로도 만료가 너무 길어지지 않게.
 */
@ConfigurationProperties(prefix = "app.r2")
public record R2Properties(
        String accountId,
        String bucket,
        String accessKeyId,
        String secretAccessKey,
        String endpoint,
        Duration uploadUrlTtl,
        Duration downloadUrlTtl,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final Duration MIN_TTL = Duration.ofMinutes(5);
    private static final Duration MAX_TTL = Duration.ofMinutes(15);

    public R2Properties {
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = accountId == null || accountId.isBlank()
                    ? ""
                    : "https://" + accountId + ".r2.cloudflarestorage.com";
        }
        uploadUrlTtl = clampTtl(uploadUrlTtl, Duration.ofMinutes(15));
        downloadUrlTtl = clampTtl(downloadUrlTtl, Duration.ofMinutes(10));
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }

    private static Duration clampTtl(Duration value, Duration fallback) {
        Duration ttl = value != null ? value : fallback;
        if (ttl.compareTo(MIN_TTL) < 0) {
            return MIN_TTL;
        }
        if (ttl.compareTo(MAX_TTL) > 0) {
            return MAX_TTL;
        }
        return ttl;
    }

    public boolean isConfigured() {
        return isNotBlank(accountId) && isNotBlank(bucket)
                && isNotBlank(accessKeyId) && isNotBlank(secretAccessKey);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
