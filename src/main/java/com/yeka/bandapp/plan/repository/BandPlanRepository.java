package com.yeka.bandapp.plan.repository;

import com.yeka.bandapp.plan.entity.BandPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 밴드 요금제 저장소. 밴드당 한 행이라 조회는 {@code band_id} 하나뿐이다.
 */
public interface BandPlanRepository extends JpaRepository<BandPlan, Long> {

    /** 조회용(잠금 없음). */
    Optional<BandPlan> findByBandId(long bandId);

    /**
     * 티어를 바꾸는 명령(구독/해지/갱신)용 — 행에 {@code PESSIMISTIC_WRITE}(=Postgres {@code SELECT … FOR UPDATE})를
     * 건다. 같은 밴드에 대한 동시 요청(구독 더블탭 등)을 직렬화해, 티어 전이와 그에 딸린 미디어 보관기한
     * 재계산이 한 번만 일어나게 한다({@code ReservationRepository#findByIdAndBandIdForUpdate} 선례).
     *
     * <p>트랜잭션 안에서만 호출한다({@code PlanMutationService} 의 각 메서드가 {@code @Transactional}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BandPlan p where p.bandId = :bandId")
    Optional<BandPlan> findByBandIdForUpdate(@Param("bandId") long bandId);
}
