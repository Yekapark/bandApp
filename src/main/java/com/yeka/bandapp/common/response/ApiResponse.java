package com.yeka.bandapp.common.response;

/**
 * 모든 API 공통 응답 포맷.
 * 성공 시 {@code success=true, data=결과, error=null},
 * 실패 시 {@code success=false, data=null, error=상세}.
 */
public record ApiResponse<T>(boolean success, T data, ErrorPayload error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(ErrorPayload error) {
        return new ApiResponse<>(false, null, error);
    }
}
