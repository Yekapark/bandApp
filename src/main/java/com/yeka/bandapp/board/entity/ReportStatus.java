package com.yeka.bandapp.board.entity;

/**
 * 신고 처리 상태. 접수 시 {@code OPEN}. {@code RESOLVED}로의 전이는 운영자 도구의 몫이며
 * BUILD_PLAN Phase 8 범위 밖이라 API 를 두지 않는다(컬럼만 유지).
 */
public enum ReportStatus {
    OPEN,
    RESOLVED
}
