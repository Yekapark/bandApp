package com.yeka.bandapp.board.service;

import com.yeka.bandapp.board.entity.Report;
import com.yeka.bandapp.board.entity.ReportTargetType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 신고 접수 메일의 제목·본문. 스프링 컨테이너가 필요 없는 순수 함수라 단위 테스트로 본다.
 * (같은 패키지에 두어 package-private 인 {@link ReportMail} 을 그대로 부른다.)
 *
 * <p>여기서 지키려는 것은 <b>메일만 보고 바로 조회할 수 있는가</b>다. 운영자 화면이 없어서
 * 신고 확인은 DB 직접 조회로 하는데, 본문의 쿼리에 신고 번호·대상 id 가 안 박히면 메일이
 * 반쪽이 된다.
 */
class ReportMailTest {

    private static Report reportOf(ReportTargetType type, long targetId, long reporterId, String reason) {
        Report report = Report.open(type, targetId, reporterId, reason);
        // 실제로는 saveAndFlush 뒤에 붙는 값. 메일 본문이 이 값을 쓰므로 테스트에서 채워 준다.
        ReflectionTestUtils.setField(report, "id", 123L);
        return report;
    }

    @Test
    void 제목에_신고번호와_종류가_들어간다() {
        Report report = reportOf(ReportTargetType.POST, 456L, 7L, "욕설");

        assertThat(ReportMail.subject(report, "게시글")).isEqualTo("[밴듈] 신고 접수 #123 (게시글)");
    }

    @Test
    void 본문에_신고_내용과_조회_쿼리가_함께_들어간다() {
        Report report = reportOf(ReportTargetType.POST, 456L, 7L, "욕설이 심해요");

        String body = ReportMail.body(report, "게시글");

        // 사람이 읽을 부분
        assertThat(body).contains("#123").contains("게시글").contains("욕설이 심해요");
        assertThat(body).contains("users.id = 7");

        // 복사해 붙이면 바로 도는 쿼리
        assertThat(body).contains("SELECT * FROM reports WHERE id = 123;");
        assertThat(body).contains("FROM board_posts p").contains("WHERE p.id = 456;");
        assertThat(body).contains("UPDATE reports SET status = 'RESOLVED' WHERE id = 123;");
    }

    @Test
    void 대상_종류마다_다른_테이블을_조회한다() {
        assertThat(ReportMail.body(reportOf(ReportTargetType.MEDIA, 99L, 7L, "부적절"), "사진·영상"))
                .contains("FROM media_attachments m")
                .contains("WHERE m.id = 99;");

        assertThat(ReportMail.body(reportOf(ReportTargetType.USER, 42L, 7L, "사칭"), "사용자"))
                .contains("FROM users u WHERE u.id = 42;")
                .contains("WHERE author_id = 42");
    }

    /** 저장 직후 createdAt 이 아직 비어 있어도 본문 생성이 터지지 않아야 한다. */
    @Test
    void 접수시각이_아직_없어도_본문을_만든다() {
        Report report = reportOf(ReportTargetType.POST, 1L, 2L, "사유");
        assertThat(report.getCreatedAt()).isNull();

        assertThat(ReportMail.body(report, "게시글")).contains("접수 시각");
    }
}
