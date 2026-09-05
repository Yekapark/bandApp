package com.yeka.bandapp.notification.repository;

import com.yeka.bandapp.notification.entity.NotificationDispatch;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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
            insert into notification_dispatches
                (user_id, type, target_id, variant, band_id, title, body, created_at)
            values (:userId, :type, :targetId, :variant, :bandId, :title, :body, now())
            on conflict (user_id, type, target_id, variant) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") long userId, @Param("type") String type,
                       @Param("targetId") long targetId, @Param("variant") int variant,
                       @Param("bandId") Long bandId, @Param("title") String title,
                       @Param("body") String body);

    /**
     * 알림 목록 한 페이지. 최신순(id 내림차순)이며 <b>문구가 있는 행만</b> 돌려준다 —
     * V11 이전에 쌓인 행은 보낸 문구가 없어 화면에 그릴 수 없다.
     *
     * @param cursorId 이 id 보다 작은 것만(첫 페이지는 {@code null})
     */
    @Query("""
            select d from NotificationDispatch d
            where d.userId = :userId and d.bandId = :bandId and d.title is not null
              and (:cursorId is null or d.id < :cursorId)
            order by d.id desc
            """)
    List<NotificationDispatch> findFeed(@Param("userId") long userId, @Param("bandId") long bandId,
                                        @Param("cursorId") Long cursorId, Pageable pageable);

    /**
     * 보관기한이 지난 발송 이력 정리. 리마인더 배치가 실행 끝에 호출한다(별도 배치를 늘리지 않는다).
     *
     * @return 지운 행 수
     */
    @Transactional
    @Modifying
    @Query("delete from NotificationDispatch d where d.createdAt < :threshold")
    int deleteOlderThan(@Param("threshold") Instant threshold);

    /**
     * 밴드 삭제 정리. {@code band_id} 는 V11 이 FK 없는 순수 {@code BIGINT} 로 추가한 컬럼이라
     * FK 를 훑는 방식으로는 빠진다 — 남겨두면 없어진 밴드의 알림이 사용자 피드에 계속 뜬다.
     */
    @Modifying
    @Query("delete from NotificationDispatch d where d.bandId = :bandId")
    int deleteByBandId(@Param("bandId") long bandId);
}
