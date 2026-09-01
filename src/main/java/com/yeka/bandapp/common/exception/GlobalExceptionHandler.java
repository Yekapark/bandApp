package com.yeka.bandapp.common.exception;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.response.ErrorPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.errorCode();
        return ResponseEntity.status(code.status())
                .body(ApiResponse.fail(ErrorPayload.of(code.name(), e.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorPayload.FieldViolation> violations = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorPayload.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ErrorCode code = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(code.status())
                .body(ApiResponse.fail(new ErrorPayload(code.name(), code.defaultMessage(), violations)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        ErrorCode code = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(code.status())
                .body(ApiResponse.fail(ErrorPayload.of(code.name(), "요청 본문을 해석할 수 없습니다.")));
    }

    /**
     * 쿼리·경로 파라미터가 빠졌거나 타입이 안 맞을 때(예: {@code ?from=엉터리}, 오프셋 없는 날짜).
     * 이 매핑이 없으면 아래 {@link #handleUnexpected}가 먼저 잡아 500이 된다 — 400이 맞다.
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequestParam(Exception e) {
        ErrorCode code = ErrorCode.INVALID_INPUT;
        return ResponseEntity.status(code.status())
                .body(ApiResponse.fail(ErrorPayload.of(code.name(), "요청 파라미터가 올바르지 않습니다.")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        ErrorCode code = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(code.status())
                .body(ApiResponse.fail(ErrorPayload.of(code.name(), code.defaultMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.status())
                .body(ApiResponse.fail(ErrorPayload.of(code.name(), code.defaultMessage())));
    }
}
