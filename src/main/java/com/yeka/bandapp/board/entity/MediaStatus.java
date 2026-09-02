package com.yeka.bandapp.board.entity;

/**
 * 첨부 미디어의 생애.
 *
 * <ul>
 *   <li>{@code PENDING} — 업로드 URL 발급 시 선생성. 아직 R2 에 실제 객체가 있는지 확인되지 않았다.</li>
 *   <li>{@code READY} — 완료 콜백에서 R2 HEAD 로 실제 크기·형식을 확인했다. 조회 시 presigned GET URL 을 발급한다.</li>
 *   <li>{@code EXPIRED} — 보관기한이 지났거나 게시글이 삭제돼 R2 객체가 더 이상 없다.</li>
 * </ul>
 */
public enum MediaStatus {
    PENDING,
    READY,
    EXPIRED
}
