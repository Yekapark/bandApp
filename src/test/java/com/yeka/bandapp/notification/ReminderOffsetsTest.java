package com.yeka.bandapp.notification;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.notification.service.ReminderOffsets;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 리마인더 시점 정규화·검증(순수 함수). Docker 불필요.
 */
class ReminderOffsetsTest {

    private static final int MAX_MINUTES = 1440;
    private static final int MAX_COUNT = 5;

    @Test
    void dedupes_and_sorts_ascending() {
        int[] result = ReminderOffsets.normalize(List.of(60, 10, 60, 30), MAX_MINUTES, MAX_COUNT);
        assertThat(result).containsExactly(10, 30, 60);
    }

    @Test
    void null_or_empty_means_no_reminders() {
        assertThat(ReminderOffsets.normalize(null, MAX_MINUTES, MAX_COUNT)).isEmpty();
        assertThat(ReminderOffsets.normalize(List.of(), MAX_MINUTES, MAX_COUNT)).isEmpty();
    }

    @Test
    void rejects_value_below_one() {
        assertThatThrownBy(() -> ReminderOffsets.normalize(List.of(0), MAX_MINUTES, MAX_COUNT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("리마인더 시점");
    }

    @Test
    void rejects_value_over_the_ceiling() {
        assertThatThrownBy(() -> ReminderOffsets.normalize(List.of(MAX_MINUTES + 1), MAX_MINUTES, MAX_COUNT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejects_too_many_distinct_values() {
        assertThatThrownBy(() -> ReminderOffsets.normalize(List.of(1, 2, 3, 4, 5, 6), MAX_MINUTES, MAX_COUNT))
                .isInstanceOf(BusinessException.class);
        // 중복 제거 후 개수가 상한 이내면 통과한다.
        assertThat(ReminderOffsets.normalize(List.of(1, 1, 2, 2, 3, 3), MAX_MINUTES, MAX_COUNT))
                .containsExactly(1, 2, 3);
    }

    @Test
    void parses_csv_default_and_falls_back_to_60_when_empty() {
        assertThat(ReminderOffsets.parseCsv("10, 60", MAX_MINUTES, MAX_COUNT)).containsExactly(10, 60);
        assertThat(ReminderOffsets.parseCsv("  ,  ", MAX_MINUTES, MAX_COUNT)).containsExactly(60);
        assertThat(ReminderOffsets.parseCsv("not-a-number", MAX_MINUTES, MAX_COUNT)).containsExactly(60);
    }

    @Test
    void parse_csv_still_enforces_ceiling() {
        assertThatThrownBy(() -> ReminderOffsets.parseCsv("99999", MAX_MINUTES, MAX_COUNT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void normalize_result_is_a_plain_int_array() {
        int[] result = ReminderOffsets.normalize(Arrays.asList(30, 15), MAX_MINUTES, MAX_COUNT);
        assertThat(result).isInstanceOf(int[].class).containsExactly(15, 30);
    }
}
