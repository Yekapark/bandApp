package com.yeka.bandapp.common.exception;

import com.yeka.bandapp.common.response.ApiResponse;
import com.yeka.bandapp.common.response.ErrorPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.ErrorResponse;
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

    /**
     * 위에서 못 잡은 예외. 단, 스프링이 이미 4xx 로 분류해 둔 것({@link ErrorResponse} — 없는 주소
     * {@code NoResourceFoundException}, 안 받는 메서드 405, 지원 안 하는 Content-Type 415, 필수 헤더 누락,
     * 파라미터 검증 실패 등)은 그 상태 그대로 돌려준다. 이 분기가 없으면 전부 500 + ERROR 로그가 되어
     * 사용자는 "서버 오류" 를 보고, 스캐너가 긁는 없는 주소마다 에러 로그가 쌓여 진짜 장애가 묻힌다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        if (e instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = er.getStatusCode();
            ErrorCode code = switch (status.value()) {
                case 404 -> ErrorCode.NOT_FOUND;
                case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
                default -> ErrorCode.INVALID_INPUT;
            };
            return ResponseEntity.status(status)
                    .body(ApiResponse.fail(ErrorPayload.of(code.name(), code.defaultMessage())));
        }
        log.error("처리되지 않은 예외", e);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.status())
                .body(ApiResponse.fail(ErrorPayload.of(code.name(), code.defaultMessage())));
    }
}
