package com.yeka.bandapp.board.service;

import com.yeka.bandapp.band.entity.BandMember;
import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.board.dto.IssueUploadUrlRequest;
import com.yeka.bandapp.board.dto.MediaResponse;
import com.yeka.bandapp.board.dto.UploadUrlResponse;
import com.yeka.bandapp.board.entity.BoardPost;
import com.yeka.bandapp.board.entity.MediaAttachment;
import com.yeka.bandapp.board.entity.MediaStatus;
import com.yeka.bandapp.board.entity.MediaType;
import com.yeka.bandapp.board.repository.BoardPostRepository;
import com.yeka.bandapp.board.repository.MediaAttachmentRepository;
import com.yeka.bandapp.board.storage.R2Properties;
import com.yeka.bandapp.board.storage.StorageClient;
import com.yeka.bandapp.board.storage.StoredObject;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.common.ratelimit.RateLimitProperties;
import com.yeka.bandapp.common.ratelimit.RedisRateLimiter;
import com.yeka.bandapp.plan.service.PlanDirectoryService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 게시글 첨부 미디어의 presigned 업로드 흐름.
 *
 * <p><b>이 서비스의 어떤 메서드에도 {@code @Transactional}을 붙이면 안 된다.</b> R2 HTTP 호출(HEAD·DELETE)이
 * 트랜잭션 안에서 일어나면 커넥션을 왕복 시간 동안 붙잡는다(CLAUDE.md 규칙). 특히 {@link #complete}는 크기
 * 위조를 발견하면 <b>PENDING 행 삭제를 먼저 커밋한 뒤</b> 예외를 던져야 한다 — 트랜잭션이 열려 있으면 그
 * 삭제가 롤백돼 "거부하고 삭제한다"는 완료 기준이 깨진다. DB 쓰기는 저장소의 조건부 UPDATE/DELETE
 * (각자 짧은 트랜잭션)로만 한다.
 *
 * <p>흐름: 게시글 작성자가 {@link #issueUploadUrl}로 PENDING 행 + presigned PUT URL 을 받아 클라이언트가
 * R2 에 직접 PUT → {@link #complete}가 R2 HEAD 로 실제 크기·형식을 확인해 READY 로 전이(불일치면 거부·삭제).
 */
@Service
public class MediaAttachmentService {

    private static final String RATE_LIMIT_BUCKET = "media-upload:user";

    private final BoardPostRepository postRepository;
    private final MediaAttachmentRepository mediaRepository;
    private final BandAccessGuard accessGuard;
    private final StorageClient storage;
    private final R2Properties r2Properties;
    private final RedisRateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final PlanDirectoryService planDirectory;

    public MediaAttachmentService(BoardPostRepository postRepository,
                                  MediaAttachmentRepository mediaRepository,
                                  BandAccessGuard accessGuard,
                                  StorageClient storage,
                                  R2Properties r2Properties,
                                  RedisRateLimiter rateLimiter,
                                  RateLimitProperties rateLimitProperties,
                                  PlanDirectoryService planDirectory) {
        this.postRepository = postRepository;
        this.mediaRepository = mediaRepository;
        this.accessGuard = accessGuard;
        this.storage = storage;
        this.r2Properties = r2Properties;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
        this.planDirectory = planDirectory;
    }

    /**
     * PENDING 첨부를 선생성하고 presigned PUT URL 을 발급한다. 게시글 작성자만 호출할 수 있다.
     * 형식·크기 위반은 여기서 400 으로 걸러지고 행이 생기지 않는다. 레이트리밋 초과는 429.
     */
    public UploadUrlResponse issueUploadUrl(long bandId, long postId, long callerId,
                                            IssueUploadUrlRequest request) {
        accessGuard.requireActiveMember(bandId, callerId);
        rateLimiter.check(RATE_LIMIT_BUCKET, String.valueOf(callerId),
                rateLimitProperties.mediaUploadPerUserPerMin());

        requireAuthoredPost(bandId, postId, callerId);

        String contentType = request.contentType().trim();
        MediaType type = MediaPolicy.resolveType(contentType);
        MediaPolicy.requireWithinLimit(type, request.sizeBytes());

        if (mediaRepository.countByBoardPostIdAndStatusIn(
                postId, List.of(MediaStatus.PENDING, MediaStatus.READY)) >= MediaPolicy.MAX_ATTACHMENTS_PER_POST) {
            throw new BusinessException(ErrorCode.MEDIA_LIMIT_EXCEEDED);
        }

        String storageKey = StorageKeys.newMediaKey(bandId, postId);
        // 행을 먼저 만들고(짧은 tx) 그 다음 서명한다 — 반대 순서면 행 없는 서명 URL 이 나가 추적 불가한
        // 고아 객체가 생길 수 있다. 이 순서에서 남을 수 있는 건 객체 없는 PENDING 행뿐이고, 그건 Phase 9
        // 청소 배치가 지운다.
        MediaAttachment media = mediaRepository.saveAndFlush(
                MediaAttachment.pending(postId, storageKey, type, contentType, request.sizeBytes()));

        URI uploadUrl = storage.presignPut(storageKey, contentType, r2Properties.uploadUrlTtl());
        Instant urlExpiresAt = Instant.now().plus(r2Properties.uploadUrlTtl());
        return new UploadUrlResponse(
                media.getId(),
                uploadUrl.toString(),
                "PUT",
                Map.of("Content-Type", contentType),
                urlExpiresAt,
                MediaPolicy.maxBytesFor(type));
    }

    /**
     * 업로드 완료 콜백. R2 HEAD 로 실제 객체를 확인한다.
     * <ul>
     *   <li>객체 없음 → 409 {@code MEDIA_NOT_UPLOADED} (행은 PENDING 유지, 재시도 가능)</li>
     *   <li>신고 크기·형식과 불일치 → <b>PENDING 행 삭제 커밋 + R2 객체 삭제</b> 후 409
     *       {@code MEDIA_SIZE_MISMATCH}/{@code MEDIA_CONTENT_TYPE_MISMATCH}</li>
     *   <li>일치 → 조건부 UPDATE 로 READY 전이(보관기한 = 밴드 요금제 기준: FREE 는 업로드 + 30일,
     *       PREMIUM 은 무제한/NULL), presigned GET URL 을 붙여 반환</li>
     * </ul>
     */
    public MediaResponse complete(long bandId, long postId, long mediaId, long callerId) {
        accessGuard.requireActiveMember(bandId, callerId);
        requireAuthoredPost(bandId, postId, callerId);

        MediaAttachment media = mediaRepository.findByIdAndBoardPostId(mediaId, postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_NOT_FOUND));
        if (!media.isPending()) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_PENDING);
        }

        StoredObject actual = storage.head(media.getStorageKey())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_NOT_UPLOADED));

        try {
            MediaPolicy.verifyUpload(media.getType(), media.getSizeBytes(), media.getContentType(), actual);
        } catch (BusinessException mismatch) {
            // DB 를 먼저 확정하고(유령 READY 행 방지) R2 객체는 best-effort 로 지운다.
            mediaRepository.deletePending(mediaId);
            try {
                storage.delete(media.getStorageKey());
            } catch (BusinessException storageDown) {
                // 보관기한 배치가 최종 정리한다.
            }
            throw mismatch;
        }

        Instant now = Instant.now();
        Instant expiresAt = planDirectory.mediaExpiresAt(bandId, now); // FREE=업로드+30일, PREMIUM=null(무제한)
        if (mediaRepository.markReady(mediaId, now, expiresAt) == 0) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_PENDING); // 그 사이 삭제/완료됨
        }

        MediaAttachment ready = mediaRepository.findByIdAndBoardPostId(mediaId, postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_NOT_FOUND));
        return MediaResponse.of(ready, storage.presignGet(
                ready.getStorageKey(), ready.getContentType(), r2Properties.downloadUrlTtl()).toString());
    }

    /** 첨부 삭제(작성자 또는 밴드장). DB 행을 지운 뒤 R2 객체를 best-effort 로 정리한다. */
    public void delete(long bandId, long postId, long mediaId, long callerId) {
        BandMember member = accessGuard.requireActiveMember(bandId, callerId);
        BoardPost post = requirePostInBand(bandId, postId);
        if (!post.isWrittenBy(callerId) && !member.isLeader()) {
            throw new BusinessException(ErrorCode.NOT_POST_OWNER);
        }

        MediaAttachment media = mediaRepository.findByIdAndBoardPostId(mediaId, postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_NOT_FOUND));
        String key = media.getStorageKey();
        if (mediaRepository.deleteByIdAndBoardPostId(mediaId, postId) == 0) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_FOUND);
        }
        try {
            storage.delete(key);
        } catch (BusinessException storageDown) {
            // 보관기한 배치가 최종 정리한다.
        }
    }

    // --- 내부 헬퍼 -------------------------------------------------------

    private void requireAuthoredPost(long bandId, long postId, long callerId) {
        BoardPost post = requirePostInBand(bandId, postId);
        if (!post.isWrittenBy(callerId)) {
            throw new BusinessException(ErrorCode.NOT_POST_OWNER);
        }
    }

    private BoardPost requirePostInBand(long bandId, long postId) {
        BoardPost post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.belongsTo(bandId)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }
}
