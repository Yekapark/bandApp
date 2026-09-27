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

    /**
     * 밴드 생성·가입과 계정 삭제를 직렬화한다. NO KEY UPDATE 는 FK 검증의 KEY SHARE 와 호환되어,
     * 두 멤버가 동시에 탈퇴하면서 서로에게 밴드장을 넘길 때 사용자 행과 밴드 행의 교착을 막는다.
     */
    @Query(value = "select * from users where id = :id and deleted_at is null for no key update", nativeQuery = true)
    Optional<User> findActiveByIdForUpdate(@Param("id") long id);

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
