package com.yeka.bandapp.recurring.repository;

import com.yeka.bandapp.recurring.entity.RecurringRule;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecurringRuleRepository extends JpaRepository<RecurringRule, Long> {

    /** 상세·삭제용. 경로의 {@code bandId}와 대조하고 삭제된 규칙은 없는 것으로 취급한다. */
    Optional<RecurringRule> findByIdAndBandIdAndDeletedAtIsNull(long id, long bandId);

    /** 밴드의 활성 규칙 목록, 최신 등록순. */
    List<RecurringRule> findByBandIdAndDeletedAtIsNullOrderByCreatedAtDesc(long bandId);

    /**
     * 배치 연장용 — 활성 규칙을 id 오름차순으로 페이지 단위로 훑는다({@code afterId} 키셋 페이징).
     * 규칙 수가 많아져도 한 번에 전부 메모리에 올리지 않는다.
     */
    @Query("select r from RecurringRule r where r.deletedAt is null and r.id > :afterId order by r.id asc")
    List<RecurringRule> findActiveAfter(@Param("afterId") long afterId, Pageable page);
}
