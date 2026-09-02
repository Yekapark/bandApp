package com.yeka.bandapp.notification.service;

import com.yeka.bandapp.band.service.BandDirectoryService;
import com.yeka.bandapp.notification.NotificationProperties;
import com.yeka.bandapp.notification.entity.NotificationType;
import com.yeka.bandapp.reservation.service.AttendanceService;
import com.yeka.bandapp.reservation.service.ReservationDirectoryService;
import com.yeka.bandapp.reservation.service.ReservationDirectoryService.UpcomingReservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 참석 미응답 독촉 로직. 스케줄러({@code AttendanceNudgeJob})가 {@link #runOnce}를 호출한다.
 *
 * <p>대상: {@code now}로부터 {@code nudgeLeadHours} 안에 시작하는 확정 일정의 미응답자
 * (= 현재 활성 밴드 멤버 − ATTENDING/ABSENT 응답자). 일정당 한 번({@code variant = 0}) — 멱등은
 * {@code notification_dispatches} 유니크 제약이 보장한다. {@code @Transactional} 없음(FCM 호출).
 */
@Service
public class AttendanceNudgeService {

    public static final int SCAN_LIMIT = 500;

    private static final Logger log = LoggerFactory.getLogger(AttendanceNudgeService.class);

    private final ReservationDirectoryService reservationDirectory;
    private final BandDirectoryService bandDirectory;
    private final AttendanceService attendanceService;
    private final NotificationSender sender;
    private final NotificationProperties properties;

    public AttendanceNudgeService(ReservationDirectoryService reservationDirectory,
                                  BandDirectoryService bandDirectory,
                                  AttendanceService attendanceService,
                                  NotificationSender sender,
                                  NotificationProperties properties) {
        this.reservationDirectory = reservationDirectory;
        this.bandDirectory = bandDirectory;
        this.attendanceService = attendanceService;
        this.sender = sender;
        this.properties = properties;
    }

    /** @return 새로 독촉을 보낸 수신자 수 */
    public int runOnce(Instant now) {
        Instant until = now.plus(Duration.ofHours(properties.nudgeLeadHours()));
        List<UpcomingReservation> upcoming = reservationDirectory.upcomingConfirmed(now, until, SCAN_LIMIT);
        int sent = 0;
        for (UpcomingReservation reservation : upcoming) {
            try {
                sent += nudgeOne(reservation);
            } catch (RuntimeException e) {
                log.warn("참석 독촉 발송 실패 reservationId={}", reservation.reservationId(), e);
            }
        }
        return sent;
    }

    private int nudgeOne(UpcomingReservation reservation) {
        List<Long> memberIds = bandDirectory.activeMemberUserIds(reservation.bandId());
        if (memberIds.isEmpty()) {
            return 0;
        }
        Set<Long> responded = attendanceService.respondedUserIds(reservation.reservationId());
        List<Long> nonResponders = memberIds.stream().filter(id -> !responded.contains(id)).toList();
        if (nonResponders.isEmpty()) {
            return 0;
        }
        return sender.notify(NotificationType.ATTENDANCE_NUDGE, reservation.reservationId(), 0, nonResponders,
                NotificationMessages.attendanceNudge(reservation.bandId(), reservation.reservationId(),
                        reservation.startAt()));
    }
}
