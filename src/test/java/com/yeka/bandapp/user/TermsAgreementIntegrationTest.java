package com.yeka.bandapp.user;

import com.yeka.bandapp.support.ApiIntegrationTest;
import com.yeka.bandapp.user.entity.TermsAgreement;
import com.yeka.bandapp.user.repository.TermsAgreementRepository;
import com.yeka.bandapp.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가입하면 약관 동의 사실이 남는가.
 *
 * <p>그전까지 동의는 앱 화면에서 체크만 받고 아무 데도 남지 않았다. "동의한 적 없다" 는
 * 다툼이 생기면 입증할 근거가 없었다. <b>증상이 조용한 종류라</b> — 가입은 멀쩡히 되고,
 * 없다는 것은 분쟁이 생겨야 드러난다 — 테스트로 붙잡아 둔다.
 */
class TermsAgreementIntegrationTest extends ApiIntegrationTest {

    @Autowired
    TermsAgreementRepository agreements;

    @Autowired
    UserRepository users;

    private static final String SIGNUP = """
            {"email":"terms@band.app","password":"pw12345678","name":"동의"}
            """;

    @Test
    void signup_records_the_agreement_with_the_current_versions() {
        assertThat(post("/api/v1/auth/signup", SIGNUP).getStatusCode().value()).isEqualTo(201);

        long userId = users.findAll().getFirst().getId();
        List<TermsAgreement> rows = agreements.findByUserIdOrderByAgreedAtDesc(userId);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getTermsVersion()).isNotBlank();
        assertThat(rows.getFirst().getPrivacyVersion()).isNotBlank();
        assertThat(rows.getFirst().getAgreedAt()).isNotNull();
    }

    /**
     * 로그인은 가입이 아니다. 로그인할 때마다 행이 쌓이면 이력이 아니라 잡음이 된다 —
     * "언제 동의했나" 를 찾을 수 없게 된다.
     */
    @Test
    void logging_in_again_does_not_add_another_row() {
        post("/api/v1/auth/signup", SIGNUP);
        long userId = users.findAll().getFirst().getId();

        post("/api/v1/auth/login", "{\"email\":\"terms@band.app\",\"password\":\"pw12345678\"}");
        post("/api/v1/auth/login", "{\"email\":\"terms@band.app\",\"password\":\"pw12345678\"}");

        assertThat(agreements.findByUserIdOrderByAgreedAtDesc(userId)).hasSize(1);
    }
}
