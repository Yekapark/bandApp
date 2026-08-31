package com.yeka.bandapp.band.repository;

import com.yeka.bandapp.band.entity.BandMember;
import com.yeka.bandapp.band.entity.BandMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BandMemberRepository extends JpaRepository<BandMember, Long> {

    Optional<BandMember> findByBandIdAndUserIdAndLeftAtIsNull(Long bandId, Long userId);

    boolean existsByBandIdAndUserIdAndLeftAtIsNull(Long bandId, Long userId);

    List<BandMember> findByBandIdAndLeftAtIsNullOrderByJoinedAtAsc(Long bandId);

    long countByBandIdAndRoleAndLeftAtIsNull(Long bandId, BandMemberRole role);
}
