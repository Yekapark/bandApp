package com.yeka.bandapp.common.response;

import java.util.List;

/**
 * 실패 응답 본문. {@code code}는 {@link com.yeka.bandapp.common.exception.ErrorCode} 이름,
 * {@code fieldErrors}는 검증 실패 시 필드별 사유.
 */
public record ErrorPayload(String code, String message, List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String reason) {}

    public static ErrorPayload of(String code, String message) {
        return new ErrorPayload(code, message, List.of());
    }
}
