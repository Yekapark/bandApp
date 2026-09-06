package com.yeka.bandapp.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 지금 게시 중인 약관·개인정보처리방침의 버전.
 *
 * <p>문서의 <b>시행일</b>을 그대로 쓴다({@code "2026-09-06"}). 별도 번호 체계를 만들면 문서와
 * 설정이 어긋날 때 어느 쪽이 맞는지 알 수 없다 — 시행일은 게시본 첫 줄에 적혀 있어 대조가 쉽다.
 *
 * <p><b>문서를 고쳐 새로 게시하면 이 값도 함께 올린다.</b> 그래야 기존 회원에게 다시 동의를
 * 받아야 하는 시점을 구분할 수 있다(재동의 화면은 아직 없다 — 필요해지면 그때 만든다).
 *
 * @param version        이용약관 시행일
 * @param privacyVersion 개인정보처리방침 시행일
 */
@ConfigurationProperties(prefix = "app.terms")
public record TermsProperties(String version, String privacyVersion) {

    public TermsProperties {
        version = blankToUnknown(version);
        privacyVersion = blankToUnknown(privacyVersion);
    }

    /** 설정이 비어도 기동을 막지 않는다. 다만 기록에 남는 값이 흐려지므로 운영에서는 반드시 채운다. */
    private static String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
