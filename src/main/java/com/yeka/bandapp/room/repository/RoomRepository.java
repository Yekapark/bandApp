package com.yeka.bandapp.room.repository;

import com.yeka.bandapp.room.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    /**
     * 밴드의 활성 합주실 목록. {@code usageCount} 내림차순(Phase 3 완료 기준)이며,
     * 동률일 때 순서가 흔들리지 않도록 {@code id} 오름차순을 2차 정렬 키로 둔다.
     */
    List<Room> findByBandIdAndDeletedAtIsNullOrderByUsageCountDescIdAsc(Long bandId);

    Optional<Room> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByBandIdAndNameAndDeletedAtIsNull(Long bandId, String name);
}
