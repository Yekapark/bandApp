package com.yeka.bandapp.common.log;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class LogMasksTest {

    @Test
    void 토큰은_앞_6자만_남긴다() {
        assertThat(LogMasks.token("abcdefghijklmnop")).isEqualTo("abcdef…");
        assertThat(LogMasks.token("abc")).isEqualTo("…");
        assertThat(LogMasks.token(null)).isNull();
    }

    @Test
    void 예외_사본은_URL_을_지우고_스택을_유지한다() {
        IOException original = new IOException("404 Not Found\nGET https://androidpublisher.googleapis.com/"
                + "androidpublisher/v3/applications/pkg/purchases/subscriptionsv2/tokens/SECRET-TOKEN.AO-J1Ox");
        RuntimeException copy = LogMasks.redacted(original);

        assertThat(copy.getMessage()).doesNotContain("SECRET-TOKEN").contains("404 Not Found", "GET <url>");
        assertThat(copy.getStackTrace()).isEqualTo(original.getStackTrace());
        assertThat(copy.getCause()).isNull();
    }
}
