package com.yeka.bandapp.settlement.repository;

/**
 * 한 밴드에서 내가 아직 안 낸 몫의 합계와 건수 — 목록을 페이지마다 읽지 않고 DB 에서 한 번에 센다.
 */
public record MyOutstandingRow(
        long amount,
        long count
) {
}
