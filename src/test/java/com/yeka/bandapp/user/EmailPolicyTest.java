package com.yeka.bandapp.user;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;
import com.yeka.bandapp.user.service.EmailPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EmailPolicy} 단위 테스트 — Docker 불필요.
 *
 * <p>메일을 절대 받을 수 없는 예약 도메인(RFC 2606/6761)을 가입에서 거른다. 이게 없으면 인증 메일이
 * 100% 반송돼 Gmail 발신 평판이 깎이고, 그러면 비밀번호 재설정·신고 알림까지 같이 죽는다.
 */
class EmailPolicyTest {

    @Test
    void normal_addresses_pass() {
        assertThatCode(() -> EmailPolicy.requireDeliverable("leader@gmail.com")).doesNotThrowAnyException();
        assertThatCode(() -> EmailPolicy.requireDeliverable("a.b+tag@naver.com")).doesNotThrowAnyException();
        // 예약어가 도메인의 일부일 뿐이면 진짜 주소다 — 막으면 안 된다.
        assertThatCode(() -> EmailPolicy.requireDeliverable("me@example.com.co.kr")).doesNotThrowAnyException();
        assertThatCode(() -> EmailPolicy.requireDeliverable("me@myexample.org")).doesNotThrowAnyException();
        assertThatCode(() -> EmailPolicy.requireDeliverable("me@testing.io")).doesNotThrowAnyException();
    }

    /** 2026-09-09 에 실제로 이 주소로 가입이 들어와 반송이 왔다. */
    @Test
    void reserved_second_level_domains_are_rejected() {
        assertThatThrownBy(() -> EmailPolicy.requireDeliverable("testuser12345@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED);

        assertThat(EmailPolicy.isReserved("a@example.net")).isTrue();
        assertThat(EmailPolicy.isReserved("a@example.org")).isTrue();
    }

    @Test
    void reserved_tlds_are_rejected() {
        assertThat(EmailPolicy.isReserved("a@anything.test")).isTrue();
        assertThat(EmailPolicy.isReserved("a@anything.example")).isTrue();
        assertThat(EmailPolicy.isReserved("a@anything.invalid")).isTrue();
        assertThat(EmailPolicy.isReserved("a@anything.localhost")).isTrue();
        assertThat(EmailPolicy.isReserved("a@printer.local")).isTrue();
    }

    /** 대소문자·공백으로 우회할 수 없어야 한다. */
    @Test
    void case_and_whitespace_do_not_bypass() {
        assertThat(EmailPolicy.isReserved("a@EXAMPLE.COM")).isTrue();
        assertThat(EmailPolicy.isReserved("a@ Example.Com ")).isTrue();
    }

    /** 형식 자체가 아닌 값은 여기서 판단하지 않는다 — {@code @Email} 이 먼저 400 을 낸다. */
    @Test
    void malformed_input_is_left_to_bean_validation() {
        assertThat(EmailPolicy.isReserved(null)).isFalse();
        assertThat(EmailPolicy.isReserved("no-at-sign")).isFalse();
        assertThat(EmailPolicy.isReserved("trailing@")).isFalse();
    }
}
