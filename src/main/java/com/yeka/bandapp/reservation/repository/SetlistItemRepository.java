package com.yeka.bandapp.reservation.repository;

import com.yeka.bandapp.reservation.entity.SetlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SetlistItemRepository extends JpaRepository<SetlistItem, Long> {

    /** 일정별 셋리스트를 순서대로. 같은 {@code orderNo}가 잠깐 겹칠 수 있어 {@code id}를 2차 키로 둔다. */
    List<SetlistItem> findByReservationIdOrderByOrderNoAscIdAsc(Long reservationId);

    /** 경로의 {@code reservationId}와 대조해 타 일정의 항목 접근을 차단한다. */
    Optional<SetlistItem> findByIdAndReservationId(Long id, Long reservationId);

    /** 곡 추가 시 항목 수 상한 확인용. */
    long countByReservationId(Long reservationId);

    /** 새 곡의 순서 번호(마지막 + 1) 계산용. 항목이 없으면 0. */
    @Query("select coalesce(max(s.orderNo), 0) from SetlistItem s where s.reservationId = :reservationId")
    int maxOrderNo(@Param("reservationId") long reservationId);
}
