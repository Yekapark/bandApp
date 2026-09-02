package com.yeka.bandapp.board.entity;

import com.yeka.bandapp.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 밴드 내부 게시판의 글. 합주 사진·영상을 공유하는 용도이며, 첨부는 {@link MediaAttachment}가
 * {@code boardPostId}로 이 글을 참조한다(연관관계 매핑 없이 Long FK).
 *
 * <p>삭제는 {@link #softDelete}로 {@code deletedAt}만 찍는다 — 이미 접수된 신고가 대상을 계속 가리킬 수
 * 있어야 하고, 첨부 정리를 별도로 처리하기 때문이다. 도메인 모델에 {@code updatedAt}이 없어 수정 이력은
 * 남기지 않는다(BUILD_PLAN 3장 BoardPost).
 */
@Entity
@Table(name = "board_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BoardPost extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "band_id", nullable = false)
    private Long bandId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private BoardPost(long bandId, long authorId, String title, String content) {
        this.bandId = bandId;
        this.authorId = authorId;
        this.title = title;
        this.content = content;
    }

    public static BoardPost create(long bandId, long authorId, String title, String content) {
        return new BoardPost(bandId, authorId, title, content);
    }

    /** 제목·본문 전체 교체(PUT). */
    public void edit(String title, String content) {
        this.title = title;
        this.content = content;
    }

    /** 소프트 삭제. 이미 삭제된 글은 그대로 둔다(멱등). */
    public void softDelete(Instant when) {
        if (deletedAt == null) {
            this.deletedAt = when;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean belongsTo(long bandId) {
        return this.bandId != null && this.bandId == bandId;
    }

    public boolean isWrittenBy(long userId) {
        return this.authorId != null && this.authorId == userId;
    }
}
