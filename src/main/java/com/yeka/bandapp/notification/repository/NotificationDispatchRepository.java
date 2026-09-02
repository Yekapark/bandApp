package com.yeka.bandapp.notification.repository;

import com.yeka.bandapp.notification.entity.NotificationDispatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface NotificationDispatchRepository extends JpaRepository<NotificationDispatch, Long> {

    /**
     * 이력을 남기되 이미 있으면 조용히 넘어간다 — 멱등 키. {@code ON CONFLICT DO NOTHING}이라
     * <b>유니크 충돌이 예외를 던지지 않는다</b>(그래서 트랜잭션이 rollback-only 로 오염되지 않는다).
     *
     * @return 새로 기록했으면 1, 이미 있었으면 0
     */
    @Transactional
    @Modifying
    @Query(value = """
            insert into notification_dispatches (user_id, type, target_id, variant, created_at)
            values (:userId, :type, :targetId, :variant, now())
            on conflict (user_id, type, target_id, variant) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") long userId, @Param("type") String type,
                       @Param("targetId") long targetId, @Param("variant") int variant);

    /**
     * 보관기한이 지난 발송 이력 정리. 리마인더 배치가 실행 끝에 호출한다(별도 배치를 늘리지 않는다).
     *
     * @return 지운 행 수
     */
    @Transactional
    @Modifying
    @Query("delete from NotificationDispatch d where d.createdAt < :threshold")
    int deleteOlderThan(@Param("threshold") Instant threshold);
}
