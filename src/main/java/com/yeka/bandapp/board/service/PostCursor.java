package com.yeka.bandapp.board.service;

import com.yeka.bandapp.board.entity.BoardPost;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * 게시글 목록의 커서(순수 함수). 정렬 키 {@code (createdAt DESC, id DESC)}의 마지막 항목을
 * {@code "<ISO-8601 instant>|<id>"}로 만들어 Base64URL 로 감싼다.
 *
 * <p>offset 페이징과 달리 스크롤 중 글이 삭제·추가돼도 항목이 누락·중복되지 않는다. 형식이 깨진
 * 커서는 400 {@code POST_CURSOR_INVALID}.
 */
public record PostCursor(Instant createdAt, long id) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public static PostCursor ofLast(BoardPost post) {
        return new PostCursor(post.getCreatedAt(), post.getId());
    }

    public String encode() {
        String raw = createdAt.toString() + '|' + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** {@code null}·공백이면 첫 페이지({@code null}). 형식 오류는 {@code POST_CURSOR_INVALID}. */
    public static PostCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String raw = new String(DECODER.decode(encoded), StandardCharsets.UTF_8);
            int sep = raw.lastIndexOf('|');
            if (sep < 0) {
                throw new BusinessException(ErrorCode.POST_CURSOR_INVALID);
            }
            return new PostCursor(Instant.parse(raw.substring(0, sep)),
                    Long.parseLong(raw.substring(sep + 1)));
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
            throw new BusinessException(ErrorCode.POST_CURSOR_INVALID);
        }
    }
}
