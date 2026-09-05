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

    List<BandMember> findByBandIdAndLeftAtIsNullOrderByJoinedAtAsc(Long bandId);

    /** "내가 속한 밴드 목록"용. {@code ix_band_members_user_active} 부분 인덱스를 탄다. */
    List<BandMember> findByUserIdAndLeftAtIsNullOrderByJoinedAtAsc(Long userId);

    long countByBandIdAndRoleAndLeftAtIsNull(Long bandId, BandMemberRole role);

    long countByBandIdAndLeftAtIsNull(Long bandId);

    /** 밴드 삭제 정리 — 나간 멤버의 과거 행까지 전부 지운다. */
    @Modifying
    @Query("delete from BandMember m where m.bandId = :bandId")
    int deleteByBandId(@Param("bandId") long bandId);
}
