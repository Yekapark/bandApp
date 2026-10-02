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
import org.springframework.data.jpa.repository.Modifying;

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

    /**
     * 구독기간이 {@code (now, until]} 사이에 <b>실제로 끝나는</b> PREMIUM 밴드 — 만료 예고 배치가 쓴다.
     *
     * <p>강등 배치의 {@code < :now} 와 겹치지 않도록 <b>아직 안 지난 것만</b> 고른다. 이미 지난 밴드는
     * 예고할 게 아니라 강등 대상이고, 강등 직후 별도 알림({@code PLAN_EXPIRED})이 나간다.
     *
     * <p><b>자동 갱신 중인 스토어 구독은 뺀다</b>({@code store} 있고 {@code subscriptionRef} 있음 =
     * {@link BandPlan#isAutoRenewingStoreSubscription()}). 만료일이 와도 Google 이 갱신해 끝나지 않는데,
     * 예전에는 "N일 뒤 끝나요, 사진·영상이 사라져요" 를 보내 겁을 줬다(LAUNCH_REVIEW B6). 남는 것은 해지
     * 예약한 구독({@code subscriptionRef} 없음)과 쿠폰 PREMIUM({@code store} 없음) — 정말로 끝나는 밴드다.
     */
    @Query("select p from BandPlan p "
            + "where p.tier = com.yeka.bandapp.plan.entity.PlanTier.PREMIUM "
            + "and p.expiresAt > :now and p.expiresAt <= :until "
            + "and (p.store is null or p.subscriptionRef is null or p.subscriptionRef like 'coupon-%') "
            + "order by p.expiresAt")
    List<BandPlan> findPremiumExpiringBetween(@Param("now") Instant now,
                                              @Param("until") Instant until,
                                              Pageable pageable);

    /**
     * 스토어 구매 토큰으로 밴드 id 를 찾는다 — RTDN 웹훅이 밴드를 특정할 때. 토큰은 FREE 로 내려온 뒤에도
     * 남으므로(보류·만료 후 복구) FREE 밴드도 찾는다. 한 토큰은 한 밴드에만 붙는다(StoreSubscriptionService.grantPremium).
     */
    @Query("select p.bandId from BandPlan p where p.purchaseToken = :token")
    Optional<Long> findBandIdByPurchaseToken(@Param("token") String token);

    /** 이 회원이 결제자로 적힌 요금제(탈퇴 때 자동 갱신 해지 대상 찾기 — LAUNCH_REVIEW B13). 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BandPlan p where p.purchasedByUserId = :userId")
    List<BandPlan> findByPurchaserForUpdate(@Param("userId") long userId);

    /**
     * 결제자가 탈퇴했는데 아직 연결이 남은 스토어 구독 = 해지가 아직 확인되지 않은 것(LAUNCH_REVIEW B16·B17).
     * 해지에 성공하면 연결을 끊으므로 정상이면 비어 있다. 잠그지 않는다 — 재시도 배치가 Play 호출 전에 읽기만 한다.
     */
    @Query("select p from BandPlan p where p.purchaseToken is not null and p.purchasedByUserId in "
            + "(select u.id from User u where u.deletedAt is not null)")
    List<BandPlan> findWithWithdrawnPurchaser();

    /** 밴드 삭제 정리. */
    @Modifying
    @Query("delete from BandPlan p where p.bandId = :bandId")
    int deleteByBandId(@Param("bandId") long bandId);
}
