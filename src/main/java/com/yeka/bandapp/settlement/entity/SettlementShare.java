package com.yeka.bandapp.settlement.entity;

import com.yeka.bandapp.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 정산에서 멤버 한 명이 낼 몫. {@code (settlement_id, user_id)} 유니크 — 정산당 멤버 하나.
 *
 * <p>{@code paid}는 본인이 직접 체크하는 셀프 리포트다({@link #markPaid}). 다른 멤버가 대신 바꿀 수 없다.
 * 재계산 시 계속 대상인 멤버의 행은 {@link #reassign}으로 금액만 새로 매기고 납부 여부는 보존한다.
 */
@Entity
@Table(name = "settlement_shares")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementShare extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id", nullable = false)
    private Long settlementId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    private boolean paid;

    /** {@code paid}를 true 로 바꾼 시각. 체크를 취소하면 {@code null}로 되돌린다. */
    @Column(name = "paid_at")
    private Instant paidAt;

    private SettlementShare(long settlementId, long userId, int amount) {
        this.settlementId = settlementId;
        this.userId = userId;
        this.amount = amount;
        this.paid = false;
    }

    /** 새 몫. 금액은 호출 측(서비스)이 분배 계산 결과로 넘긴다. 처음엔 미납. */
    public static SettlementShare of(long settlementId, long userId, int amount) {
        return new SettlementShare(settlementId, userId, amount);
    }

    /** 재계산 — 분담액만 새로 매긴다. 납부 여부({@code paid}/{@code paidAt})는 유지한다. */
    public void reassign(int amount) {
        this.amount = amount;
    }

    /** 본인 납부 체크. 취소({@code paid=false})하면 시각도 지운다. */
    public void markPaid(boolean paid, Instant when) {
        this.paid = paid;
        this.paidAt = paid ? when : null;
    }
}
