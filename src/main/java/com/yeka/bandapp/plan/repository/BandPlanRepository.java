package com.yeka.bandapp.plan.repository;

import com.yeka.bandapp.plan.entity.BandPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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

    /**
     * 구독기간이 지난 PREMIUM 밴드의 id — 만료 강등 배치가 쓴다
     * ({@code MediaAttachmentRepository#findExpiredReady} 선례와 같은 페이징 조회).
     *
     * <p>{@code expires_at} 이 NULL 인 PREMIUM 은 {@code < :now} 비교에서 자연히 빠진다. 1년 구독에서는
     * 모든 PREMIUM 에 만료일이 찍히므로 정상적으로는 없는 상태지만, 데이터가 어긋났을 때
     * <b>남의 미디어를 실수로 만료시키지 않는 쪽</b>이 안전하다.
     *
     * <p>ponytail: 밴드당 1행이고 하루 1회 도는 배치라 {@code expires_at} 인덱스 없이 순차 스캔한다.
     * 밴드가 수만 개가 되면 {@code V8__board_media_report.sql} 의 부분 인덱스 선례대로 추가한다.
     */
    @Query("select p.bandId from BandPlan p "
            + "where p.tier = com.yeka.bandapp.plan.entity.PlanTier.PREMIUM and p.expiresAt < :now "
            + "order by p.expiresAt")
    List<Long> findExpiredPremiumBandIds(@Param("now") Instant now, Pageable pageable);
}
