package com.yeka.bandapp.board.repository;

import com.yeka.bandapp.board.entity.Report;
import com.yeka.bandapp.board.entity.ReportStatus;
import com.yeka.bandapp.board.entity.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 같은 대상에 이미 미처리 신고를 넣었는지 선검사. 실제 경합 방어는 {@code ux_reports_open_target}
     * 부분 유니크 인덱스 + {@code DataIntegrityViolationException} 변환이다({@code RoomService.persist} 선례).
     */
    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
            Long reporterId, ReportTargetType targetType, Long targetId, ReportStatus status);
}
