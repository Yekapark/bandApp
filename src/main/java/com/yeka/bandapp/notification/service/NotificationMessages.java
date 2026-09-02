package com.yeka.bandapp.notification.service;

import com.yeka.bandapp.notification.entity.NotificationType;
import com.yeka.bandapp.notification.push.PushMessage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * 트리거별 푸시 문구·data 페이로드 조립(순수 함수). Docker 없이 단위 테스트한다.
 *
 * <p>{@code data}에는 항상 {@code type}·{@code bandId}·{@code reservationId}가 들어가, 클라이언트가
 * 알림을 눌렀을 때 해당 일정 화면으로 이동할 수 있다.
 */
public final class NotificationMessages {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN);

    private NotificationMessages() {
    }

    public static PushMessage reservationCreated(long bandId, long reservationId, Instant startAt) {
        return message(NotificationType.RESERVATION_CREATED, bandId, reservationId,
                "새 합주 일정", when(startAt) + " 합주 일정이 등록됐어요.");
    }

    public static PushMessage approvalRequested(long bandId, long reservationId, Instant startAt) {
        return message(NotificationType.RESERVATION_APPROVAL_REQUESTED, bandId, reservationId,
                "일정 승인 요청", when(startAt) + " 합주 일정 승인을 기다리고 있어요.");
    }

    public static PushMessage decision(long bandId, long reservationId, Instant startAt, boolean approved) {
        NotificationType type = approved ? NotificationType.RESERVATION_APPROVED : NotificationType.RESERVATION_REJECTED;
        String body = approved
                ? when(startAt) + " 합주 일정이 승인됐어요."
                : when(startAt) + " 합주 일정이 거절됐어요.";
        return message(type, bandId, reservationId, approved ? "일정 승인됨" : "일정 거절됨", body);
    }

    public static PushMessage cancelled(long bandId, long reservationId, Instant startAt) {
        return message(NotificationType.RESERVATION_CANCELLED, bandId, reservationId,
                "일정 취소", when(startAt) + " 합주 일정이 취소됐어요.");
    }

    public static PushMessage settlementRequested(long bandId, long reservationId, int totalAmount) {
        return message(NotificationType.SETTLEMENT_REQUESTED, bandId, reservationId,
                "정산 요청", "합주 비용 " + totalAmount + "원 정산이 등록됐어요. 납부를 확인해 주세요.");
    }

    public static PushMessage reminder(long bandId, long reservationId, Instant startAt, int offsetMinutes) {
        return message(NotificationType.RESERVATION_REMINDER, bandId, reservationId,
                "합주 리마인더", when(startAt) + " 합주가 " + offsetMinutes + "분 뒤 시작해요.");
    }

    public static PushMessage attendanceNudge(long bandId, long reservationId, Instant startAt) {
        return message(NotificationType.ATTENDANCE_NUDGE, bandId, reservationId,
                "참석 응답 부탁해요", when(startAt) + " 합주 참석 여부를 아직 응답하지 않았어요.");
    }

    private static PushMessage message(NotificationType type, long bandId, long reservationId,
                                       String title, String body) {
        return new PushMessage(title, body, Map.of(
                "type", type.name(),
                "bandId", Long.toString(bandId),
                "reservationId", Long.toString(reservationId)));
    }

    private static String when(Instant startAt) {
        return WHEN.format(startAt.atZone(SEOUL));
    }
}
