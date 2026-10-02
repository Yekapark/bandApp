package com.yeka.bandapp.band.repository;

import com.yeka.bandapp.band.entity.Band;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BandRepository extends JpaRepository<Band, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Band b where b.id = :id")
    Optional<Band> findByIdForUpdate(@Param("id") long id);

    /** 활성 멤버가 한 명도 없는 밴드(마지막 멤버가 탈퇴) — 빈 밴드 정리 배치가 쓴다(LAUNCH_REVIEW L7). */
    @Query("select b.id from Band b where not exists "
            + "(select m.id from BandMember m where m.bandId = b.id and m.leftAt is null) order by b.id")
    List<Long> findMemberlessBandIds();
}
