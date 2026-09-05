package com.yeka.bandapp.settlement.repository;

import java.time.Instant;

/**
 * 밴드 정산 목록의 한 줄 — 정산과 그 정산이 붙은 일정을 조인한 결과.
 *
 * <p>몫·합주실 이름은 여기 없다. 페이지의 정산들을 모아 한 번에 더 읽는다(N+1 회피).
 */
public record BandSettlementRow(
        Long settlementId,
        Long reservationId,
        Instant startAt,
        Long roomId,
        int totalAmount
) {
}
