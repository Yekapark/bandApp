package com.yeka.bandapp.notification.service;

import com.yeka.bandapp.notification.entity.NotificationType;
import com.yeka.bandapp.notification.repository.DeviceTokenRepository;
import com.yeka.bandapp.notification.repository.NotificationDispatchRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * {@link NotificationSender}의 DB 쓰기(멱등 이력·무효 토큰 정리)를 담는 짧은 트랜잭션 조각.
 *
 * <p><b>{@code REQUIRES_NEW}인 이유</b> — 트리거 알림은 {@code @TransactionalEventListener(AFTER_COMMIT)}에서
 * 발송되는데, 그 시점에는 방금 커밋된 트랜잭션의 동기화가 아직 정리 중이라 일반 {@code @Transactional}
 * (REQUIRED)로 시작한 쓰기가 커밋되지 않고 사라진다. 새 트랜잭션을 강제해 이력을 확실히 남긴다.
 * 배치(트랜잭션 없는 컨텍스트)에서 불릴 때도 그냥 새 트랜잭션 하나가 열릴 뿐이라 문제없다.
 *
 * <p>이력 기록은 {@code INSERT … ON CONFLICT DO NOTHING}이라 유니크 충돌이 <b>예외를 던지지 않는다</b> —
 * {@code saveAndFlush} + {@code catch}로 처리하면 flush 실패가 트랜잭션을 rollback-only 로 만들어
 * {@code REQUIRES_NEW} 커밋 시 {@code UnexpectedRollbackException}이 나고, 한 수신자의 "이미 발송됨"이
 * 같은 {@code notify()} 호출의 나머지 수신자 처리를 통째로 중단시킨다.
 */
@Component
public class NotificationDispatchRecorder {

    private final NotificationDispatchRepository dispatchRepository;
    private final DeviceTokenRepository deviceTokenRepository;

    public NotificationDispatchRecorder(NotificationDispatchRepository dispatchRepository,
                                        DeviceTokenRepository deviceTokenRepository) {
        this.dispatchRepository = dispatchRepository;
        this.deviceTokenRepository = deviceTokenRepository;
    }

    /**
     * 아직 발송하지 않은 수신자면 이력을 남기고 {@code true}. 이미 발송된 조합이면 {@code false}
     * (충돌은 DB 가 흡수하므로 예외 없음).
     *
     * <p>{@code bandId}/{@code title}/{@code body}는 앱의 알림 목록에 그대로 쓰인다. 보낸 문구를
     * 그때 그대로 남겨 두면 나중에 일정이 바뀌거나 지워져도 알림은 온전히 남는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordIfAbsent(NotificationType type, long targetId, int variant, long userId,
                                  Long bandId, String title, String body) {
        return dispatchRepository.insertIfAbsent(
                userId, type.name(), targetId, variant, bandId, title, body) > 0;
    }

    /** FCM 이 무효라고 응답한 토큰 제거. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forgetTokens(Collection<String> tokens) {
        if (!tokens.isEmpty()) {
            deviceTokenRepository.deleteByTokenIn(tokens);
        }
    }
}
