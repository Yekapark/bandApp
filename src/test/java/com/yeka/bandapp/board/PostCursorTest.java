package com.yeka.bandapp.board;

import com.yeka.bandapp.board.service.PostCursor;
import com.yeka.bandapp.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link PostCursor} 순수 단위 테스트 — Docker 불필요. 인코딩/디코딩 왕복과 깨진 커서 처리. */
class PostCursorTest {

    @Test
    void round_trips_through_encode_and_decode_preserving_microseconds() {
        Instant createdAt = Instant.parse("2026-03-02T09:15:30.123456Z");
        PostCursor original = new PostCursor(createdAt, 4242L);

        PostCursor decoded = PostCursor.decode(original.encode());

        assertThat(decoded.createdAt()).isEqualTo(createdAt);
        assertThat(decoded.id()).isEqualTo(4242L);
    }

    @Test
    void null_or_blank_cursor_means_first_page() {
        assertThat(PostCursor.decode(null)).isNull();
        assertThat(PostCursor.decode("")).isNull();
        assertThat(PostCursor.decode("   ")).isNull();
    }

    @Test
    void malformed_cursor_is_rejected_as_post_cursor_invalid() {
        for (String bad : new String[] {"not-base64!!!", "Zm9vYmFy", "====", "bm8tc2VwYXJhdG9y"}) {
            assertThatThrownBy(() -> PostCursor.decode(bad))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).errorCode().name())
                            .isEqualTo("POST_CURSOR_INVALID"));
        }
    }
}
