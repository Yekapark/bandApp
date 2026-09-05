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
    BAND_NAME_MISMATCH(HttpStatus.BAD_REQUEST, "밴드 이름이 일치하지 않습니다."),
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
    ALREADY_BAND_MEMBER(HttpStatus.CONFLICT, "이미 이 밴드에 속해 있습니다."),

    // 합주실 (Phase 3)
    // 지오코딩 실패는 예외가 아니다(좌표 없이 등록 성공) — 그래서 에러코드가 없다.
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "합주실을 찾을 수 없습니다."),
    ROOM_NAME_DUPLICATED(HttpStatus.CONFLICT, "같은 이름의 합주실이 이미 있습니다."),

    // 일정 (Phase 4)
    // 시간대 겹침은 예외가 아니다(경고만 하고 등록은 성공) — 그래서 에러코드가 없다.
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."),
    INVALID_RESERVATION_PERIOD(HttpStatus.BAD_REQUEST, "종료 시각은 시작 시각보다 뒤여야 합니다."),
    NOT_RESERVATION_OWNER(HttpStatus.FORBIDDEN, "등록자 본인 또는 밴드장만 할 수 있는 작업입니다."),
    RESERVATION_NOT_PENDING(HttpStatus.CONFLICT, "승인 대기 중인 일정이 아닙니다."),
    RESERVATION_NOT_EDITABLE(HttpStatus.CONFLICT, "취소·거절된 일정은 수정할 수 없습니다."),
    RESERVATION_RANGE_TOO_WIDE(HttpStatus.BAD_REQUEST, "조회 기간이 너무 넓습니다. (최대 400일)"),

    // 정기 일정 (Phase 5)
    RECURRING_RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "정기 일정 규칙을 찾을 수 없습니다."),
    INVALID_RECURRING_TIME(HttpStatus.BAD_REQUEST, "종료 시각은 시작 시각보다 뒤여야 합니다."),
    INVALID_RECURRING_DATE_RANGE(HttpStatus.BAD_REQUEST, "종료일은 시작일과 같거나 뒤여야 합니다."),
    NOT_RECURRING_RULE_OWNER(HttpStatus.FORBIDDEN, "규칙 등록자 본인 또는 밴드장만 할 수 있는 작업입니다."),

    // 참석 체크(RSVP) · 셋리스트 (Phase 6)
    NOT_ATTENDANCE_OWNER(HttpStatus.FORBIDDEN, "본인의 참석 상태만 변경할 수 있습니다."),
    ATTENDANCE_UPDATE_CONFLICT(HttpStatus.CONFLICT, "참석 응답이 동시에 처리되었습니다. 다시 시도해 주세요."),
    SETLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "셋리스트 항목을 찾을 수 없습니다."),
    SETLIST_REORDER_MISMATCH(HttpStatus.BAD_REQUEST, "재정렬 목록이 현재 셋리스트 항목과 일치하지 않습니다."),
    SETLIST_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "한 일정의 셋리스트 항목 수 상한을 넘었습니다."),

    // 정산(N빵) (Phase 7)
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "정산 정보를 찾을 수 없습니다."),
    SETTLEMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 정산이 생성된 일정입니다. 재계산 API 를 사용하세요."),
    SETTLEMENT_NO_ATTENDEES(HttpStatus.CONFLICT, "참석자가 없어 참석자 기준 정산을 만들 수 없습니다."),
    NOT_SETTLEMENT_MANAGER(HttpStatus.FORBIDDEN, "일정 등록자 본인 또는 밴드장만 정산을 만들거나 재계산할 수 있습니다."),
    SETTLEMENT_SHARE_NOT_FOUND(HttpStatus.NOT_FOUND, "본인의 분담 항목이 없습니다."),
    NOT_SETTLEMENT_SHARE_OWNER(HttpStatus.FORBIDDEN, "본인의 납부 상태만 변경할 수 있습니다."),

    // 게시판·미디어·신고·차단 (Phase 8)
    // 파일 바이트는 서버를 지나지 않는다. 형식·크기 위반은 URL 발급 시(400) 걸러지고, 신고한 값과
    // 실제 업로드가 다른 경우는 완료 콜백에서(409) 거부되며 R2 객체와 레코드가 함께 삭제된다.
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    NOT_POST_OWNER(HttpStatus.FORBIDDEN, "작성자 본인 또는 밴드장만 할 수 있는 작업입니다."),
    POST_CURSOR_INVALID(HttpStatus.BAD_REQUEST, "목록 커서 형식이 올바르지 않습니다."),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "첨부 파일을 찾을 수 없습니다."),
    MEDIA_TYPE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    MEDIA_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "파일 크기 상한을 넘었습니다. (이미지 10MB, 영상 200MB)"),
    MEDIA_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "한 게시글에 첨부할 수 있는 파일 수를 넘었습니다."),
    MEDIA_NOT_PENDING(HttpStatus.CONFLICT, "이미 업로드가 완료되었거나 취소된 첨부입니다."),
    MEDIA_NOT_UPLOADED(HttpStatus.CONFLICT, "업로드된 파일이 없습니다. 업로드를 마친 뒤 다시 시도해 주세요."),
    MEDIA_SIZE_MISMATCH(HttpStatus.CONFLICT, "신고한 파일 크기와 실제 업로드된 크기가 다릅니다. 업로드가 취소되었습니다."),
    MEDIA_CONTENT_TYPE_MISMATCH(HttpStatus.CONFLICT, "신고한 파일 형식과 실제 업로드된 형식이 다릅니다. 업로드가 취소되었습니다."),
    MEDIA_STORAGE_ERROR(HttpStatus.BAD_GATEWAY, "파일 저장소와 통신하지 못했습니다."),
    MEDIA_STORAGE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "파일 업로드가 설정되지 않았습니다."),
    REPORT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "신고 대상을 찾을 수 없습니다."),
    CANNOT_REPORT_SELF(HttpStatus.BAD_REQUEST, "자기 자신은 신고할 수 없습니다."),
    REPORT_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 접수되어 처리 중인 신고입니다."),
    CANNOT_BLOCK_SELF(HttpStatus.BAD_REQUEST, "자기 자신은 차단할 수 없습니다."),
    ALREADY_BLOCKED(HttpStatus.CONFLICT, "이미 차단한 사용자입니다."),
    BLOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "차단하지 않은 사용자입니다."),

    // 알림 (Phase 9)
    // FCM 키 미설정은 예외가 아니다 — 푸시 발송만 조용히 건너뛰고 설정·토큰 API 는 정상 동작한다.
    INVALID_REMINDER_OFFSET(HttpStatus.BAD_REQUEST, "리마인더 시점은 1분 이상, 설정된 상한 이하의 값이어야 합니다."),
    TOO_MANY_REMINDER_OFFSETS(HttpStatus.BAD_REQUEST, "리마인더 시점을 너무 많이 지정했습니다."),
    DEVICE_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "등록되지 않은 디바이스 토큰입니다."),

    // 요금제 (Phase 10)
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "밴드의 요금제 정보를 찾을 수 없습니다."),
    PLAN_ALREADY_PREMIUM(HttpStatus.CONFLICT, "이미 프리미엄 요금제입니다."),
    PLAN_ALREADY_FREE(HttpStatus.CONFLICT, "이미 무료 요금제입니다."),
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "결제 처리에 실패했습니다."),

    // 요금제 쿠폰
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 쿠폰 코드입니다."),
    COUPON_EXPIRED(HttpStatus.GONE, "사용 기한이 지난 쿠폰입니다."),
    COUPON_EXHAUSTED(HttpStatus.CONFLICT, "모두 사용된 쿠폰입니다."),
    COUPON_ALREADY_USED(HttpStatus.CONFLICT, "이 밴드에서 이미 사용한 쿠폰입니다.");

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
