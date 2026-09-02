package com.yeka.bandapp.settlement.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * N빵 분배 계산(순수 함수). 상태·트랜잭션이 없어 Docker 없이 단위 테스트할 수 있다.
 *
 * <p>균등 몫은 내림하고, 나누어떨어지지 않는 나머지 R원은 <b>앞에서부터</b> 한 명당 1원씩 더한다.
 * 그래서 반환 맵의 값 합계는 항상 {@code total}이다. 호출 측이 {@code recipients}를
 * "밴드장 먼저 → 가입일 순"으로 정렬해 넘기므로, 나머지는 밴드장이(밴드장이 대상에 없으면 최고참이) 진다.
 */
public final class SettlementCalculator {

    private SettlementCalculator() {
    }

    /**
     * @param total      나눌 총액(원). 0보다 커야 한다.
     * @param recipients 분배 대상 userId, 우선순위 순. 비어 있으면 안 된다.
     * @return userId → 몫(원). 입력 순서를 유지하며 값 합계 = {@code total}.
     */
    public static Map<Long, Integer> split(int total, List<Long> recipients) {
        if (total <= 0) {
            throw new IllegalArgumentException("총액은 0보다 커야 합니다: " + total);
        }
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("분배 대상이 없습니다.");
        }
        int n = recipients.size();
        int base = total / n;
        int remainder = total - base * n; // 0 .. n-1

        Map<Long, Integer> shares = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            shares.put(recipients.get(i), base + (i < remainder ? 1 : 0));
        }
        return shares;
    }
}
