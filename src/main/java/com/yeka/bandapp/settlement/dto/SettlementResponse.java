package com.yeka.bandapp.settlement.dto;

import com.yeka.bandapp.settlement.entity.Settlement;
import com.yeka.bandapp.settlement.entity.SplitType;

import java.time.Instant;
import java.util.List;

/**
 * 정산 현황 전체. {@code shares}의 {@code amount} 합계는 항상 {@code totalAmount}와 일치한다
 * (나머지는 밴드장 먼저, 없으면 최고참이 부담).
 *
 * <p>{@code paidAmount}는 납부 완료로 체크된 몫의 합, {@code outstandingAmount}는 남은 금액
 * ({@code totalAmount - paidAmount})이다.
 */
public record SettlementResponse(
        Long reservationId,
        Long settlementId,
        int totalAmount,
        SplitType splitType,
        int shareCount,
        int paidCount,
        int paidAmount,
        int outstandingAmount,
        Instant createdAt,
        List<SettlementShareResponse> shares
) {
    public static SettlementResponse of(long reservationId, Settlement settlement,
                                        List<SettlementShareResponse> shares) {
        int paidAmount = shares.stream().filter(SettlementShareResponse::paid)
                .mapToInt(SettlementShareResponse::amount).sum();
        long paidCount = shares.stream().filter(SettlementShareResponse::paid).count();
        return new SettlementResponse(
                reservationId,
                settlement.getId(),
                settlement.getTotalAmount(),
                settlement.getSplitType(),
                shares.size(),
                (int) paidCount,
                paidAmount,
                settlement.getTotalAmount() - paidAmount,
                settlement.getCreatedAt(),
                shares);
    }
}
