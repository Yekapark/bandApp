package com.yeka.bandapp.board.entity;

import com.yeka.bandapp.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 게시글 하나에 딸린 사진·영상 첨부. 파일 바이트는 서버를 지나지 않는다 — 클라이언트가 Cloudflare R2 로
 * presigned PUT 으로 직접 올리고, 이 엔티티는 {@code storageKey}와 메타데이터만 갖는다.
 *
 * <p>상태 전이({@code PENDING → READY}, {@code → EXPIRED})는 동시성 안전을 위해 이 엔티티의 메서드가
 * 아니라 저장소의 조건부 원자 UPDATE 로 한다({@code MediaAttachmentRepository#markReady} 등). 여기서는
 * 읽기 헬퍼만 노출한다. {@code sizeBytes}는 클라이언트가 <b>신고한</b> 값이며, 완료 콜백에서 R2 HEAD 로
 * 실제 크기와 대조한다.
 */
@Entity
@Table(name = "media_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaAttachment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_post_id", nullable = false)
    private Long boardPostId;

    @Column(name = "storage_key", nullable = false, length = 200)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaStatus status;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    private MediaAttachment(long boardPostId, String storageKey, MediaType type,
                            String contentType, long sizeBytes) {
        this.boardPostId = boardPostId;
        this.storageKey = storageKey;
        this.type = type;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.status = MediaStatus.PENDING;
    }

    /** 업로드 URL 발급 시 선생성. 아직 R2 에 객체가 있는지는 확인되지 않았다(PENDING). */
    public static MediaAttachment pending(long boardPostId, String storageKey, MediaType type,
                                          String contentType, long sizeBytes) {
        return new MediaAttachment(boardPostId, storageKey, type, contentType, sizeBytes);
    }

    public boolean isPending() {
        return status == MediaStatus.PENDING;
    }

    public boolean isReady() {
        return status == MediaStatus.READY;
    }

    public boolean belongsTo(long boardPostId) {
        return this.boardPostId != null && this.boardPostId == boardPostId;
    }
}
