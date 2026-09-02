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

/**
 * 게시글·미디어·사용자 신고 접수 기록. {@code targetId}는 {@link ReportTargetType}에 따라 서로 다른
 * 테이블을 가리키는 다형 참조라 외래키가 없다(마이그레이션 주석 참조).
 *
 * <p>같은 대상에 대한 미처리(OPEN) 신고는 신고자당 하나다({@code ux_reports_open_target} 부분 유니크).
 * {@code RESOLVED}로의 전이는 운영자 도구의 몫이며 이 Phase 범위 밖이다.
 */
@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    private Report(ReportTargetType targetType, long targetId, long reporterId, String reason) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.status = ReportStatus.OPEN;
    }

    public static Report open(ReportTargetType targetType, long targetId, long reporterId, String reason) {
        return new Report(targetType, targetId, reporterId, reason);
    }
}
