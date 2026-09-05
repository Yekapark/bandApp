package com.yeka.bandapp.recurring.repository;

import com.yeka.bandapp.recurring.entity.RecurringRule;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;

public interface RecurringRuleRepository extends JpaRepository<RecurringRule, Long> {

    /** 상세 조회용(잠금 없음). 경로의 {@code bandId}와 대조하고 삭제된 규칙은 없는 것으로 취급한다. */
    Optional<RecurringRule> findByIdAndBandIdAndDeletedAtIsNull(long id, long bandId);

    /**
     * 규칙 삭제 명령용 — 행에 {@code PESSIMISTIC_WRITE}(=Postgres {@code SELECT … FOR UPDATE})를 건다.
     * 같은 규칙에 대한 동시 DELETE(더블탭·재시도)를 직렬화해, 미래 회차 취소와 그에 딸린 합주실
     * {@code usageCount} 감소가 정확히 한 번만 일어나게 한다({@code ReservationRepository.findByIdAndBandIdForUpdate}와
     * 같은 계열, Phase 4 §8.1 #1). 두 번째 요청은 락을 기다렸다가 이미 {@code deletedAt}이 찍힌 것을 보고
     * 빈 값을 받는다 → 404.
     *
     * <p>트랜잭션 안에서만 호출해야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringRule r where r.id = :id and r.bandId = :bandId and r.deletedAt is null")
    Optional<RecurringRule> findActiveByIdAndBandIdForUpdate(@Param("id") long id, @Param("bandId") long bandId);

    /**
     * 배치 회차 연장용 잠금 파인더 — {@code deletedAt} 조건 없이 행을 잠근다. 연장 도중 사용자가
     * 같은 규칙을 삭제하는 레이스에서, 삭제된 규칙에 새 회차가 붙지 않도록 로드 후 {@code isDeleted()}로
     * 판단한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringRule r where r.id = :id")
    Optional<RecurringRule> findByIdForUpdate(@Param("id") long id);

    /** 밴드의 활성 규칙 목록, 최신 등록순. */
    List<RecurringRule> findByBandIdAndDeletedAtIsNullOrderByCreatedAtDesc(long bandId);

    /**
     * 배치 연장용 — 활성 규칙을 id 오름차순으로 페이지 단위로 훑는다({@code afterId} 키셋 페이징).
     * 규칙 수가 많아져도 한 번에 전부 메모리에 올리지 않는다.
     */
    @Query("select r from RecurringRule r where r.deletedAt is null and r.id > :afterId order by r.id asc")
    List<RecurringRule> findActiveAfter(@Param("afterId") long afterId, Pageable page);

    /** 밴드 삭제 정리. 회차(reservations)를 먼저 지운 뒤 호출한다. */
    @Modifying
    @Query("delete from RecurringRule r where r.bandId = :bandId")
    int deleteByBandId(@Param("bandId") long bandId);
}
