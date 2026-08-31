package com.yeka.bandapp.common.exception;

/**
 * 도메인 규칙 위반을 표현하는 예외. {@link GlobalExceptionHandler}에서 응답으로 변환된다.
 */
public class BusinessException extends RuntimeException {

    private final transient ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
