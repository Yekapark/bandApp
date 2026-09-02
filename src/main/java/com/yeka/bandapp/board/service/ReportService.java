package com.yeka.bandapp.board.service;

import com.yeka.bandapp.band.service.BandAccessGuard;
import com.yeka.bandapp.board.dto.CreateReportRequest;
import com.yeka.bandapp.board.dto.ReportResponse;
import com.yeka.bandapp.board.entity.BoardPost;
import com.yeka.bandapp.board.entity.MediaAttachment;
import com.yeka.bandapp.board.entity.Report;
import com.yeka.bandapp.board.entity.ReportStatus;
import com.yeka.bandapp.board.entity.ReportTargetType;
import com.yeka.bandapp.board.repository.BoardPostRepository;
import com.yeka.bandapp.board.repository.MediaAttachmentRepository;
import com.yeka.bandapp.board.repository.ReportRepository;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.common.ratelimit.RateLimitProperties;
import com.yeka.bandapp.common.ratelimit.RedisRateLimiter;
import com.yeka.bandapp.user.service.UserDirectoryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글·미디어·사용자 신고 접수. 접수만 한다 — 처리(RESOLVED 전이)용 운영 API 는 BUILD_PLAN Phase 8
 * 범위 밖이다.
 *
 * <p>대상이 요청자에게 보이지 않으면(타 밴드 게시글·미디어, 없는 사용자) 존재를 알리지 않고
 * {@code REPORT_TARGET_NOT_FOUND}(404). 자기 자신·자기 글은 {@code CANNOT_REPORT_SELF}(400).
 * 같은 대상에 대한 미처리 신고는 신고자당 하나({@code REPORT_ALREADY_SUBMITTED}, 409).
 */
@Service
public class ReportService {

    private static final String RATE_LIMIT_BUCKET = "report:user";

    private final ReportRepository reportRepository;
    private final BoardPostRepository postRepository;
    private final MediaAttachmentRepository mediaRepository;
    private final BandAccessGuard accessGuard;
    private final UserDirectoryService userDirectory;
    private final RedisRateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;

    public ReportService(ReportRepository reportRepository,
                         BoardPostRepository postRepository,
                         MediaAttachmentRepository mediaRepository,
                         BandAccessGuard accessGuard,
                         UserDirectoryService userDirectory,
                         RedisRateLimiter rateLimiter,
                         RateLimitProperties rateLimitProperties) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.mediaRepository = mediaRepository;
        this.accessGuard = accessGuard;
        this.userDirectory = userDirectory;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
    }

    @Transactional
    public ReportResponse report(long callerId, CreateReportRequest request) {
        rateLimiter.check(RATE_LIMIT_BUCKET, String.valueOf(callerId),
                rateLimitProperties.reportPerUserPerMin());

        verifyTargetVisible(callerId, request.targetType(), request.targetId());

        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
                callerId, request.targetType(), request.targetId(), ReportStatus.OPEN)) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_SUBMITTED);
        }
        Report report = Report.open(request.targetType(), request.targetId(), callerId, request.reason().trim());
        try {
            reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException duplicate) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_SUBMITTED);
        }
        return ReportResponse.from(report);
    }

    // --- 내부 헬퍼 -------------------------------------------------------

    private void verifyTargetVisible(long callerId, ReportTargetType targetType, long targetId) {
        switch (targetType) {
            case POST -> {
                BoardPost post = postRepository.findByIdAndDeletedAtIsNull(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                requireVisibleInBand(callerId, post.getBandId());
                requireNotSelf(post.getAuthorId(), callerId);
            }
            case MEDIA -> {
                MediaAttachment media = mediaRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                BoardPost post = postRepository.findByIdAndDeletedAtIsNull(media.getBoardPostId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND));
                requireVisibleInBand(callerId, post.getBandId());
                requireNotSelf(post.getAuthorId(), callerId);
            }
            case USER -> {
                if (!userDirectory.existsActive(targetId)) {
                    throw new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND);
                }
                requireNotSelf(targetId, callerId);
            }
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    /** 요청자가 그 밴드 멤버가 아니면 존재를 알리지 않고 404 로 바꾼다. */
    private void requireVisibleInBand(long callerId, long bandId) {
        try {
            accessGuard.requireActiveMember(bandId, callerId);
        } catch (BusinessException notMember) {
            throw new BusinessException(ErrorCode.REPORT_TARGET_NOT_FOUND);
        }
    }

    private void requireNotSelf(long targetUserId, long callerId) {
        if (targetUserId == callerId) {
            throw new BusinessException(ErrorCode.CANNOT_REPORT_SELF);
        }
    }
}
