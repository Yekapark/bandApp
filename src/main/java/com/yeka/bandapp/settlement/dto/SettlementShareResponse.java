package com.yeka.bandapp.settlement.dto;

import com.yeka.bandapp.settlement.entity.SettlementShare;

import java.time.Instant;

/**
 * 정산 현황의 한 줄 — 멤버 한 명의 몫. {@code name}/{@code role}은 표시용이며, 정산 생성 이후 밴드를
 * 떠난 멤버(이미 낸 몫은 재계산 후에도 남는다)는 {@code role}이 "MEMBER"로, 계정까지 없으면 {@code name}이
 * "(알 수 없음)"으로 채워진다.
 *
 * <p>{@code paidByLeader} — 밴드장이 대신 "냈음" 으로 체크했다("밴드장 확인"). {@code exempt} — 밴드장이 면제한 몫(나간 멤버의 미납).
 */
public record SettlementShareResponse(
        Long userId,
        String name,
        String role,
        int amount,
        boolean paid,
        Instant paidAt,
        boolean paidByLeader,
        boolean exempt
) {
    public static SettlementShareResponse of(SettlementShare share, String name, String role) {
        return new SettlementShareResponse(
                share.getUserId(), name, role, share.getAmount(), share.isPaid(), share.getPaidAt(),
                share.isPaidByLeader(), share.isExempt());
    }
}
