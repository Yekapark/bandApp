package com.yeka.bandapp.board.repository;

import com.yeka.bandapp.board.entity.MediaAttachment;
import com.yeka.bandapp.board.entity.MediaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 첨부 미디어 저장소. {@code MediaAttachmentService}는 R2 호출을 트랜잭션 밖에 두려고
 * {@code @Transactional} 없이 돌므로, 상태를 바꾸는 쿼리에는 여기에 직접 트랜잭션을 단다
 * ({@code RoomRepository} 선례).
 */
public interface MediaAttachmentRepository extends JpaRepository<MediaAttachment, Long> {

    List<MediaAttachment> findByBoardPostIdOrderByIdAsc(Long boardPostId);

    /** 게시글 목록의 첫 미디어(썸네일) 조립용 — 여러 글의 지정 상태 첨부를 한 번에. */
    List<MediaAttachment> findByBoardPostIdInAndStatusOrderByIdAsc(Collection<Long> boardPostIds, MediaStatus status);

    Optional<MediaAttachment> findByIdAndBoardPostId(Long id, Long boardPostId);

    /**
     * {@code PENDING → READY} 조건부 원자 전이. 동시 complete 호출 중 한 번만 1을 받는다(락 불필요).
     * 0이면 그 사이 삭제됐거나 이미 READY 다.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update MediaAttachment m
               set m.status = com.yeka.bandapp.board.entity.MediaStatus.READY,
                   m.uploadedAt = :uploadedAt, m.expiresAt = :expiresAt
             where m.id = :id and m.status = com.yeka.bandapp.board.entity.MediaStatus.PENDING
            """)
    int markReady(@Param("id") long id, @Param("uploadedAt") Instant uploadedAt,
                  @Param("expiresAt") Instant expiresAt);

    /** 크기·형식 위조 거부 시 PENDING 행 제거. READY 행은 이 쿼리로 지워지지 않는다(안전장치). */
    @Transactional
    @Modifying
    @Query("delete from MediaAttachment m where m.id = :id "
            + "and m.status = com.yeka.bandapp.board.entity.MediaStatus.PENDING")
    int deletePending(@Param("id") long id);

    /** 첨부 삭제(작성자·밴드장). id·boardPostId 를 함께 걸어 타 게시글 첨부를 지우지 못하게 한다. */
    @Transactional
    @Modifying
    @Query("delete from MediaAttachment m where m.id = :id and m.boardPostId = :boardPostId")
    int deleteByIdAndBoardPostId(@Param("id") long id, @Param("boardPostId") long boardPostId);

    /** 게시글 삭제 시 — 남은 첨부를 EXPIRED 로. R2 객체 삭제는 호출 측이 트랜잭션 밖에서 best-effort 로 한다. */
    @Transactional
    @Modifying
    @Query("update MediaAttachment m set m.status = com.yeka.bandapp.board.entity.MediaStatus.EXPIRED "
            + "where m.boardPostId = :boardPostId "
            + "and m.status <> com.yeka.bandapp.board.entity.MediaStatus.EXPIRED")
    int expireAllOfPost(@Param("boardPostId") long boardPostId);

    long countByBoardPostIdAndStatusIn(Long boardPostId, Collection<MediaStatus> statuses);
}
