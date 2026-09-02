package com.yeka.bandapp.board.repository;

import com.yeka.bandapp.board.entity.BoardPost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BoardPostRepository extends JpaRepository<BoardPost, Long> {

    /** 상세·수정·삭제용. 소프트 삭제된 글은 없는 것으로 본다. */
    Optional<BoardPost> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 커서 페이징 <b>첫 페이지</b> + 차단 사용자 글 제외. 정렬은 {@code (created_at DESC, id DESC)}로
     * {@code ix_board_posts_band_created} 부분 인덱스를 탄다. {@code excludedAuthorIds}는 호출 측이 절대
     * 빈 컬렉션이 아니게(센티넬 {@code -1} 포함) 넘긴다. {@code Pageable}은 {@code limit + 1}건을 요청해
     * 다음 페이지 존재 여부를 판정한다.
     *
     * <p>커서 유무로 메서드를 나눈 이유: 한 쿼리에서 {@code :cursor is null}로 분기하면 PostgreSQL 이
     * null 바인드의 타입을 추론하지 못해({@code could not determine data type of parameter}) 실패한다.
     */
    @Query("""
            select p from BoardPost p
             where p.bandId = :bandId
               and p.deletedAt is null
               and p.authorId not in :excludedAuthorIds
             order by p.createdAt desc, p.id desc
            """)
    List<BoardPost> findFirstPage(@Param("bandId") long bandId,
                                  @Param("excludedAuthorIds") Collection<Long> excludedAuthorIds,
                                  Pageable pageable);

    /** 커서 <b>이후</b> 페이지. {@code (createdAt, id)} 튜플 비교로 중복·누락 없이 이어 읽는다. */
    @Query("""
            select p from BoardPost p
             where p.bandId = :bandId
               and p.deletedAt is null
               and p.authorId not in :excludedAuthorIds
               and (p.createdAt < :cursorCreatedAt
                    or (p.createdAt = :cursorCreatedAt and p.id < :cursorId))
             order by p.createdAt desc, p.id desc
            """)
    List<BoardPost> findPageAfter(@Param("bandId") long bandId,
                                  @Param("excludedAuthorIds") Collection<Long> excludedAuthorIds,
                                  @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                  @Param("cursorId") long cursorId,
                                  Pageable pageable);

    /**
     * 소프트 삭제. 조회와 UPDATE 사이에 이미 삭제됐으면 0을 돌려준다(멱등).
     *
     * <p>{@code BoardPostService.delete}가 R2 객체 정리를 트랜잭션 밖에서 하려고 {@code @Transactional}
     * 없이 호출하므로 여기에 직접 트랜잭션을 단다({@code RoomRepository} 선례).
     */
    @Transactional
    @Modifying
    @Query("update BoardPost p set p.deletedAt = :now where p.id = :id and p.deletedAt is null")
    int softDelete(@Param("id") long id, @Param("now") Instant now);

    /**
     * 제목·본문만 바꾸는 부분 UPDATE. {@code BoardPostService.update}가 {@code @Transactional} 없이
     * (첨부 URL 서명을 트랜잭션 밖에 두려고) 호출하므로 여기에 직접 트랜잭션을 단다.
     *
     * @return 갱신된 행 수(0 = 그 사이 삭제됨)
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update BoardPost p set p.title = :title, p.content = :content "
            + "where p.id = :id and p.deletedAt is null")
    int updateContent(@Param("id") long id, @Param("title") String title, @Param("content") String content);
}
