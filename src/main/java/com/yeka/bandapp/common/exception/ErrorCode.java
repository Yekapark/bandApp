package com.yeka.bandapp.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 비즈니스 예외 코드. 각 도메인 Phase에서 필요한 값을 추가한다.
 */
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),

    // 인증 / 인가 (Phase 1)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료되었습니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 유효하지 않습니다. 다시 로그인해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // 계정 (Phase 1)
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    ACCOUNT_WITHDRAWN(HttpStatus.UNAUTHORIZED, "탈퇴한 계정입니다."),

    // 카카오 연동 (Phase 1)
    KAKAO_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "카카오 토큰이 유효하지 않습니다."),
    KAKAO_APP_MISMATCH(HttpStatus.UNAUTHORIZED, "다른 앱에서 발급된 카카오 토큰입니다."),
    KAKAO_API_ERROR(HttpStatus.BAD_GATEWAY, "카카오 서버와 통신하지 못했습니다."),
    KAKAO_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "카카오 로그인이 설정되지 않았습니다."),

    // 밴드 / 멤버 (Phase 2)
    BAND_NOT_FOUND(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."),
    NOT_BAND_MEMBER(HttpStatus.FORBIDDEN, "밴드 멤버가 아닙니다."),
    NOT_BAND_LEADER(HttpStatus.FORBIDDEN, "밴드장만 할 수 있는 작업입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 밴드 멤버를 찾을 수 없습니다."),
    LEADER_MUST_DELEGATE_BEFORE_LEAVING(HttpStatus.CONFLICT, "밴드장은 다른 멤버에게 위임한 뒤에 탈퇴할 수 있습니다."),
    CANNOT_KICK_SELF(HttpStatus.BAD_REQUEST, "자기 자신은 추방할 수 없습니다."),
    CANNOT_DELEGATE_TO_SELF(HttpStatus.BAD_REQUEST, "자기 자신에게 위임할 수 없습니다."),

    // 초대코드 (Phase 2)
    INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 초대코드입니다."),
    INVITE_EXPIRED(HttpStatus.GONE, "만료된 초대코드입니다."),
    INVITE_REVOKED(HttpStatus.GONE, "무효화된 초대코드입니다."),
    INVITE_EXHAUSTED(HttpStatus.CONFLICT, "사용 가능 횟수를 모두 소진한 초대코드입니다."),
    ALREADY_BAND_MEMBER(HttpStatus.CONFLICT, "이미 이 밴드에 속해 있습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
