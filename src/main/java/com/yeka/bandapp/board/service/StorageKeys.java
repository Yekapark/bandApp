package com.yeka.bandapp.board.service;

import java.util.UUID;

/**
 * R2 객체 키 생성(순수 함수). 사용자가 올린 파일명을 절대 쓰지 않는다 — 경로 탈출·유니코드·열거를
 * 막기 위해 밴드/게시글 경로 + 무작위 UUID 로만 만든다. 확장자도 붙이지 않는다(형식은 DB 의
 * {@code content_type}이 안다).
 */
public final class StorageKeys {

    private StorageKeys() {
    }

    public static String newMediaKey(long bandId, long postId) {
        return bandPrefix(bandId) + "posts/%d/%s".formatted(postId, UUID.randomUUID());
    }

    /**
     * 한 밴드의 모든 객체가 놓이는 접두사. 밴드 삭제가 이 접두사로 한 번에 지운다
     * ({@code StorageClient#deleteByPrefix}).
     *
     * <p>끝의 {@code /} 가 중요하다 — 없으면 {@code bands/1} 이 {@code bands/12} 까지 잡는다.
     */
    public static String bandPrefix(long bandId) {
        return "bands/%d/".formatted(bandId);
    }
}
