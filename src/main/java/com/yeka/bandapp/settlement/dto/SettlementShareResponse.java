package com.yeka.bandapp.settlement.dto;

import com.yeka.bandapp.settlement.entity.SettlementShare;

import java.time.Instant;

/**
 * 정산 현황의 한 줄 — 멤버 한 명의 몫. {@code name}/{@code role}은 표시용이며, 정산 생성 이후 밴드를
 * 떠난 멤버는 {@code name}이 "(알 수 없음)", {@code role}이 "MEMBER"로 채워질 수 있다.
 */
public record SettlementShareResponse(
        Long userId,
        String name,
        String role,
        int amount,
        boolean paid,
        Instant paidAt
) {
    public static SettlementShareResponse of(SettlementShare share, String name, String role) {
        return new SettlementShareResponse(
                share.getUserId(), name, role, share.getAmount(), share.isPaid(), share.getPaidAt());
    }
}
