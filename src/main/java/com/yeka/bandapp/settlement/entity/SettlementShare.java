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
 * <p>{@code paid}는 본인이 직접 체크하거나, 밴드장이 대신 체크한다({@link #markPaid}, 현금 등 — {@code paidByLeader}).
 * 밴드를 나간 멤버의 미납 몫은 밴드장이 면제할 수 있다({@link #exempt}) — 미납으로 세지 않는다.
 * 재계산 시 계속 대상인 멤버의 행은 {@link #reassign}으로 금액을 새로 매긴다 — 금액이 그대로면 납부 여부를
 * 보존하고, 바뀌면 납부 체크를 푼다(낸 돈과 새 몫이 달라 차액을 다시 확인해야 하므로).
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

    /** 밴드장이 대신 "냈음" 으로 체크했는지. {@code paid} 가 false 면 항상 false. */
    @Column(name = "paid_by_leader", nullable = false)
    private boolean paidByLeader;

    /** 밴드장이 면제한 몫(나간 멤버의 미납). {@code paid} 와 함께 true 일 수 없다. */
    @Column(nullable = false)
    private boolean exempt;

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

    /**
     * 재계산 — 분담액을 새로 매긴다. 금액이 그대로면 납부 여부를 유지하고, 바뀌었는데 납부 체크가 돼 있었으면
     * 체크를 푼다(예전 금액만 냈으므로 "냈음" 으로 두면 차액이 조용히 사라진다).
     *
     * @return 이 호출로 납부 체크가 풀렸으면 {@code true}(본인에게 알려야 한다).
     */
    public boolean reassign(int amount) {
        this.exempt = false;   // 다시 분담 대상이 됐다(재가입) — 면제는 나간 동안의 것이다
        if (this.amount == amount) {
            return false;
        }
        this.amount = amount;
        if (!paid) {
            return false;
        }
        this.paid = false;
        this.paidAt = null;
        this.paidByLeader = false;
        return true;
    }

    /**
     * 납부 체크. 취소({@code paid=false})하면 시각도 지운다. {@code byLeader} 는 밴드장이 본인 대신 체크했는지 —
     * 본인이 다시 체크하면 풀린다. 냈다고 체크하면 면제는 풀린다.
     */
    public void markPaid(boolean paid, Instant when, boolean byLeader) {
        this.paid = paid;
        this.paidAt = paid ? when : null;
        this.paidByLeader = paid && byLeader;
        if (paid) {
            this.exempt = false;
        }
    }

    /** 면제하거나 푼다. 낸 몫은 면제할 수 없다(호출 측이 먼저 거른다). */
    public void exempt(boolean exempt) {
        if (exempt && paid) {
            throw new IllegalStateException("낸 몫은 면제할 수 없다 shareId=" + id);
        }
        this.exempt = exempt;
    }
}
