package com.yeka.bandapp.plan.repository;

import com.yeka.bandapp.plan.entity.UnmatchedStorePurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 밴드를 못 찾은 스토어 구독(V24). 미해결 건은 적다(정상이면 0) — 페이지 없이 다 읽는다. */
public interface UnmatchedStorePurchaseRepository extends JpaRepository<UnmatchedStorePurchase, Long> {

    List<UnmatchedStorePurchase> findByResolvedAtIsNullOrderByIdAsc();
}
