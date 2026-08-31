package com.yeka.bandapp.band.entity;

/**
 * 밴드 내 역할. 전역 롤이 아니라 밴드별 역할이므로 Spring Security authority 로 쓰지 않고
 * 서비스 레이어에서 검증한다.
 */
public enum BandMemberRole {
    LEADER,
    MEMBER
}
