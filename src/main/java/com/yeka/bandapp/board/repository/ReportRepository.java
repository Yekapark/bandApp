package com.yeka.bandapp.board.repository;

import com.yeka.bandapp.board.entity.Report;
import com.yeka.bandapp.board.entity.ReportStatus;
import com.yeka.bandapp.board.entity.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 같은 대상에 이미 미처리 신고를 넣었는지 선검사. 실제 경합 방어는 {@code ux_reports_open_target}
     * 부분 유니크 인덱스 + {@code DataIntegrityViolationException} 변환이다({@code RoomService.persist} 선례).
     */
    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
            Long reporterId, ReportTargetType targetType, Long targetId, ReportStatus status);

    /**
     * 밴드 삭제 정리. {@code reports} 는 {@code target_type} + {@code target_id} 다형 참조라
     * {@code band_id} 도 FK 도 없다 — 대상을 직접 거슬러 올라가야 하고, <b>대상 행을 지우기 전에</b>
     * 실행해야 한다.
     *
     * <p>{@code USER} 신고는 밴드가 아니라 사람에 대한 것이라 남긴다.
     * 밴드가 사라지면 POST·MEDIA 신고는 대상 콘텐츠가 없어져 처리 자체가 불가능해진다.
     */
    @Modifying
    @Query("delete from Report r where "
            + "(r.targetType = com.yeka.bandapp.board.entity.ReportTargetType.POST "
            + "  and r.targetId in (select p.id from BoardPost p where p.bandId = :bandId)) "
            + "or (r.targetType = com.yeka.bandapp.board.entity.ReportTargetType.MEDIA "
            + "  and r.targetId in (select m.id from MediaAttachment m where m.boardPostId in "
            + "      (select p.id from BoardPost p where p.bandId = :bandId)))")
    int deleteByBandId(@Param("bandId") long bandId);
}
