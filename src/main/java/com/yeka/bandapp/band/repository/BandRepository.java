package com.yeka.bandapp.band.repository;

import com.yeka.bandapp.band.entity.Band;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BandRepository extends JpaRepository<Band, Long> {
}
