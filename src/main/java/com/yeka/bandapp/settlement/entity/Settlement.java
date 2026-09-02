package com.yeka.bandapp.settlement.entity;

import com.yeka.bandapp.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 일정 하나에 매긴 방값 총액과 분배 방식. 일정당 하나만 존재한다({@code reservation_id} 유니크).
 *
 * <p>총액을 다시 입력하거나 참석자가 바뀌면 재계산 API 가 {@link #changeTerms}로 값을 바꾸고
 * {@link SettlementShare}를 다시 만든다. 서버가 참석 응답 변화를 감지해 자동으로 다시 나누지는 않는다
 * (BUILD_PLAN Phase 7).
 */
@Entity
@Table(name = "settlements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 20)
    private SplitType splitType;

    private Settlement(long reservationId, int totalAmount, SplitType splitType) {
        this.reservationId = reservationId;
        this.totalAmount = totalAmount;
        this.splitType = splitType;
    }

    public static Settlement create(long reservationId, int totalAmount, SplitType splitType) {
        return new Settlement(reservationId, totalAmount, splitType);
    }

    /** 재계산 시 총액·분배 방식을 새 값으로 교체한다(참석자 재반영과 한 트랜잭션에서). */
    public void changeTerms(int totalAmount, SplitType splitType) {
        this.totalAmount = totalAmount;
        this.splitType = splitType;
    }
}
