package com.yeka.bandapp.band.repository;

import com.yeka.bandapp.band.entity.BandMember;
import com.yeka.bandapp.band.entity.BandMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BandMemberRepository extends JpaRepository<BandMember, Long> {

    Optional<BandMember> findByBandIdAndUserIdAndLeftAtIsNull(Long bandId, Long userId);

    boolean existsByBandIdAndUserIdAndLeftAtIsNull(Long bandId, Long userId);

    /** 두 회원이 지금 같은 밴드에 함께 있는가(둘 다 활성 멤버). */
    @Query("select count(a) > 0 from BandMember a, BandMember b where a.bandId = b.bandId "
            + "and a.userId = :userId and b.userId = :otherUserId and a.leftAt is null and b.leftAt is null")
    boolean shareActiveBand(@Param("userId") long userId, @Param("otherUserId") long otherUserId);

    List<BandMember> findByBandIdAndLeftAtIsNullOrderByJoinedAtAsc(Long bandId);

    /** "내가 속한 밴드 목록"용. {@code ix_band_members_user_active} 부분 인덱스를 탄다. */
    List<BandMember> findByUserIdAndLeftAtIsNullOrderByJoinedAtAsc(Long userId);

    /** 잠금 대기 전에 멤버 엔티티를 읽지 않는다. 여러 밴드는 항상 id 순서로 잠근다. */
    @Query("select m.bandId from BandMember m where m.userId = :userId and m.leftAt is null order by m.bandId")
    List<Long> findActiveBandIdsForWithdrawal(@Param("userId") long userId);

    long countByBandIdAndRoleAndLeftAtIsNull(Long bandId, BandMemberRole role);

    long countByBandIdAndLeftAtIsNull(Long bandId);

    /** 밴드 삭제 정리 — 나간 멤버의 과거 행까지 전부 지운다. */
    @Modifying
    @Query("delete from BandMember m where m.bandId = :bandId")
    int deleteByBandId(@Param("bandId") long bandId);
}
