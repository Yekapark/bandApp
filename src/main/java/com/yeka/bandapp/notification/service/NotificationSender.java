package com.yeka.bandapp.notification.service;

import com.yeka.bandapp.notification.entity.DeviceToken;
import com.yeka.bandapp.notification.entity.NotificationType;
import com.yeka.bandapp.notification.push.PushMessage;
import com.yeka.bandapp.notification.push.PushResult;
import com.yeka.bandapp.notification.push.PushSender;
import com.yeka.bandapp.notification.repository.DeviceTokenRepository;
import com.yeka.bandapp.notification.repository.NotificationDispatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 알림 발송 오케스트레이션 — 수신자 필터 → 멱등 이력 기록 → 푸시 → 무효 토큰 정리.
 *
 * <p><b>이 서비스에는 {@code @Transactional}을 붙이지 않는다.</b> FCM HTTP 호출이 트랜잭션 안에서
 * 일어나면 커넥션을 왕복 시간 동안 붙잡는다(CLAUDE.md 규칙) — {@code MediaAttachmentService}와 같은 이유.
 * DB 쓰기는 {@link NotificationDispatchRecorder}의 짧은 {@code REQUIRES_NEW} 트랜잭션으로만 한다
 * (트리거 알림은 {@code AFTER_COMMIT} 리스너에서 불리므로 일반 트랜잭션은 커밋되지 않고 사라진다).
 *
 * <p>멱등성은 {@code notification_dispatches} 유니크 제약이 보장한다: 이미 발송된 {@code (user, type,
 * target, variant)}는 이력 기록 단계에서 걸러진다. 그래서 배치 재실행·서버 재시작에도 같은 알림이
 * 두 번 나가지 않는다.
 */
@Service
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final NotificationDispatchRepository dispatchRepository;
    private final NotificationDispatchRecorder dispatchRecorder;
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationSettingService settingService;
    private final PushSender pushSender;

    public NotificationSender(NotificationDispatchRepository dispatchRepository,
                              NotificationDispatchRecorder dispatchRecorder,
                              DeviceTokenRepository deviceTokenRepository,
                              NotificationSettingService settingService,
                              PushSender pushSender) {
        this.dispatchRepository = dispatchRepository;
        this.dispatchRecorder = dispatchRecorder;
        this.deviceTokenRepository = deviceTokenRepository;
        this.settingService = settingService;
        this.pushSender = pushSender;
    }

    /**
     * 수신자들에게 알림을 보낸다.
     *
     * @return 이번 호출에서 <b>새로</b> 발송 처리한 수신자 수(이미 발송된 사람·푸시를 끈 사람은 제외).
     *         푸시가 미설정이거나 등록된 기기가 없어도 이력은 남고 이 수에 포함된다.
     */
    public int notify(NotificationType type, long targetId, int variant,
                      Collection<Long> recipientUserIds, PushMessage message) {
        if (recipientUserIds.isEmpty()) {
            return 0;
        }
        Set<Long> recipients = new LinkedHashSet<>(recipientUserIds);
        recipients.removeAll(settingService.pushDisabledUserIds(recipients));
        if (recipients.isEmpty()) {
            return 0;
        }

        List<Long> fresh = new ArrayList<>();
        for (Long userId : recipients) {
            if (dispatchRecorder.recordIfAbsent(type, targetId, variant, userId)) {
                fresh.add(userId);
            }
        }
        if (fresh.isEmpty()) {
            return 0;
        }

        pushToDevices(type, targetId, fresh, message);
        return fresh.size();
    }

    /** 보관기한이 지난 발송 이력 정리(리마인더 배치가 실행 끝에 호출). */
    public int purgeDispatchesBefore(Instant threshold) {
        return dispatchRepository.deleteOlderThan(threshold);
    }

    private void pushToDevices(NotificationType type, long targetId, List<Long> userIds, PushMessage message) {
        List<String> tokens = deviceTokenRepository.findByUserIdIn(userIds).stream()
                .map(DeviceToken::getToken)
                .toList();
        if (tokens.isEmpty() || !pushSender.isConfigured()) {
            return;
        }
        try {
            PushResult result = pushSender.send(message, tokens);
            if (!result.invalidTokens().isEmpty()) {
                dispatchRecorder.forgetTokens(result.invalidTokens());
            }
        } catch (RuntimeException e) {
            // 발송 실패는 이미 커밋된 본 작업을 되돌리지 않는다. 이력이 남았으므로 재시도되지는 않는다.
            log.warn("푸시 전송 실패 type={} targetId={}", type, targetId, e);
        }
    }
}
