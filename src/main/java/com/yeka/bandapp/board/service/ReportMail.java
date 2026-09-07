package com.yeka.bandapp.board.service;

import com.yeka.bandapp.board.entity.Report;
import com.yeka.bandapp.board.entity.ReportTargetType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 신고 접수 알림 메일의 제목·본문.
 *
 * <p><b>본문에 조회 쿼리를 함께 싣는다.</b> 운영자 화면이 아직 없어서 신고를 확인하려면 DB 를
 * 직접 봐야 하는데, 그때마다 테이블 구조를 기억해 내거나 문서를 찾는 것이 실제로 번거로웠다.
 * 메일에서 복사해 붙이면 바로 되도록 신고 종류에 맞는 쿼리를 만들어 넣는다.
 *
 * <p>스프링에 기대지 않는 순수 함수라 {@code ReportMailTest} 가 컨테이너 없이 검증한다.
 */
final class ReportMail {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(KST);

    private ReportMail() {
    }

    static String subject(Report report, String targetLabel) {
        return "[밴듈] 신고 접수 #" + report.getId() + " (" + targetLabel + ")";
    }

    static String body(Report report, String targetLabel) {
        long id = report.getId();
        long targetId = report.getTargetId();
        return """
                신고가 한 건 접수됐습니다.

                  신고 번호 : #%d
                  종류      : %s (%s)
                  대상 id   : %d
                  신고자    : users.id = %d
                  접수 시각 : %s (KST)

                  사유
                  ----
                  %s


                ── 확인용 쿼리 ─────────────────────────────────────────────

                -- 이 신고 한 건
                SELECT * FROM reports WHERE id = %d;

                -- 신고 대상
                %s

                -- 이 신고자가 낸 다른 신고 (반복 신고인지)
                SELECT id, target_type, target_id, status, created_at
                FROM reports WHERE reporter_id = %d ORDER BY id DESC;

                -- 아직 처리 안 된 신고 전체
                SELECT id, target_type, target_id, reporter_id, created_at
                FROM reports WHERE status = 'OPEN' ORDER BY created_at;

                -- 처리했으면 닫기
                UPDATE reports SET status = 'RESOLVED' WHERE id = %d;

                ────────────────────────────────────────────────────────────

                DB 접속 방법은 docs/OPERATIONS.md 를 보세요.
                """.formatted(
                id, targetLabel, report.getTargetType().name(), targetId,
                report.getReporterId(), STAMP.format(instantOf(report)),
                report.getReason(),
                id,
                targetQuery(report.getTargetType(), targetId),
                report.getReporterId(),
                id);
    }

    /** 신고 종류에 따라 대상이 다른 테이블에 있다 — 각각에 맞는 조회문을 만든다. */
    private static String targetQuery(ReportTargetType type, long targetId) {
        return switch (type) {
            case POST -> """
                    SELECT p.id, p.band_id, b.name AS band, p.author_id, u.name AS author,
                           p.title, p.content, p.created_at, p.deleted_at
                    FROM board_posts p
                      JOIN bands b ON b.id = p.band_id
                      JOIN users u ON u.id = p.author_id
                    WHERE p.id = %d;

                    -- 글을 내리려면 (행은 남기고 숨긴다)
                    UPDATE board_posts SET deleted_at = now() WHERE id = %d;"""
                    .formatted(targetId, targetId);
            case MEDIA -> """
                    SELECT m.id, m.type, m.status, m.content_type, m.size_bytes, m.storage_key,
                           m.created_at, p.id AS post_id, p.band_id, p.author_id, u.name AS author
                    FROM media_attachments m
                      JOIN board_posts p ON p.id = m.board_post_id
                      JOIN users u ON u.id = p.author_id
                    WHERE m.id = %d;"""
                    .formatted(targetId);
            case USER -> """
                    SELECT u.id, u.name, u.email, u.social_provider, u.created_at, u.deleted_at
                    FROM users u WHERE u.id = %d;

                    -- 이 사람이 쓴 글
                    SELECT id, band_id, title, created_at, deleted_at
                    FROM board_posts WHERE author_id = %d ORDER BY id DESC LIMIT 50;"""
                    .formatted(targetId, targetId);
        };
    }

    /** 저장 직후라 createdAt 이 아직 비어 있을 수 있다 — 그럴 땐 지금 시각으로 대신한다. */
    private static Instant instantOf(Report report) {
        return report.getCreatedAt() == null ? Instant.now() : report.getCreatedAt();
    }
}
