package com.yeka.bandapp.band.service;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 컨테이너가 필요 없는 순수 단위 테스트. */
class InviteCodeGeneratorTest {

    private final InviteCodeGenerator generator = new InviteCodeGenerator();

    @Test
    void code_is_8_chars_of_the_allowed_alphabet() {
        IntStream.range(0, 2000).forEach(i -> {
            String code = generator.generate();
            assertThat(code).hasSize(8);
            assertThat(code.chars()).allMatch(c -> InviteCodeGenerator.ALPHABET.indexOf(c) >= 0);
        });
    }

    @Test
    void confusable_characters_are_excluded() {
        assertThat(InviteCodeGenerator.ALPHABET)
                .doesNotContain("0").doesNotContain("O")
                .doesNotContain("1").doesNotContain("I");
    }

    @Test
    void codes_are_not_trivially_repeated() {
        long distinct = IntStream.range(0, 500).mapToObj(i -> generator.generate()).distinct().count();
        assertThat(distinct).isEqualTo(500);
    }
}
