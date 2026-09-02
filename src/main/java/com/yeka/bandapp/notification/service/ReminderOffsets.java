package com.yeka.bandapp.notification.service;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;

import java.util.List;
import java.util.Objects;

/**
 * 리마인더 시점(분) 목록의 검증·정규화(순수 함수). 상태·트랜잭션이 없어 Docker 없이 단위 테스트한다
 * ({@code MediaPolicy}·{@code SettlementCalculator} 선례).
 *
 * <p>정규화 = 중복 제거 + 오름차순 정렬. 범위(1 ~ 상한)·개수 상한 위반은 도메인 예외(400).
 */
public final class ReminderOffsets {

    private ReminderOffsets() {
    }

    /**
     * 사용자가 보낸 시점 목록을 저장 가능한 배열로. {@code null}이나 빈 목록은 "리마인더 없음"({@code []})이다.
     *
     * @throws BusinessException {@code INVALID_REMINDER_OFFSET}(값이 1 미만 또는 {@code maxMinutes} 초과),
     *                           {@code TOO_MANY_REMINDER_OFFSETS}(고유 값 수가 {@code maxCount} 초과)
     */
    public static int[] normalize(List<Integer> raw, int maxMinutes, int maxCount) {
        List<Integer> distinctSorted = (raw == null ? List.<Integer>of() : raw).stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        for (int value : distinctSorted) {
            if (value < 1 || value > maxMinutes) {
                throw new BusinessException(ErrorCode.INVALID_REMINDER_OFFSET);
            }
        }
        if (distinctSorted.size() > maxCount) {
            throw new BusinessException(ErrorCode.TOO_MANY_REMINDER_OFFSETS);
        }
        return distinctSorted.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * 쉼표로 구분된 기본값 문자열({@code "10,60"})을 정규화된 배열로. 파싱 불가 토큰은 무시한다.
     * 결과가 비면 {@code {60}}으로 대체한다 — 기본값이 "리마인더 없음"이 되지 않도록.
     */
    public static int[] parseCsv(String csv, int maxMinutes, int maxCount) {
        List<Integer> values = java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(ReminderOffsets::tryParse)
                .filter(Objects::nonNull)
                .toList();
        int[] normalized = normalize(values, maxMinutes, maxCount);
        return normalized.length == 0 ? new int[]{60} : normalized;
    }

    private static Integer tryParse(String token) {
        try {
            return Integer.valueOf(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
