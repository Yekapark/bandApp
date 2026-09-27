package com.yeka.bandapp.band.repository;

import com.yeka.bandapp.band.entity.Band;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BandRepository extends JpaRepository<Band, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Band b where b.id = :id")
    Optional<Band> findByIdForUpdate(@Param("id") long id);
}
