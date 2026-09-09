package com.yeka.bandapp.user.service;

import com.yeka.bandapp.common.exception.BusinessException;
import com.yeka.bandapp.common.exception.ErrorCode;

import java.util.Locale;
import java.util.Set;

/**
 * 가입 이메일 주소 규칙(순수 함수). 상태·트랜잭션이 없어 Docker 없이 단위 테스트한다
 * ({@code MediaPolicy}·{@code SettlementCalculator} 선례).
 *
 * <p><b>메일을 절대 받을 수 없는 예약 도메인을 거른다.</b> RFC 2606/6761 이 문서·예제·테스트용으로
 * 못 박아 둔 이름들이라 MX 레코드가 존재할 수 없고, 보내면 100% 반송된다. 반송이 쌓이면 Gmail SMTP
 * 발신 평판이 깎여 <b>비밀번호 재설정·신고 알림까지 같이 죽는다</b> — 그래서 가입 자체를 막는다.
 * (2026-09-09 에 {@code testuser12345@example.com} 가입 한 건으로 반송이 왔다.)
 *
 * <p>여기서 하지 않는 것: 일회용 메일 도메인 차단. 목록을 계속 따라다녀야 하고 오탐이 곧
 * 가입 거부라 값에 비해 비싸다. 반송이 확정된 주소만 막는다.
 */
public final class EmailPolicy {

    /** RFC 2606 §3 예약 2단계 도메인. */
    private static final Set<String> RESERVED_DOMAINS = Set.of(
            "example.com", "example.net", "example.org");

    /** RFC 2606 §2 · RFC 6761 예약 TLD + mDNS 의 {@code .local}(RFC 6762). 앞에 점을 붙여 비교한다. */
    private static final Set<String> RESERVED_SUFFIXES = Set.of(
            ".test", ".example", ".invalid", ".localhost", ".local");

    private EmailPolicy() {
    }

    /**
     * 가입 이메일이 메일을 받을 수 있는 주소인지 본다.
     *
     * @throws BusinessException {@link ErrorCode#EMAIL_DOMAIN_NOT_ALLOWED} 예약 도메인일 때(400)
     */
    public static void requireDeliverable(String email) {
        if (isReserved(email)) {
            throw new BusinessException(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED);
        }
    }

    /** 형식이 아예 아니면(@ 없음 등) false — 형식 검증은 {@code @Email} 이 이미 한다. */
    public static boolean isReserved(String email) {
        if (email == null) {
            return false;
        }
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) {
            return false;
        }
        String domain = email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        if (RESERVED_DOMAINS.contains(domain)) {
            return true;
        }
        return RESERVED_SUFFIXES.stream().anyMatch(domain::endsWith);
    }
}
