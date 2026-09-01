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
 * 한 일정에 대한 한 멤버의 참석 응답. 일정이 만들어질 때 그 시점의 활성 밴드 멤버 전원에 대해
 * {@link #pending}으로 미리 생성된다(BUILD_PLAN Phase 6).
 *
 * <p>일정 생성 이후 밴드에 합류한 멤버는 초기 행이 없다 — 그 경우 참석 상태 변경 API 가
 * {@link #pending} 행을 만들어 바로 {@link #respond}한다(완료 기준: 나중에 합류한 멤버도 응답 가능).
 * {@code (reservation_id, user_id)} 유니크 인덱스가 멤버당 한 행을 보장한다.
 *
 * <p>상태 변경은 setter 가 아니라 {@link #respond}로만 한다.
 */
@Entity
@Table(name = "reservation_attendances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationAttendance extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    /** 본인이 ATTENDING/ABSENT 로 응답한 시각. 미응답(PENDING)이면 {@code null}. */
    @Column(name = "responded_at")
    private Instant respondedAt;

    private ReservationAttendance(Long reservationId, Long userId, AttendanceStatus status) {
        this.reservationId = reservationId;
        this.userId = userId;
        this.status = status;
    }

    /** 일정 생성 시(또는 뒤늦게 합류한 멤버의 첫 응답 직전) 만드는 초기 행. 아직 응답 전이라 시각은 없다. */
    public static ReservationAttendance pending(long reservationId, long userId) {
        return new ReservationAttendance(reservationId, userId, AttendanceStatus.PENDING);
    }

    /**
     * 본인 응답. status 를 바꾸고 응답 시각을 남긴다. 다시 {@code PENDING}(미응답)으로 되돌리면
     * 시각도 지운다.
     */
    public void respond(AttendanceStatus newStatus, Instant when) {
        this.status = newStatus;
        this.respondedAt = newStatus == AttendanceStatus.PENDING ? null : when;
    }

    public boolean isAttending() {
        return status == AttendanceStatus.ATTENDING;
    }
}
