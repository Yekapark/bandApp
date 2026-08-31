package com.yeka.bandapp.user.repository;

import com.yeka.bandapp.user.entity.SocialProvider;
import com.yeka.bandapp.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    Optional<User> findByEmailAndSocialProviderIsNullAndDeletedAtIsNull(String email);

    boolean existsByEmailAndSocialProviderIsNullAndDeletedAtIsNull(String email);

    Optional<User> findBySocialProviderAndSocialIdAndDeletedAtIsNull(SocialProvider socialProvider, String socialId);

    /**
     * 파기 대상: 보관기간이 지난 탈퇴 계정 중 아직 익명화되지 않은 것.
     * 익명화 후에는 세 컬럼이 모두 NULL 이라 다음 실행에서 자연히 제외된다(별도 플래그 없이 멱등).
     */
    @Query("""
            select u from User u
            where u.deletedAt < :threshold
              and (u.email is not null or u.socialId is not null or u.passwordHash is not null)
            """)
    List<User> findPurgeTargets(@Param("threshold") Instant threshold, Pageable pageable);
}
