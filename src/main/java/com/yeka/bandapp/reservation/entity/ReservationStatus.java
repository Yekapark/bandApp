package com.yeka.bandapp.reservation.entity;

/**
 * 일정의 등록 상태. <b>밴드 내부 일정으로서의 상태이지, 합주실의 실제 예약 상태가 아니다</b>
 * (이 앱은 예약을 대행하지 않는다 — BUILD_PLAN 2장).
 *
 * <p>등록 직후 값은 밴드 설정({@code Band.reservationPermission})에 따라 갈린다:
 * {@code LEADER_ONLY}/{@code ANYONE} → {@link #CONFIRMED}, {@code APPROVAL_REQUIRED} → {@link #PENDING}.
 */
public enum ReservationStatus {

    /** 밴드장 승인 대기. {@code APPROVAL_REQUIRED} 밴드에서 멤버가 등록하면 이 상태로 시작한다. */
    PENDING,

    /** 확정된 일정. 캘린더·정산의 대상이 된다. */
    CONFIRMED,

    /** 등록자 또는 밴드장이 취소함. 행은 남는다(과거 기록·정산 참조). */
    CANCELLED,

    /** 밴드장이 승인 요청을 거절함. */
    REJECTED;

    /** 아직 살아 있는 일정인지 — 겹침 경고와 캘린더 기본 조회의 대상. */
    public boolean isActive() {
        return this == PENDING || this == CONFIRMED;
    }
}
