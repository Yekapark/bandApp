package com.yeka.bandapp.board;

import com.yeka.bandapp.board.entity.MediaType;
import com.yeka.bandapp.board.service.MediaPolicy;
import com.yeka.bandapp.board.storage.StoredObject;
import com.yeka.bandapp.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MediaPolicy} 순수 단위 테스트 — Docker 불필요.
 * 형식 화이트리스트, 이미지 10MB / 영상 50MB 경계, 위조(크기·형식) 판정.
 * (보관기한 계산은 요금제 도메인으로 옮겨졌다 — {@code MediaRetentionTest} 참조.)
 */
class MediaPolicyTest {

    @Test
    void resolves_supported_mime_types_ignoring_case_and_params() {
        assertThat(MediaPolicy.resolveType("image/jpeg")).isEqualTo(MediaType.IMAGE);
        assertThat(MediaPolicy.resolveType("IMAGE/PNG")).isEqualTo(MediaType.IMAGE);
        assertThat(MediaPolicy.resolveType("image/webp; charset=binary")).isEqualTo(MediaType.IMAGE);
        assertThat(MediaPolicy.resolveType("video/mp4")).isEqualTo(MediaType.VIDEO);
        assertThat(MediaPolicy.resolveType("video/quicktime")).isEqualTo(MediaType.VIDEO);
    }

    @Test
    void rejects_unsupported_or_dangerous_mime_types() {
        for (String bad : new String[] {"image/svg+xml", "image/gif", "text/html", "application/pdf", "", "  "}) {
            assertThatThrownBy(() -> MediaPolicy.resolveType(bad))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).errorCode().name())
                            .isEqualTo("MEDIA_TYPE_NOT_SUPPORTED"));
        }
    }

    @Test
    void image_size_limit_is_exactly_10_mib() {
        MediaPolicy.requireWithinLimit(MediaType.IMAGE, MediaPolicy.IMAGE_MAX_BYTES); // 경계값 허용
        assertThatThrownBy(() -> MediaPolicy.requireWithinLimit(MediaType.IMAGE, MediaPolicy.IMAGE_MAX_BYTES + 1))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode().name()).isEqualTo("MEDIA_SIZE_EXCEEDED"));
    }

    @Test
    void video_size_limit_is_exactly_50_mib() {
        MediaPolicy.requireWithinLimit(MediaType.VIDEO, MediaPolicy.VIDEO_MAX_BYTES);
        assertThatThrownBy(() -> MediaPolicy.requireWithinLimit(MediaType.VIDEO, MediaPolicy.VIDEO_MAX_BYTES + 1))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode().name()).isEqualTo("MEDIA_SIZE_EXCEEDED"));
    }

    @Test
    void zero_or_negative_declared_size_is_invalid_input() {
        assertThatThrownBy(() -> MediaPolicy.requireWithinLimit(MediaType.IMAGE, 0))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode().name()).isEqualTo("INVALID_INPUT"));
        assertThatThrownBy(() -> MediaPolicy.requireWithinLimit(MediaType.IMAGE, -1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void verify_upload_passes_when_actual_matches_declared() {
        MediaPolicy.verifyUpload(MediaType.IMAGE, 1_000, "image/jpeg",
                new StoredObject(1_000, "image/jpeg"));
    }

    @Test
    void verify_upload_rejects_size_mismatch() {
        assertThatThrownBy(() -> MediaPolicy.verifyUpload(MediaType.IMAGE, 1_000, "image/jpeg",
                new StoredObject(2_000, "image/jpeg")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode().name())
                        .isEqualTo("MEDIA_SIZE_MISMATCH"));
    }

    @Test
    void verify_upload_rejects_actual_over_limit_even_if_it_matches_declared() {
        long huge = MediaPolicy.IMAGE_MAX_BYTES + 5;
        assertThatThrownBy(() -> MediaPolicy.verifyUpload(MediaType.IMAGE, huge, "image/jpeg",
                new StoredObject(huge, "image/jpeg")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode().name())
                        .isEqualTo("MEDIA_SIZE_MISMATCH"));
    }

    @Test
    void verify_upload_rejects_content_type_mismatch() {
        assertThatThrownBy(() -> MediaPolicy.verifyUpload(MediaType.IMAGE, 1_000, "image/jpeg",
                new StoredObject(1_000, "image/png")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).errorCode().name())
                        .isEqualTo("MEDIA_CONTENT_TYPE_MISMATCH"));
    }

    @Test
    void verify_upload_tolerates_missing_actual_content_type() {
        MediaPolicy.verifyUpload(MediaType.IMAGE, 1_000, "image/jpeg", new StoredObject(1_000, null));
    }
}
