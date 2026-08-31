package com.yeka.bandapp.band.entity;

/**
 * 일정 등록 권한 모드. 밴드 설정에서 밴드장이 선택한다. 기본값은 {@link #LEADER_ONLY}.
 *
 * <p>Phase 4에서 이 값에 따라 일정 등록 권한과 등록 직후 status(CONFIRMED / PENDING)가 갈린다.
 */
public enum ReservationPermission {

    /** 밴드장만 일정을 등록할 수 있다. */
    LEADER_ONLY,

    /** 모든 멤버가 등록할 수 있고 즉시 확정된다. */
    ANYONE,

    /** 모든 멤버가 등록을 신청할 수 있으나 밴드장 승인 전까지 대기 상태다. */
    APPROVAL_REQUIRED
}
