package com.yeka.bandapp.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 비즈니스 예외 코드. 각 도메인 Phase에서 필요한 값을 추가한다.
 */
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력한 값을 다시 확인해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦아요. 잠시 후 다시 시도해 주세요."),
    // 스프링이 컨트롤러에 닿기 전에 거절한 요청(없는 주소·안 받는 메서드). GlobalExceptionHandler.handleUnexpected 참조.
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없어요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청이에요."),

    // 인증 / 인가 (Phase 1)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않아요. 다시 로그인해 주세요."),
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료됐어요."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "로그인이 만료됐어요. 다시 로그인해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없어요."),

    // 계정 (Phase 1)
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 이메일이에요."),
    EMAIL_DOMAIN_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "메일을 받을 수 없는 주소예요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않아요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없어요."),
    ACCOUNT_WITHDRAWN(HttpStatus.UNAUTHORIZED, "탈퇴한 계정이에요."),
    /**
     * 이용 정지(약관 제14조). 401 인 이유 — 앱은 401 이면 토큰 갱신을 시도하고, 갱신도 이 코드로 거절되면
     * 로그아웃하면서 안내를 띄운다. 403 이면 로그인한 채로 모든 화면이 실패만 한다.
     */
    ACCOUNT_SUSPENDED(HttpStatus.UNAUTHORIZED, "이용이 정지된 계정이에요."),
    PASSWORD_RESET_CODE_INVALID(HttpStatus.BAD_REQUEST, "인증번호가 맞지 않거나 만료됐어요."),
    EMAIL_VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "인증번호가 맞지 않거나 만료됐어요."),

    // 카카오 연동 (Phase 1)
    KAKAO_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "카카오 로그인 정보를 확인하지 못했어요. 다시 시도해 주세요."),
    KAKAO_APP_MISMATCH(HttpStatus.UNAUTHORIZED, "카카오 로그인 정보를 확인하지 못했어요. 다시 시도해 주세요."),
    KAKAO_API_ERROR(HttpStatus.BAD_GATEWAY, "카카오 서버와 연결하지 못했어요. 잠시 후 다시 시도해 주세요."),
    KAKAO_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "지금은 카카오 로그인을 쓸 수 없어요."),

    // 밴드 / 멤버 (Phase 2)
    BAND_NOT_FOUND(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없어요."),
    BAND_NAME_MISMATCH(HttpStatus.BAD_REQUEST, "밴드 이름이 맞지 않아요."),
    BAND_HAS_ACTIVE_SUBSCRIPTION(HttpStatus.CONFLICT, "자동 갱신 중인 구독이 있어 밴드를 삭제할 수 없어요. 결제한 Google 계정의 Play 스토어에서 먼저 구독을 해지해 주세요."),
    NOT_BAND_MEMBER(HttpStatus.FORBIDDEN, "이 밴드의 멤버가 아니에요."),
    NOT_BAND_LEADER(HttpStatus.FORBIDDEN, "밴드장만 할 수 있어요."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "멤버를 찾을 수 없어요."),
    LEADER_MUST_DELEGATE_BEFORE_LEAVING(HttpStatus.CONFLICT, "밴드장은 다른 멤버에게 밴드장을 넘긴 뒤 나갈 수 있어요."),
    CANNOT_KICK_SELF(HttpStatus.BAD_REQUEST, "자기 자신은 내보낼 수 없어요."),
    CANNOT_DELEGATE_TO_SELF(HttpStatus.BAD_REQUEST, "자기 자신에게는 밴드장을 넘길 수 없어요."),

    // 초대코드 (Phase 2)
    INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 초대코드예요."),
    INVITE_EXPIRED(HttpStatus.GONE, "기한이 지난 초대코드예요."),
    INVITE_REVOKED(HttpStatus.GONE, "더 이상 쓸 수 없는 초대코드예요."),
    INVITE_EXHAUSTED(HttpStatus.CONFLICT, "사용 횟수를 다 쓴 초대코드예요."),
    ALREADY_BAND_MEMBER(HttpStatus.CONFLICT, "이미 이 밴드의 멤버예요."),

    // 합주실 (Phase 3)
    // 지오코딩 실패는 예외가 아니다(좌표 없이 등록 성공) — 그래서 에러코드가 없다.
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "합주실을 찾을 수 없어요."),
    ROOM_NAME_DUPLICATED(HttpStatus.CONFLICT, "같은 이름의 합주실이 이미 있어요."),

    // 일정 (Phase 4)
    // 시간대 겹침은 예외가 아니다(경고만 하고 등록은 성공) — 그래서 에러코드가 없다.
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "일정을 찾을 수 없어요."),
    INVALID_RESERVATION_PERIOD(HttpStatus.BAD_REQUEST, "종료 시각은 시작 시각보다 뒤여야 해요."),
    RESERVATION_TOO_LONG(HttpStatus.BAD_REQUEST, "일정은 24시간까지 등록할 수 있어요."),
    NOT_RESERVATION_OWNER(HttpStatus.FORBIDDEN, "등록한 사람이나 밴드장만 할 수 있어요."),
    RESERVATION_NOT_PENDING(HttpStatus.CONFLICT, "승인 대기 중인 일정이 아니에요."),
    RESERVATION_NOT_EDITABLE(HttpStatus.CONFLICT, "취소·거절된 일정은 수정할 수 없어요."),
    RESERVATION_RANGE_TOO_WIDE(HttpStatus.BAD_REQUEST, "조회 기간이 너무 길어요. (최대 400일)"),

    // 정기 일정 (Phase 5)
    RECURRING_RULE_NOT_FOUND(HttpStatus.NOT_FOUND, "정기 일정을 찾을 수 없어요."),
    INVALID_RECURRING_TIME(HttpStatus.BAD_REQUEST, "종료 시각은 시작 시각보다 뒤여야 해요."),
    INVALID_RECURRING_DATE_RANGE(HttpStatus.BAD_REQUEST, "종료일은 시작일과 같거나 뒤여야 해요."),
    NOT_RECURRING_RULE_OWNER(HttpStatus.FORBIDDEN, "등록한 사람이나 밴드장만 할 수 있어요."),

    // 참석 체크(RSVP) · 셋리스트 (Phase 6)
    NOT_ATTENDANCE_OWNER(HttpStatus.FORBIDDEN, "본인의 참석 여부만 바꿀 수 있어요."),
    ATTENDANCE_UPDATE_CONFLICT(HttpStatus.CONFLICT, "참석 응답이 겹쳐서 저장하지 못했어요. 다시 시도해 주세요."),
    SETLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "곡을 찾을 수 없어요."),
    SETLIST_REORDER_MISMATCH(HttpStatus.BAD_REQUEST, "곡 목록이 바뀌었어요. 화면을 새로고침한 뒤 다시 시도해 주세요."),
    SETLIST_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "한 합주에 넣을 수 있는 곡 수를 넘었어요."),

    // 정산(N빵) (Phase 7)
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "정산을 찾을 수 없어요."),
    SETTLEMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 정산이 있는 일정이에요. 정산 화면에서 다시 계산해 주세요."),
    SETTLEMENT_RESERVATION_NOT_CONFIRMED(HttpStatus.CONFLICT, "확정된 일정만 정산할 수 있어요."),
    SETTLEMENT_NO_ATTENDEES(HttpStatus.CONFLICT, "참석자가 없어서 참석자 기준으로 나눌 수 없어요."),
    SETTLEMENT_TOTAL_BELOW_PAID(HttpStatus.CONFLICT, "빠진 멤버가 이미 낸 금액보다 총액을 작게 할 수 없어요."),
    NOT_SETTLEMENT_MANAGER(HttpStatus.FORBIDDEN, "일정을 등록한 사람이나 밴드장만 정산을 만들거나 다시 계산할 수 있어요."),
    SETTLEMENT_SHARE_NOT_FOUND(HttpStatus.NOT_FOUND, "이 정산에 그 멤버의 몫이 없어요."),
    NOT_SETTLEMENT_SHARE_OWNER(HttpStatus.FORBIDDEN, "본인의 납부 상태만 바꿀 수 있어요."),
    SETTLEMENT_SHARE_NOT_EXEMPTABLE(HttpStatus.CONFLICT, "밴드를 나간 멤버의 미납 몫만 면제할 수 있어요."),
    SETTLEMENT_TOTAL_TOO_LARGE(HttpStatus.BAD_REQUEST, "정산 총액은 1,000만 원까지 입력할 수 있어요."),

    // 게시판·미디어·신고·차단 (Phase 8)
    // 파일 바이트는 서버를 지나지 않는다. 형식·크기 위반은 URL 발급 시(400) 걸러지고, 신고한 값과
    // 실제 업로드가 다른 경우는 완료 콜백에서(409) 거부되며 R2 객체와 레코드가 함께 삭제된다.
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없어요."),
    NOT_POST_OWNER(HttpStatus.FORBIDDEN, "작성자나 밴드장만 할 수 있어요."),
    POST_CURSOR_INVALID(HttpStatus.BAD_REQUEST, "목록을 불러오지 못했어요. 새로고침해 주세요."),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND, "첨부 파일을 찾을 수 없어요."),
    MEDIA_TYPE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식이에요."),
    MEDIA_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "파일이 너무 커요. (이미지 10MB, 영상 200MB)"),
    MEDIA_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "한 글에 첨부할 수 있는 파일 수를 넘었어요."),
    MEDIA_NOT_PENDING(HttpStatus.CONFLICT, "이미 올렸거나 취소된 첨부예요."),
    MEDIA_NOT_UPLOADED(HttpStatus.CONFLICT, "파일이 아직 올라가지 않았어요. 다시 시도해 주세요."),
    MEDIA_SIZE_MISMATCH(HttpStatus.CONFLICT, "올린 파일의 크기가 달라 업로드를 취소했어요. 다시 올려 주세요."),
    MEDIA_CONTENT_TYPE_MISMATCH(HttpStatus.CONFLICT, "올린 파일의 형식이 달라 업로드를 취소했어요. 다시 올려 주세요."),
    MEDIA_STORAGE_ERROR(HttpStatus.BAD_GATEWAY, "파일 저장소와 연결하지 못했어요. 잠시 후 다시 시도해 주세요."),
    MEDIA_STORAGE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "지금은 파일을 올릴 수 없어요."),
    REPORT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "신고할 대상을 찾을 수 없어요."),
    CANNOT_REPORT_SELF(HttpStatus.BAD_REQUEST, "자기 자신은 신고할 수 없어요."),
    REPORT_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 접수돼 처리 중인 신고예요."),
    CANNOT_BLOCK_SELF(HttpStatus.BAD_REQUEST, "자기 자신은 차단할 수 없어요."),
    ALREADY_BLOCKED(HttpStatus.CONFLICT, "이미 차단한 사용자예요."),
    BLOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "차단하지 않은 사용자예요."),

    // 알림 (Phase 9)
    // FCM 키 미설정은 예외가 아니다 — 푸시 발송만 조용히 건너뛰고 설정·토큰 API 는 정상 동작한다.
    INVALID_REMINDER_OFFSET(HttpStatus.BAD_REQUEST, "리마인더 시점이 올바르지 않아요."),
    TOO_MANY_REMINDER_OFFSETS(HttpStatus.BAD_REQUEST, "리마인더 시점을 너무 많이 골랐어요."),
    DEVICE_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "등록되지 않은 기기예요."),

    // 요금제 (Phase 10)
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "밴드의 요금제 정보를 찾을 수 없어요."),
    PLAN_ALREADY_PREMIUM(HttpStatus.CONFLICT, "이미 프리미엄이에요."),
    PLAN_ALREADY_FREE(HttpStatus.CONFLICT, "이미 무료 요금제예요."),
    PLAN_ALREADY_CANCELED(HttpStatus.CONFLICT, "이미 해지 예약된 요금제예요."),
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "결제를 처리하지 못했어요."),
    PURCHASE_NOT_VERIFIED(HttpStatus.PAYMENT_REQUIRED, "구매를 확인하지 못했어요. 결제가 끝난 뒤 잠시 후 다시 시도해 주세요."),
    PURCHASE_ALREADY_LINKED(HttpStatus.CONFLICT, "이 구매는 이미 다른 밴드에 연결돼 있어요."),
    BAND_ALREADY_SUBSCRIBED(HttpStatus.CONFLICT, "이 밴드에는 아직 끝나지 않은 구독이 있어요(결제 보류 중이거나, 해지했지만 기간이 남았어요). 보류 중이면 결제한 Google 계정의 Play 스토어에서 결제 수단을 고쳐 주세요. 방금 결제한 금액은 자동으로 환불돼요."),
    PURCHASE_BAND_MISMATCH(HttpStatus.CONFLICT, "이 구매는 다른 밴드의 요금제로 결제됐어요."),
    PURCHASE_BAND_UNKNOWN(HttpStatus.UNPROCESSABLE_ENTITY, "어느 밴드의 구매인지 알 수 없어요. 결제한 밴드의 요금제 화면에서 다시 시도해 주세요."),
    PLAN_REQUIRED(HttpStatus.FORBIDDEN, "프리미엄에서만 쓸 수 있는 기능이에요."),

    // 요금제 쿠폰
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 쿠폰 코드예요."),
    COUPON_EXPIRED(HttpStatus.GONE, "사용 기한이 지난 쿠폰이에요."),
    COUPON_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 쿠폰 기간을 구독에 더할 수 없어요. 잠시 후 다시 시도해 주세요."),
    COUPON_STORE_REJECTED(HttpStatus.CONFLICT, "이 구독에는 쿠폰 기간을 더할 수 없어요. Google Play 구독 상태를 확인해 주세요."),
    COUPON_EXHAUSTED(HttpStatus.CONFLICT, "모두 사용된 쿠폰이에요."),
    COUPON_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용한 쿠폰이에요.");

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
