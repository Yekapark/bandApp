package com.yeka.bandapp.board.repository;

import com.yeka.bandapp.board.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    /**
     * 게시판 목록·상세 필터용 — "내가 차단한 사람"과 "나를 차단한 사람"의 userId 를 한 쿼리로 모은다.
     * 양방향으로 숨겨야 차단 사실이 상대에게 역으로 드러나지 않는다.
     */
    @Query("""
            select case when b.blockerId = :userId then b.blockedUserId else b.blockerId end
              from UserBlock b
             where b.blockerId = :userId or b.blockedUserId = :userId
            """)
    List<Long> findRelatedUserIds(@Param("userId") long userId);

    List<UserBlock> findByBlockerIdOrderByIdDesc(Long blockerId);

    Optional<UserBlock> findByBlockerIdAndBlockedUserId(Long blockerId, Long blockedUserId);
}
