package com.yeka.bandapp.reservation.entity;

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

import java.time.Instant;

/**
 * 밴드가 앱에 기록해 둔 합주 일정. 실제 합주실 예약이 아니라 "이미 외부에서 잡은 예약의 기록"이다
 * (BUILD_PLAN 2장). 그래서 시간대가 겹치는 다른 일정이 있어도 저장을 막지 않는다 — 겹침은
 * {@code ReservationService}가 응답에 경고로만 싣는다.
 *
 * <p>상태 전이는 setter 가 아니라 의미 있는 메서드로만 한다:
 * {@link #approve()} / {@link #reject()} / {@link #cancel()} / {@link #revertToPending()}.
 * 시간·장소 교체는 {@link #reschedule}, 비고·비용은 {@link #changeDetails}.
 *
 * <p>{@code recurringRuleId}는 Phase 5 정기 일정에서만 채운다. Phase 4 경로에서는 항상 {@code null}이다.
 */
@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "band_id", nullable = false)
    private Long bandId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    /** 합주실 비용(원). 참고용 메모 성격이며 정산(Phase 7)의 입력이 아니다. */
    private Integer cost;

    @Column(length = 500)
    private String note;

    /** Phase 5 정기 일정 연결. Phase 4 에서는 항상 {@code null}. */
    @Column(name = "recurring_rule_id")
    private Long recurringRuleId;

    private Reservation(Long bandId, Long roomId, Long requestedBy, ReservationStatus status,
                        Instant startAt, Instant endAt, Integer cost, String note) {
        this.bandId = bandId;
        this.roomId = roomId;
        this.requestedBy = requestedBy;
        this.status = status;
        this.startAt = startAt;
        this.endAt = endAt;
        this.cost = cost;
        this.note = note;
    }

    /**
     * 새 일정. 초기 {@code status}는 호출 측이 밴드의 {@code reservationPermission}으로 결정해 넘긴다
     * ({@code CONFIRMED} 또는 {@code PENDING}).
     */
    public static Reservation create(long bandId, long roomId, long requestedBy, ReservationStatus status,
                                     Instant startAt, Instant endAt, Integer cost, String note) {
        return new Reservation(bandId, roomId, requestedBy, status, startAt, endAt, cost, note);
    }

    /**
     * 정기 규칙(Phase 5)이 만든 회차. {@code recurringRuleId}로 규칙과 이어지며 status 는 항상
     * {@code CONFIRMED}로 시작한다 — 규칙 등록 자체가 승인 행위이므로 회차마다 다시 승인받지 않는다.
     * 개별 회차의 수정/취소는 일반 일정과 똑같이 다루고, 규칙은 그대로 유지된다.
     */
    public static Reservation ofRecurringRule(long bandId, long roomId, long createdBy, long recurringRuleId,
                                              Instant startAt, Instant endAt, Integer cost, String note) {
        Reservation reservation = new Reservation(bandId, roomId, createdBy, ReservationStatus.CONFIRMED,
                startAt, endAt, cost, note);
        reservation.recurringRuleId = recurringRuleId;
        return reservation;
    }

    /** 승인 대기 → 확정. */
    public void approve() {
        this.status = ReservationStatus.CONFIRMED;
    }

    /** 승인 대기 → 거절. */
    public void reject() {
        this.status = ReservationStatus.REJECTED;
    }

    /**
     * 취소. 이미 취소돼 있으면 아무 일도 하지 않는다.
     *
     * @return 이번 호출에서 실제로 {@code CANCELLED}로 바뀌었으면 {@code true}
     *         (호출 측이 합주실 {@code usageCount} 감소를 이 결과에 묶어 멱등하게 처리한다)
     */
    public boolean cancel() {
        if (this.status == ReservationStatus.CANCELLED) {
            return false;
        }
        this.status = ReservationStatus.CANCELLED;
        return true;
    }

    /**
     * 확정 상태였던 일정의 핵심(시간·장소)이 바뀌어 다시 승인이 필요해졌을 때 호출한다.
     * {@code APPROVAL_REQUIRED} 밴드에서만 의미가 있다.
     */
    public void revertToPending() {
        this.status = ReservationStatus.PENDING;
    }

    /** 시간·장소 교체(PUT 전체 수정의 일부). 상태는 건드리지 않는다. */
    public void reschedule(long roomId, Instant startAt, Instant endAt) {
        this.roomId = roomId;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    /** 비용·비고 교체. */
    public void changeDetails(Integer cost, String note) {
        this.cost = cost;
        this.note = note;
    }

    public boolean isActive() {
        return status.isActive();
    }

    public boolean belongsTo(long bandId) {
        return this.bandId != null && this.bandId == bandId;
    }

    public boolean isRequestedBy(long userId) {
        return this.requestedBy != null && this.requestedBy == userId;
    }
}
