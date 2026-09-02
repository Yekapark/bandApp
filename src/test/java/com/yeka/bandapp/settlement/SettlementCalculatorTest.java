package com.yeka.bandapp.settlement;

import com.yeka.bandapp.settlement.service.SettlementCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * N빵 분배 계산({@link SettlementCalculator})의 경계 동작. 컨테이너 없이 도는 순수 단위 테스트다.
 */
class SettlementCalculatorTest {

    @Test
    void remainder_goes_to_the_first_recipients_one_won_each() {
        Map<Long, Integer> shares = SettlementCalculator.split(10_000, List.of(1L, 2L, 3L));

        // 10000 / 3 = 3333, 나머지 1 → 맨 앞(밴드장) 한 명이 +1
        assertThat(shares).containsExactly(
                Map.entry(1L, 3334),
                Map.entry(2L, 3333),
                Map.entry(3L, 3333));
        assertThat(sum(shares)).isEqualTo(10_000);
    }

    @Test
    void divisible_amount_splits_evenly() {
        Map<Long, Integer> shares = SettlementCalculator.split(9_000, List.of(1L, 2L, 3L));

        assertThat(shares.values()).containsExactly(3_000, 3_000, 3_000);
        assertThat(sum(shares)).isEqualTo(9_000);
    }

    @Test
    void two_won_remainder_spreads_to_first_two() {
        Map<Long, Integer> shares = SettlementCalculator.split(3_002, List.of(10L, 20L, 30L));

        assertThat(shares).containsExactly(
                Map.entry(10L, 1_001),
                Map.entry(20L, 1_001),
                Map.entry(30L, 1_000));
        assertThat(sum(shares)).isEqualTo(3_002);
    }

    @Test
    void single_recipient_takes_everything() {
        Map<Long, Integer> shares = SettlementCalculator.split(7_777, List.of(42L));

        assertThat(shares).containsExactly(Map.entry(42L, 7_777));
    }

    @Test
    void amount_smaller_than_headcount_still_sums_exactly() {
        Map<Long, Integer> shares = SettlementCalculator.split(2, List.of(1L, 2L, 3L));

        assertThat(shares.values()).containsExactly(1, 1, 0);
        assertThat(sum(shares)).isEqualTo(2);
    }

    @Test
    void rejects_empty_recipients_and_non_positive_total() {
        assertThatThrownBy(() -> SettlementCalculator.split(1_000, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettlementCalculator.split(0, List.of(1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static int sum(Map<Long, Integer> shares) {
        return shares.values().stream().mapToInt(Integer::intValue).sum();
    }
}
