package com.yeka.bandapp.board.entity;

/** 신고 대상 종류. {@code targetId}는 종류에 따라 board_posts/media_attachments/users 중 하나를 가리킨다. */
public enum ReportTargetType {
    POST,
    MEDIA,
    USER
}
