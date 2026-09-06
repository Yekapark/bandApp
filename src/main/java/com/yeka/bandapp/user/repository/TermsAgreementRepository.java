package com.yeka.bandapp.user.repository;

import com.yeka.bandapp.user.entity.TermsAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TermsAgreementRepository extends JpaRepository<TermsAgreement, Long> {

    /** 최근 동의부터. 분쟁 확인용 조회에 쓴다(앱 화면은 없다). */
    List<TermsAgreement> findByUserIdOrderByAgreedAtDesc(long userId);

    boolean existsByUserIdAndTermsVersionAndPrivacyVersion(
            long userId, String termsVersion, String privacyVersion);
}
