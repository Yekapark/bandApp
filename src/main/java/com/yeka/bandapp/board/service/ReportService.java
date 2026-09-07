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
import com.yeka.bandapp.common.mail.EmailSender;
import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.board.config.ReportProperties;
import com.yeka.bandapp.common.ratelimit.RateLimitProperties;
import com.yeka.bandapp.common.ratelimit.RedisRateLimiter;
import com.yeka.bandapp.notification.entity.NotificationType;
import com.yeka.bandapp.notification.service.NotificationMessages;
import com.yeka.bandapp.notification.service.NotificationSender;
import com.yeka.bandapp.user.service.UserDirectoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 게시글·미디어·사용자 신고 접수. 접수만 한다 — 처리(RESOLVED 전이)용 운영 API 는 BUILD_PLAN Phase 8
 * 범위 밖이다.
 *
 * <p>접수되면 <b>운영자에게 푸시가 간다</b>({@code app.report.notify-user-ids}). 그게 없으면
 * 신고는 표에 한 줄 쌓일 뿐 아무도 모른다.
 *
 * <p>대상이 요청자에게 보이지 않으면(타 밴드 게시글·미디어, 없는 사용자) 존재를 알리지 않고
 * {@code REPORT_TARGET_NOT_FOUND}(404). 자기 자신·자기 글은 {@code CANNOT_REPORT_SELF}(400).
 * 같은 대상에 대한 미처리 신고는 신고자당 하나({@code REPORT_ALREADY_SUBMITTED}, 409).
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private static final String RATE_LIMIT_BUCKET = "report:user";

    private final ReportRepository reportRepository;
    private final BoardPostRepository postRepository;
    private final MediaAttachmentRepository mediaRepository;
    private final BandAccessGuard accessGuard;
    private final UserDirectoryService userDirectory;
    private final RedisRateLimiter rateLimiter;
    private final RateLimitProperties rateLimitProperties;
    private final NotificationSender notificationSender;
    private final ReportProperties reportProperties;
    private final EmailSender emailSender;

    public ReportService(ReportRepository reportRepository,
                         BoardPostRepository postRepository,
                         MediaAttachmentRepository mediaRepository,
                         BandAccessGuard accessGuard,
                         UserDirectoryService userDirectory,
                         RedisRateLimiter rateLimiter,
                         RateLimitProperties rateLimitProperties,
                         NotificationSender notificationSender,
                         ReportProperties reportProperties,
                         EmailSender emailSender) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.mediaRepository = mediaRepository;
        this.accessGuard = accessGuard;
        this.userDirectory = userDirectory;
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = rateLimitProperties;
        this.notificationSender = notificationSender;
        this.reportProperties = reportProperties;
        this.emailSender = emailSender;
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
        notifyOperatorsAfterCommit(report);
        return ReportResponse.from(report);
    }

    /**
     * 커밋된 뒤에 보낸다.
     *
     * <p><b>트랜잭션 안에서 보내지 않는 이유</b> — 발송이 FCM HTTP 를 부르는데, 그러면 왕복
     * 시간 내내 DB 커넥션을 붙잡는다(CLAUDE.md). 게다가 뒤에서 롤백이 나면 없는 신고를
     * 알린 셈이 된다.
     *
     * <p>알림 실패가 신고 접수를 되돌리지는 않는다. 접수는 이미 끝났고, 못 알린 것은
     * 로그로 남는다.
     */
    private void notifyOperatorsAfterCommit(Report report) {
        boolean push = !reportProperties.notifyUserIds().isEmpty();
        boolean mail = !reportProperties.notifyEmails().isEmpty();
        if (!push && !mail) {
            return;
        }
        long reportId = report.getId();
        String label = targetLabel(report.getTargetType());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (push) {
                    try {
                        notificationSender.notify(NotificationType.REPORT_RECEIVED, reportId, 0,
                                reportProperties.notifyUserIds(),
                                NotificationMessages.reportReceived(reportId, label));
                    } catch (RuntimeException e) {
                        log.warn("신고 접수 푸시 실패 reportId={}", reportId, e);
                    }
                }
                if (mail) {
                    sendMail(report, label);
                }
            }
        });
    }

    /**
     * 신고 접수 메일. 푸시와 <b>함께</b> 보낸다 — 푸시는 기기 토큰이 있어야 닿고(앱을 지웠거나
     * 알림 권한을 껐으면 조용히 사라진다) 앱을 켜야 본다. 신고는 놓치면 곤란한 종류라 두 경로로 민다.
     *
     * <p>한 사람에게 실패해도 나머지에게는 보낸다. 발송 실패가 이미 끝난 접수를 되돌리지는 않는다
     * ({@link EmailSender} 도 자체적으로 예외를 삼키지만, 주소가 잘못돼 던지는 경우까지 막는다).
     */
    private void sendMail(Report report, String label) {
        String subject = ReportMail.subject(report, label);
        String body = ReportMail.body(report, label);
        for (String to : reportProperties.notifyEmails()) {
            try {
                emailSender.send(to, subject, body);
            } catch (RuntimeException e) {
                log.warn("신고 접수 메일 실패 reportId={} to={}", report.getId(), to, e);
            }
        }
    }

    private static String targetLabel(ReportTargetType targetType) {
        return switch (targetType) {
            case POST -> "게시글";
            case MEDIA -> "사진·영상";
            case USER -> "사용자";
        };
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
