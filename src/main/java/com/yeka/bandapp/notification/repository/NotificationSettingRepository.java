package com.yeka.bandapp.notification.repository;

import com.yeka.bandapp.notification.entity.NotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    /** 발송 전 "푸시를 끈 사용자" 걸러내기 — 설정 행이 없으면 기본 on 이므로 여기 안 잡힌다. */
    List<NotificationSetting> findByUserIdInAndPushEnabledFalse(Collection<Long> userIds);

    /** 리마인더 배치가 대상 멤버들의 시점 배열을 한 번에 읽는다. */
    List<NotificationSetting> findByUserIdIn(Collection<Long> userIds);

    @Transactional
    @Modifying
    @Query("delete from NotificationSetting s where s.userId = :userId")
    int deleteByUserId(@Param("userId") long userId);
}
