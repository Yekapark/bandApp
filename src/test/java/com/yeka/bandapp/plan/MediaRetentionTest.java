package com.yeka.bandapp.plan;

import com.yeka.bandapp.plan.service.MediaRetention;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MediaRetention} 순수 단위 테스트 — Docker 불필요.
 * FREE 는 업로드 + 보관일수, PREMIUM(null)은 무제한.
 */
class MediaRetentionTest {

    private static final Instant UPLOADED_AT = Instant.parse("2026-03-01T12:00:00Z");

    @Test
    void free_plan_expiry_is_retention_days_after_upload() {
        assertThat(MediaRetention.expiresAt(UPLOADED_AT, 30))
                .isEqualTo(UPLOADED_AT.plus(30, ChronoUnit.DAYS));
    }

    @Test
    void arbitrary_retention_days_are_honored() {
        assertThat(MediaRetention.expiresAt(UPLOADED_AT, 7))
                .isEqualTo(UPLOADED_AT.plus(7, ChronoUnit.DAYS));
    }

    @Test
    void null_retention_means_unlimited() {
        assertThat(MediaRetention.expiresAt(UPLOADED_AT, null)).isNull();
    }

    @Test
    void zero_or_negative_retention_is_a_programming_error() {
        assertThatThrownBy(() -> MediaRetention.expiresAt(UPLOADED_AT, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MediaRetention.expiresAt(UPLOADED_AT, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void free_retention_days_constant_matches_the_migration_literal() {
        assertThat(MediaRetention.FREE_RETENTION_DAYS).isEqualTo(30);
    }
}
