package com.yeka.bandapp.notification.service;

import com.yeka.bandapp.band.service.BandDirectoryService;
import com.yeka.bandapp.notification.entity.NotificationType;
import com.yeka.bandapp.plan.service.PlanDirectoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * PREMIUM 구독이 끝나기 전에 밴드장에게 미리 알린다.
 *
 * <p>이게 없으면 구독이 조용히 끝나고 유예 30일 뒤 사진·영상이 사라진다. 밴드원 입장에서는
 * 합주 영상이 예고 없이 없어지는 것이라, 요금제 기능 중 가장 먼저 갖춰야 할 안전장치다.
 *
 * <p><b>{@code @Transactional} 없음</b> — {@link NotificationSender}가 FCM HTTP 를 호출한다
 * ({@link ReminderService} 와 같은 이유. CLAUDE.md: 외부 HTTP 는 트랜잭션 밖에서).
 */
@Service
public class PlanExpiryReminderService {

    private static final Logger log = LoggerFactory.getLogger(PlanExpiryReminderService.class);

    /**
     * 며칠 전에 알릴지. 한 번 놓쳐도 다음 시점이 받아 주도록 넉넉히 벌려 둔다.
     * 발송 이력의 {@code variant} 에 이 값이 들어가 <b>시점마다 한 번씩만</b> 나간다.
     */
    static final int[] LEAD_DAYS = {30, 7, 1};

    /** 한 번 실행에서 훑을 밴드 수 상한. 하루 1회 도는 배치라 이 정도면 충분하다. */
    static final int PAGE_SIZE = 200;

    private final PlanDirectoryService planDirectory;
    private final BandDirectoryService bandDirectory;
    private final NotificationSender sender;

    public PlanExpiryReminderService(PlanDirectoryService planDirectory,
                                     BandDirectoryService bandDirectory,
                                     NotificationSender sender) {
        this.planDirectory = planDirectory;
        this.bandDirectory = bandDirectory;
        this.sender = sender;
    }

    /**
     * 각 시점(30·7·1일 전)마다 해당 구간에 드는 밴드를 찾아 밴드장에게 알린다.
     *
     * @return 이번 실행에서 새로 발송한 수신자 수
     */
    public int remindExpiringSoon(Instant now) {
        Instant horizon = now.plus(LEAD_DAYS[0], ChronoUnit.DAYS);
        int sent = 0;
        for (PlanDirectoryService.ExpiringBand band : planDirectory.premiumBandsExpiringBy(now, horizon, PAGE_SIZE)) {
            int bucket = bucketFor(now, band.expiresAt());
            try {
                sent += notifyLeaders(band.bandId(), bucket);
            } catch (RuntimeException e) {
                // 한 밴드의 실패가 나머지를 막지 않는다. 멱등이라 다음 실행이 다시 시도한다.
                log.warn("요금제 만료 예고 실패 bandId={} bucket={}", band.bandId(), bucket, e);
            }
        }
        return sent;
    }

    /**
     * 남은 일수를 {@link #LEAD_DAYS} 중 하나로 접는다. <b>가장 작은(=가장 급한) 구간</b>을 고른다.
     *
     * <p>구간을 하나만 고르는 게 핵심이다. "30일 안", "7일 안" 을 각각 조회하면 6일 남은 밴드가
     * 두 조회에 다 걸려 알림이 두 번 나간다(실제로 그렇게 만들었다가 테스트에서 잡혔다).
     * 날이 지나면서 구간이 30 → 7 → 1 로 바뀌고, {@code variant} 가 다르니 구간마다 한 번씩만 간다.
     * 배치가 하루 걸러도 다음 실행이 같은 구간을 집어내므로 놓치지 않는다.
     */
    static int bucketFor(Instant now, Instant expiresAt) {
        long daysLeft = ChronoUnit.DAYS.between(now, expiresAt);
        for (int i = LEAD_DAYS.length - 1; i >= 0; i--) {
            if (daysLeft <= LEAD_DAYS[i]) {
                return LEAD_DAYS[i];
            }
        }
        return LEAD_DAYS[0];
    }

    /** 만료 강등 직후 한 번. 유예가 끝나면 첨부가 사라진다는 것을 알린다. */
    public int notifyExpired(long bandId, int graceDays) {
        List<Long> leaders = bandDirectory.leaderUserIds(bandId);
        return sender.notify(NotificationType.PLAN_EXPIRED, bandId, 0, leaders,
                NotificationMessages.planExpired(bandId, graceDays));
    }

    /**
     * 밴드장에게만 보낸다 — 요금제를 바꿀 수 있는 유일한 사람이다.
     * 멤버에게도 알리면 할 수 있는 게 없는 알림이 되고, 밴드 홈 배너가 그 몫을 한다.
     */
    private int notifyLeaders(long bandId, int daysLeft) {
        List<Long> leaders = bandDirectory.leaderUserIds(bandId);
        if (leaders.isEmpty()) {
            return 0;
        }
        // targetId 는 밴드, variant 는 남은 일수 — (밴드, 30일 전) 조합이 한 번만 나가게 하는 키다.
        return sender.notify(NotificationType.PLAN_EXPIRING_SOON, bandId, daysLeft, leaders,
                NotificationMessages.planExpiringSoon(bandId, daysLeft));
    }
}
