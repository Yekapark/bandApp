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

    /**
     * 설정 행이 없을 때 기본값 행을 만든다. {@code push_enabled}·{@code reminder_offsets}는
     * 마이그레이션 V9 의 컬럼 DEFAULT(각각 {@code TRUE}, {@code '{60}'})를 쓴다 —
     * {@code app.notification.default-reminder-offsets}(기본 60)와 값을 맞춰 둔다.
     * {@code ON CONFLICT DO NOTHING}이라 동시 최초 접근 경합이 예외 없이 흡수된다
     * (그 뒤 호출 측이 {@code findById}로 확정 값을 읽는다).
     *
     * @return 새로 만들었으면 1, 이미 있었으면 0
     */
    @Transactional
    @Modifying
    @Query(value = """
            insert into notification_settings (user_id, created_at, updated_at)
            values (:userId, now(), now())
            on conflict (user_id) do nothing
            """, nativeQuery = true)
    int insertDefaultsIfAbsent(@Param("userId") long userId);
}
