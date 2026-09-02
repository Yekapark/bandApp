package com.yeka.bandapp.notification.service;

import com.yeka.bandapp.notification.entity.NotificationDispatch;
import com.yeka.bandapp.notification.entity.NotificationType;
import com.yeka.bandapp.notification.repository.DeviceTokenRepository;
import com.yeka.bandapp.notification.repository.NotificationDispatchRepository;
import org.springframework.dao.DataIntegrityViolationException;
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
 * <p>충돌(이미 발송)이 나면 그 한 건짜리 트랜잭션만 롤백되도록 <b>수신자당 한 번씩</b> 호출한다 —
 * 한 트랜잭션에서 여러 건을 시도하면 첫 충돌로 rollback-only 가 되어 나머지가 다 실패한다.
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
     * 아직 발송하지 않은 수신자면 이력을 남기고 {@code true}. 이미 발송된 조합이면 유니크 제약에 걸려
     * {@code false}(그 한 건 트랜잭션만 롤백).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordIfAbsent(NotificationType type, long targetId, int variant, long userId) {
        try {
            dispatchRepository.saveAndFlush(NotificationDispatch.of(userId, type, targetId, variant));
            return true;
        } catch (DataIntegrityViolationException alreadySent) {
            return false;
        }
    }

    /** FCM 이 무효라고 응답한 토큰 제거. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forgetTokens(Collection<String> tokens) {
        if (!tokens.isEmpty()) {
            deviceTokenRepository.deleteByTokenIn(tokens);
        }
    }
}
