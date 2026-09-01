package com.yeka.bandapp.reservation.repository;

import com.yeka.bandapp.reservation.entity.Reservation;
import com.yeka.bandapp.reservation.entity.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** 상세·목록 조회용(잠금 없음). 경로의 {@code bandId}와 대조해 타 밴드 일정 접근을 차단한다. */
    Optional<Reservation> findByIdAndBandId(Long id, Long bandId);

    /**
     * 상태를 바꾸는 명령(승인·거절·수정·취소)용 — 행에 {@code PESSIMISTIC_WRITE}(=Postgres {@code SELECT … FOR UPDATE})를
     * 건다. 같은 일정에 대한 동시 요청(예: 취소 더블탭, 거절과 취소가 겹침)을 직렬화해, 상태 전이가 한 번만
     * 일어나고 그에 따른 합주실 {@code usageCount} 증감도 한 번만 반영되게 한다.
     *
     * <p>트랜잭션 안에서만 호출해야 한다(호출 측 서비스 메서드가 {@code @Transactional}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id and r.bandId = :bandId")
    Optional<Reservation> findByIdAndBandIdForUpdate(@Param("id") long id, @Param("bandId") long bandId);

    /**
     * 캘린더 조회: 밴드의 일정 중 반열림 구간 {@code [from, to)}와 조금이라도 겹치는 것.
     *
     * <p>파라미터 순서 주의 — 구간 겹침 조건은 {@code startAt < to AND endAt > from}이라
     * {@code StartAtLessThan}에 {@code to}, {@code EndAtGreaterThan}에 {@code from}이 들어간다.
     * {@code statuses}로 활성(PENDING·CONFIRMED)만 볼지 전부 볼지를 호출 측이 정한다.
     */
    List<Reservation> findByBandIdAndStatusInAndStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(
            Long bandId, Collection<ReservationStatus> statuses, Instant to, Instant from);

    /**
     * 겹침 경고용: 같은 밴드의 <b>살아 있는</b>(PENDING·CONFIRMED) 일정 중 주어진 구간과 겹치는 것.
     *
     * <p>겹침 판정은 반열림 구간이다 — {@code startAt < :endAt AND endAt > :startAt}. 앞 일정의 종료와
     * 뒤 일정의 시작이 정확히 같으면 겹치지 않는다.
     *
     * <p>이 결과는 <b>등록/수정을 거부하는 데 쓰지 않는다</b>. 응답에 경고로만 싣는다(BUILD_PLAN 2장 2번).
     * 수정 시 자기 자신이 잡히지 않도록 {@code excludeId}로 제외한다(신규 등록은 존재할 수 없는 값을 넘긴다).
     * 경고 목록이 무한정 커지지 않도록 호출 측이 {@link Pageable}로 상한을 준다.
     */
    @Query("""
            select r from Reservation r
             where r.bandId = :bandId
               and r.status in (com.yeka.bandapp.reservation.entity.ReservationStatus.PENDING,
                                com.yeka.bandapp.reservation.entity.ReservationStatus.CONFIRMED)
               and r.id <> :excludeId
               and r.startAt < :endAt
               and r.endAt > :startAt
             order by r.startAt asc
            """)
    List<Reservation> findOverlapping(@Param("bandId") long bandId,
                                     @Param("startAt") Instant startAt,
                                     @Param("endAt") Instant endAt,
                                     @Param("excludeId") long excludeId,
                                     Pageable limit);

    // --- 정기 일정(Phase 5) 회차 관리 -----------------------------------------

    /** 규칙 검증·내부 확인용(배치·테스트). 취소분 포함, 상한 없음 — 사용자 응답에는 쓰지 않는다. */
    List<Reservation> findByRecurringRuleIdOrderByStartAtAsc(Long recurringRuleId);

    /**
     * 규칙 상세·등록 응답용 — {@code from} 이후 회차만(취소분 포함), start_at 오름차순. 주간 규칙이
     * 몇 년 쌓여도 응답이 무한정 커지지 않게 최근 구간만 준다(Phase 4 §8.1 #3과 같은 취지).
     * 그 이전 이력은 400일로 제한된 캘린더 API 로 조회한다.
     */
    List<Reservation> findByRecurringRuleIdAndStartAtGreaterThanEqualOrderByStartAtAsc(
            Long recurringRuleId, Instant from);

    /** 배치가 "이 시각 다음부터" 이어 만들도록, 규칙의 마지막 회차(상태 무관)를 준다. */
    Optional<Reservation> findFirstByRecurringRuleIdOrderByStartAtDesc(Long recurringRuleId);

    /** 규칙이 이미 만든 회차 시작 시각들(상태 무관). 재생성 시 이미 있는 슬롯을 걸러내는 데 쓴다. */
    @Query("select r.startAt from Reservation r where r.recurringRuleId = :ruleId")
    List<Instant> findOccurrenceStarts(@Param("ruleId") long recurringRuleId);

    /** 규칙 삭제 시 취소 대상 — 아직 시작하지 않았고 살아 있는(PENDING·CONFIRMED) 회차. */
    List<Reservation> findByRecurringRuleIdAndStartAtGreaterThanEqualAndStatusIn(
            Long recurringRuleId, Instant from, Collection<ReservationStatus> statuses);

    /**
     * 정기 규칙 등록 응답의 겹침 경고용: 같은 밴드의 살아 있는 일정 중 주어진 구간과 겹치되
     * <b>이 규칙이 만든 회차는 제외한</b> 것. 나머지 규칙은 여전히 서로의 겹침을 막지 않는다 —
     * 경고로만 싣는다(BUILD_PLAN 2장 2번).
     */
    @Query("""
            select r from Reservation r
             where r.bandId = :bandId
               and r.status in (com.yeka.bandapp.reservation.entity.ReservationStatus.PENDING,
                                com.yeka.bandapp.reservation.entity.ReservationStatus.CONFIRMED)
               and (r.recurringRuleId is null or r.recurringRuleId <> :excludeRuleId)
               and r.startAt < :endAt
               and r.endAt > :startAt
             order by r.startAt asc
            """)
    List<Reservation> findOverlappingExcludingRule(@Param("bandId") long bandId,
                                                   @Param("startAt") Instant startAt,
                                                   @Param("endAt") Instant endAt,
                                                   @Param("excludeRuleId") long excludeRuleId,
                                                   Pageable limit);
}
