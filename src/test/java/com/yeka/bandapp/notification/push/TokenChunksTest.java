package com.yeka.bandapp.notification.push;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FCM 멀티캐스트 한계(1회 500개)로 토큰 목록을 자르는 순수 함수. Docker 불필요.
 */
class TokenChunksTest {

    private static List<String> tokens(int n) {
        return IntStream.range(0, n).mapToObj(i -> "t" + i).toList();
    }

    @Test
    void short_list_stays_in_one_chunk() {
        assertThat(TokenChunks.of(tokens(3), TokenChunks.MAX_PER_MULTICAST)).hasSize(1);
    }

    @Test
    void exactly_500_is_one_chunk_and_501_is_two() {
        assertThat(TokenChunks.of(tokens(500), 500)).hasSize(1);
        List<List<String>> two = TokenChunks.of(tokens(501), 500);
        assertThat(two).hasSize(2);
        assertThat(two.get(0)).hasSize(500);
        assertThat(two.get(1)).containsExactly("t500");
    }

    @Test
    void chunks_cover_all_tokens_in_order() {
        List<List<String>> chunks = TokenChunks.of(tokens(1250), 500);
        assertThat(chunks).hasSize(3);
        assertThat(chunks.stream().flatMap(List::stream).toList()).isEqualTo(tokens(1250));
    }

    @Test
    void empty_list_yields_no_chunks() {
        assertThat(TokenChunks.of(List.of(), 500)).isEmpty();
    }

    @Test
    void non_positive_chunk_size_is_rejected() {
        assertThatThrownBy(() -> TokenChunks.of(tokens(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
