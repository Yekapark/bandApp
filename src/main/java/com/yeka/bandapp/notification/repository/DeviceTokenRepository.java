package com.yeka.bandapp.notification.repository;

import com.yeka.bandapp.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 디바이스 토큰 저장소. {@code DeviceTokenService}/{@code NotificationSender}는 외부 I/O(FCM)를
 * 트랜잭션 밖에 두려고 짧은 트랜잭션만 쓰므로, 삭제 쿼리에는 여기에 직접 {@code @Transactional}을 단다
 * ({@code MediaAttachmentRepository} 선례).
 */
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserIdIn(Collection<Long> userIds);

    @Transactional
    @Modifying
    @Query("delete from DeviceToken d where d.userId = :userId and d.token = :token")
    int deleteByUserIdAndToken(@Param("userId") long userId, @Param("token") String token);

    @Transactional
    @Modifying
    @Query("delete from DeviceToken d where d.userId = :userId")
    int deleteByUserId(@Param("userId") long userId);

    /** FCM 이 무효라고 응답한 토큰 정리. */
    @Transactional
    @Modifying
    @Query("delete from DeviceToken d where d.token in :tokens")
    int deleteByTokenIn(@Param("tokens") Collection<String> tokens);
}
