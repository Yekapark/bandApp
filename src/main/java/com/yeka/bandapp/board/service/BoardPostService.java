package com.yeka.bandapp.board.service;

import com.yeka.bandapp.band.entity.BandMember;
import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.band.service.BandDirectoryService;
import com.yeka.bandapp.board.dto.CreatePostRequest;
import com.yeka.bandapp.board.dto.MediaResponse;
import com.yeka.bandapp.board.dto.PostListResponse;
import com.yeka.bandapp.board.dto.PostResponse;
import com.yeka.bandapp.board.dto.PostSummaryResponse;
import com.yeka.bandapp.board.dto.UpdatePostRequest;
import com.yeka.bandapp.board.entity.BoardPost;
import com.yeka.bandapp.board.entity.MediaAttachment;
import com.yeka.bandapp.board.entity.MediaStatus;
import com.yeka.bandapp.board.entity.MediaType;
import com.yeka.bandapp.board.repository.BoardPostRepository;
import com.yeka.bandapp.board.repository.MediaAttachmentRepository;
import com.yeka.bandapp.board.storage.R2Properties;
import com.yeka.bandapp.board.storage.StorageClient;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 밴드 내부 게시판. 모든 메서드는 {@link BandAccessGuard#requireActiveMember}로 시작해 타 밴드 접근을
 * 막는다. 타 밴드·삭제·차단 상대의 글은 존재를 알리지 않고 {@code POST_NOT_FOUND}(404)다.
 *
 * <p><b>조회·목록·수정·삭제에는 {@code @Transactional}이 없다.</b> 첨부의 presigned GET URL 생성과
 * (삭제 시) R2 객체 정리를 트랜잭션 밖에서 하기 위해서다({@code RoomService} 선례). 저장·부분 UPDATE 는
 * 저장소 호출 단위의 짧은 트랜잭션으로 처리된다.
 */
@Service
public class BoardPostService {

    private static final Logger log = LoggerFactory.getLogger(BoardPostService.class);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    /** {@code author_id NOT IN} 이 빈 목록이 되지 않도록 항상 섞는 센티넬(존재하지 않는 userId). */
    private static final long NO_AUTHOR = -1L;

    private final BoardPostRepository postRepository;
    private final MediaAttachmentRepository mediaRepository;
    private final BandAccessGuard accessGuard;
    private final BandDirectoryService bandDirectory;
    private final UserBlockService blockService;
    private final StorageClient storage;
    private final R2Properties r2Properties;

    public BoardPostService(BoardPostRepository postRepository,
                            MediaAttachmentRepository mediaRepository,
                            BandAccessGuard accessGuard,
                            BandDirectoryService bandDirectory,
                            UserBlockService blockService,
                            StorageClient storage,
                            R2Properties r2Properties) {
        this.postRepository = postRepository;
        this.mediaRepository = mediaRepository;
        this.accessGuard = accessGuard;
        this.bandDirectory = bandDirectory;
        this.blockService = blockService;
        this.storage = storage;
        this.r2Properties = r2Properties;
    }

    public PostResponse create(long bandId, long callerId, CreatePostRequest request) {
        accessGuard.requireActiveMember(bandId, callerId);
        BoardPost post = postRepository.save(
                BoardPost.create(bandId, callerId, request.title().trim(), request.content().trim()));
        return PostResponse.of(post, displayName(callerId), true, List.of());
    }

    public PostListResponse list(long bandId, long callerId, String cursor, Integer limit) {
        accessGuard.requireActiveMember(bandId, callerId);

        Set<Long> excluded = new HashSet<>(blockService.hiddenUserIdsFor(callerId));
        excluded.add(NO_AUTHOR);
        PostCursor decoded = PostCursor.decode(cursor);
        int pageSize = clampLimit(limit);
        PageRequest pageRequest = PageRequest.of(0, pageSize + 1);

        List<BoardPost> rows = decoded == null
                ? postRepository.findFirstPage(bandId, excluded, pageRequest)
                : postRepository.findPageAfter(bandId, excluded, decoded.createdAt(), decoded.id(), pageRequest);

        boolean hasNext = rows.size() > pageSize;
        List<BoardPost> page = hasNext ? rows.subList(0, pageSize) : rows;

        Map<Long, String> names = displayNames(page.stream().map(BoardPost::getAuthorId).toList());
        Map<Long, List<MediaAttachment>> readyByPost = readyMediaByPost(
                page.stream().map(BoardPost::getId).toList());

        List<PostSummaryResponse> summaries = page.stream()
                .map(p -> {
                    List<MediaAttachment> media = readyByPost.getOrDefault(p.getId(), List.of());
                    String thumbnailUrl = media.stream()
                            .filter(m -> m.getType() == MediaType.IMAGE)
                            .findFirst()
                            .map(this::presignGetOrNull)
                            .orElse(null);
                    return PostSummaryResponse.of(p, names.get(p.getAuthorId()), media.size(), thumbnailUrl);
                })
                .toList();

        String nextCursor = hasNext
                ? PostCursor.ofLast(page.get(page.size() - 1)).encode()
                : null;
        return new PostListResponse(bandId, summaries.size(), summaries, nextCursor, hasNext);
    }

    public PostResponse get(long bandId, long postId, long callerId) {
        BandMember member = accessGuard.requireActiveMember(bandId, callerId);
        BoardPost post = requireVisiblePost(bandId, postId, callerId);

        List<MediaResponse> media = mediaRepository.findByBoardPostIdOrderByIdAsc(postId).stream()
                .map(m -> MediaResponse.of(m, m.isReady() ? presignGetOrNull(m) : null))
                .toList();
        boolean editable = post.isWrittenBy(callerId) || member.isLeader();
        return PostResponse.of(post, displayName(post.getAuthorId()), editable, media);
    }

    public PostResponse update(long bandId, long postId, long callerId, UpdatePostRequest request) {
        BandMember member = accessGuard.requireActiveMember(bandId, callerId);
        BoardPost post = requirePostInBand(bandId, postId);
        requireOwnerOrLeader(post, callerId, member);

        if (postRepository.updateContent(postId, request.title().trim(), request.content().trim()) == 0) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND); // 조회와 UPDATE 사이에 삭제됨
        }
        return get(bandId, postId, callerId);
    }

    public void delete(long bandId, long postId, long callerId) {
        BandMember member = accessGuard.requireActiveMember(bandId, callerId);
        BoardPost post = requirePostInBand(bandId, postId);
        requireOwnerOrLeader(post, callerId, member);

        Instant now = Instant.now();
        if (postRepository.softDelete(postId, now) == 0) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND); // 이미 삭제됨
        }
        // READY 첨부는 보관기한을 지금으로 당겨 두고(실패분은 만료 배치가 재시도), 바로 지워 본다.
        // R2 삭제가 성공한 것만 EXPIRED 로 — 먼저 EXPIRED 로 바꾸면 만료 배치가 건너뛰어 영구 고아가 된다.
        // PENDING 은 고아 정리 배치 몫이다(글이 지워져 완료 콜백은 404).
        mediaRepository.expireReadyOfPostNow(postId, now);
        for (MediaAttachment media : mediaRepository.findByBoardPostIdOrderByIdAsc(postId)) {
            if (!media.isReady()) {
                continue;
            }
            try {
                storage.delete(media.getStorageKey());
                mediaRepository.markExpired(media.getId());
            } catch (BusinessException e) {
                log.warn("삭제된 글의 첨부 R2 삭제 실패 id={} — 만료 배치가 재시도한다", media.getId());
            }
        }
    }

    // --- 크로스 도메인 창구 (다른 board 서비스가 재사용) --------------------

    /** 신고 도메인이 대상 게시글의 밴드/작성자를 확인할 때 쓴다. 없거나 삭제됐으면 empty. */
    public java.util.Optional<BoardPost> findActivePost(long postId) {
        return postRepository.findByIdAndDeletedAtIsNull(postId);
    }

    // --- 내부 헬퍼 -------------------------------------------------------

    private BoardPost requireVisiblePost(long bandId, long postId, long callerId) {
        BoardPost post = requirePostInBand(bandId, postId);
        if (blockService.hiddenUserIdsFor(callerId).contains(post.getAuthorId())) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND); // 차단 관계 — 존재를 알리지 않는다
        }
        return post;
    }

    private BoardPost requirePostInBand(long bandId, long postId) {
        BoardPost post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.belongsTo(bandId)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private void requireOwnerOrLeader(BoardPost post, long callerId, BandMember member) {
        if (!post.isWrittenBy(callerId) && !member.isLeader()) {
            throw new BusinessException(ErrorCode.NOT_POST_OWNER);
        }
    }

    private Map<Long, List<MediaAttachment>> readyMediaByPost(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<MediaAttachment>> byPost = new LinkedHashMap<>();
        for (MediaAttachment m : mediaRepository.findByBoardPostIdInAndStatusOrderByIdAsc(
                postIds, MediaStatus.READY)) {
            byPost.computeIfAbsent(m.getBoardPostId(), k -> new ArrayList<>()).add(m);
        }
        return byPost;
    }

    /**
     * 다운로드용 presigned GET URL. 저장소 미설정·서명 실패({@link BusinessException})면 {@code null}을
     * 돌려준다 — 첨부 하나 때문에 목록·상세 응답 전체가 503 으로 깨지지 않도록 degrade 한다. R2 가
     * 정상이면 항상 URL 이 나온다({@code presignGet} 은 오프라인 서명이라 네트워크 장애로는 실패하지 않음).
     */
    private String presignGetOrNull(MediaAttachment media) {
        try {
            return storage.presignGet(media.getStorageKey(), media.getContentType(),
                    r2Properties.downloadUrlTtl()).toString();
        } catch (BusinessException e) {
            log.warn("presigned GET URL 생성 실패 storageKey={} ({}) — 링크 없이 응답한다",
                    media.getStorageKey(), e.getMessage());
            return null;
        }
    }

    private String displayName(long userId) {
        return displayNames(List.of(userId)).getOrDefault(userId, "(알 수 없음)");
    }

    private Map<Long, String> displayNames(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return bandDirectory.displayNamesOf(new HashSet<>(userIds));
    }

    private static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
