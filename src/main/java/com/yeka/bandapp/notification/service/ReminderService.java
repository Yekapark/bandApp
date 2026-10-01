package com.yeka.bandapp.notification.service;

import com.yeka.bandapp.band.service.BandDirectoryService;
import com.yeka.bandapp.notification.NotificationProperties;
import com.yeka.bandapp.notification.entity.NotificationType;
import com.yeka.bandapp.reservation.service.ReservationDirectoryService;
import com.yeka.bandapp.reservation.service.ReservationDirectoryService.UpcomingReservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 일정 리마인더 발송 로직. 스케줄러({@code ReservationReminderJob})가 {@link #runOnce}를 호출한다.
 *
 * <p><b>{@code @Transactional} 없음</b> — {@link NotificationSender}가 FCM HTTP 를 호출하기 때문이다
 * ({@code MediaMaintenanceService}와 같은 이유).
 *
 * <p>"직전 실행 이후"라는 시간 창을 계산하지 않고, {@code notification_dispatches} 유니크 제약(멱등 키)에
 * 중복 방지를 맡긴다. offset 별로 {@code variant = offset}으로 기록되므로, 사용자가 여러 시점을 지정하면
 * 각 시점마다 정확히 한 번씩 발송된다. 서버가 잠깐 멈춰 실행을 걸러도 복구 후 한 번은 나간다.
 */
@Service
public class ReminderService {

    /** 한 번의 실행이 훑는 최대 일정 수(그 이후는 다음 실행이 이어 처리). */
    public static final int SCAN_LIMIT = 500;

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final ReservationDirectoryService reservationDirectory;
    private final BandDirectoryService bandDirectory;
    private final NotificationSettingService settingService;
    private final NotificationSender sender;
    private final NotificationProperties properties;

    public ReminderService(ReservationDirectoryService reservationDirectory,
                           BandDirectoryService bandDirectory,
                           NotificationSettingService settingService,
                           NotificationSender sender,
                           NotificationProperties properties) {
        this.reservationDirectory = reservationDirectory;
        this.bandDirectory = bandDirectory;
        this.settingService = settingService;
        this.sender = sender;
        this.properties = properties;
    }

    /**
     * {@code now} 기준으로 발송 시점이 도래한 리마인더를 모두 보낸다.
     *
     * @return 새로 발송 처리한 (수신자 × 시점) 건수
     */
    public int runOnce(Instant now) {
        Instant horizon = now.plus(Duration.ofMinutes(properties.maxReminderOffsetMinutes()));
        List<UpcomingReservation> upcoming = reservationDirectory.upcomingConfirmed(now, horizon, SCAN_LIMIT);
        int sent = 0;
        for (UpcomingReservation reservation : upcoming) {
            try {
                sent += remindOne(reservation, now);
            } catch (RuntimeException e) {
                // 한 일정의 실패가 나머지를 막지 않는다.
                log.warn("리마인더 발송 실패 reservationId={}", reservation.reservationId(), e);
            }
        }
        return sent;
    }

    private int remindOne(UpcomingReservation reservation, Instant now) {
        List<Long> memberIds = bandDirectory.activeMemberUserIds(reservation.bandId());
        if (memberIds.isEmpty()) {
            return 0;
        }
        Map<Long, int[]> offsetsByUser = settingService.reminderOffsetsFor(memberIds);

        // 발송 시점이 도래한 (offset -> 수신자) 묶음. offset 이 곧 dispatch variant 다.
        //
        // 사람마다 **도래한 시점 중 시작에 가장 가까운 것 하나만** 보낸다. 예전에는 도래한 시점을 전부 보내서, 시작 10분 전에
        // 일정을 만들거나(또는 시점을 새로 켜거나) 하면 6시간·3시간·1시간·30분·10분 전 알림이 한꺼번에 쏟아졌다(테스터 의견
        // 2026-10-01). 지나간 먼 시점은 이제 의미가 없다. 서버가 잠깐 멈췄다 돌아와도 가장 가까운 것 하나는 나간다.
        Map<Integer, List<Long>> dueByOffset = new HashMap<>();
        for (Long userId : memberIds) {
            int nearest = Integer.MAX_VALUE;
            for (int offset : offsetsByUser.getOrDefault(userId, properties.defaultReminderOffsetsParsed())) {
                Instant fireAt = reservation.startAt().minus(Duration.ofMinutes(offset));
                if (!fireAt.isAfter(now) && offset < nearest) {
                    nearest = offset;
                }
            }
            if (nearest != Integer.MAX_VALUE) {
                dueByOffset.computeIfAbsent(nearest, key -> new ArrayList<>()).add(userId);
            }
        }

        int sent = 0;
        for (Map.Entry<Integer, List<Long>> entry : dueByOffset.entrySet()) {
            int offset = entry.getKey();
            sent += sender.notify(NotificationType.RESERVATION_REMINDER, reservation.reservationId(), offset,
                    entry.getValue(),
                    NotificationMessages.reminder(reservation.bandId(), reservation.reservationId(),
                            reservation.startAt(), leadMinutes(offset, reservation.startAt(), now)));
        }
        return sent;
    }

    /**
     * 문구에 쓸 "몇 분 뒤". 시점(offset)이 아니라 실제 남은 시간이다 — 시작 5분 전에 만든 일정에 "1시간 뒤 시작해요" 가
     * 가면 안 된다. 정시 발송이면 offset 과 같다(배치 지연 몇 초는 반올림으로 흡수). 최소 1분.
     */
    static int leadMinutes(int offset, Instant startAt, Instant now) {
        long remaining = Math.round(Duration.between(now, startAt).toSeconds() / 60.0);
        return (int) Math.max(1, Math.min(offset, remaining));
    }
}
