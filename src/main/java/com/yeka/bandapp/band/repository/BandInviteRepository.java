package com.yeka.bandapp.band.repository;

import com.yeka.bandapp.band.entity.BandInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface BandInviteRepository extends JpaRepository<BandInvite, Long> {

    Optional<BandInvite> findByCode(String code);

    boolean existsByCode(String code);

    Optional<BandInvite> findFirstByBandIdAndRevokedFalseOrderByCreatedAtDesc(Long bandId);

    /** 재발급/무효화 시 해당 밴드의 모든 활성 코드를 revoked 로 만든다. */
    @Modifying(clearAutomatically = true)
    @Query("update BandInvite i set i.revoked = true where i.bandId = :bandId and i.revoked = false")
    int revokeActiveByBandId(@Param("bandId") Long bandId);

    /**
     * 사용 횟수를 원자적으로 1 늘린다. revoked·만료·소진 상태면 0행을 반환한다.
     * 조건을 WHERE 에 담아 두어, READ COMMITTED 에서 동시 참여가 {@code maxUses}를 넘지 못한다.
     *
     * @return 갱신된 행 수 (1 = 성공, 0 = 거부/경합 패배)
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update BandInvite i
               set i.usedCount = i.usedCount + 1
             where i.id = :id
               and i.revoked = false
               and i.expiresAt > :now
               and (i.maxUses is null or i.usedCount < i.maxUses)
            """)
    int tryConsume(@Param("id") Long id, @Param("now") Instant now);
}
