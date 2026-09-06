package com.yeka.bandapp.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 회원이 언제 어느 버전의 약관·개인정보처리방침에 동의했는지.
 *
 * <p>그전까지 동의는 앱 화면에서 체크만 받고 <b>아무 데도 남지 않았다.</b> "동의한 적 없다" 는
 * 다툼이 생기면 입증할 근거가 없었다.
 *
 * <p>회원 생성 시점에 남긴다. 앱이 두 가입 경로(이메일·카카오) 모두에서 동의 화면을 거치게
 * 되어 있으므로, 계정이 생겼다는 것은 곧 동의했다는 뜻이다. 별도 API 로 받으면 그 호출이
 * 실패했을 때 <b>계정은 있는데 동의 기록은 없는</b> 상태가 생긴다.
 *
 * <p>버전 문자열은 문서의 시행일(예: {@code "2026-09-06"})이다.
 */
@Entity
@Table(name = "terms_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "terms_version", nullable = false, length = 20)
    private String termsVersion;

    @Column(name = "privacy_version", nullable = false, length = 20)
    private String privacyVersion;

    @Column(name = "agreed_at", nullable = false)
    private Instant agreedAt;

    private TermsAgreement(Long userId, String termsVersion, String privacyVersion, Instant agreedAt) {
        this.userId = userId;
        this.termsVersion = termsVersion;
        this.privacyVersion = privacyVersion;
        this.agreedAt = agreedAt;
    }

    public static TermsAgreement of(long userId, String termsVersion, String privacyVersion, Instant agreedAt) {
        return new TermsAgreement(userId, termsVersion, privacyVersion, agreedAt);
    }
}
