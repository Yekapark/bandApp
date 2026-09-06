package com.yeka.bandapp.user.service;

import com.yeka.bandapp.user.config.TermsProperties;
import com.yeka.bandapp.user.entity.TermsAgreement;
import com.yeka.bandapp.user.repository.TermsAgreementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 약관 동의 사실을 남긴다.
 *
 * <p>회원이 만들어지는 순간에 부른다. 앱은 두 가입 경로(이메일·카카오) 모두에서 동의 화면을
 * 거치므로 <b>계정이 생겼다는 것은 곧 동의했다는 뜻</b>이다. 동의를 별도 API 로 받으면 그
 * 호출이 실패했을 때 계정은 있는데 기록은 없는 상태가 생긴다.
 *
 * <p><b>기록 실패가 가입을 막지 않는다.</b> 동의는 이미 화면에서 이루어졌고, 여기서 예외를
 * 올리면 가입 자체가 롤백된다 — 증거를 남기려다 서비스를 못 쓰게 만드는 것은 뒤바뀐 우선순위다.
 * 대신 로그로 남긴다.
 */
@Service
public class TermsAgreementService {

    private static final Logger log = LoggerFactory.getLogger(TermsAgreementService.class);

    private final TermsAgreementRepository repository;
    private final TermsProperties properties;

    public TermsAgreementService(TermsAgreementRepository repository, TermsProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** 지금 게시 중인 버전으로 동의를 남긴다. 같은 버전이 이미 있으면 아무것도 하지 않는다. */
    public void record(long userId, Instant now) {
        String terms = properties.version();
        String privacy = properties.privacyVersion();
        try {
            if (repository.existsByUserIdAndTermsVersionAndPrivacyVersion(userId, terms, privacy)) {
                return;
            }
            repository.save(TermsAgreement.of(userId, terms, privacy, now));
        } catch (DataIntegrityViolationException duplicate) {
            // 같은 계정의 동시 요청. ux_terms_agreements_user_version 이 최종 방어선이고,
            // 이미 남았다는 뜻이므로 성공으로 친다.
        } catch (RuntimeException e) {
            log.warn("약관 동의 기록 실패 userId={} terms={} privacy={}", userId, terms, privacy, e);
        }
    }
}
