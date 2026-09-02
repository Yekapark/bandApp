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
     * 보관기한이 지난 발송 이력 정리. 리마인더 배치가 실행 끝에 호출한다(별도 배치를 늘리지 않는다).
     *
     * @return 지운 행 수
     */
    @Transactional
    @Modifying
    @Query("delete from NotificationDispatch d where d.createdAt < :threshold")
    int deleteOlderThan(@Param("threshold") Instant threshold);
}
